package com.flashsale.resilient.service.impl;

import com.flashsale.resilient.dto.ProductResponse;
import com.flashsale.resilient.entity.Product;
import com.flashsale.resilient.exception.ResourceNotFoundException;
import com.flashsale.resilient.repository.ProductRepository;
import com.flashsale.resilient.service.FlashSaleProductService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlashSaleProductServiceImpl implements FlashSaleProductService {

    private final ProductRepository productRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RateLimiterRegistry rateLimiterRegistry;

    @Value("${flash-sale.cache.base-ttl-seconds:3600}")
    private long baseTtlSeconds;

    @Value("${flash-sale.cache.max-jitter-seconds:600}")
    private long maxJitterSeconds;

    /**
     * Lấy chi tiết sản phẩm Flash Sale:
     * 1. @Cacheable(sync = true):
     *    - Khi Cache Hit: Trả về trực tiếp từ Redis trong < 3ms.
     *    - Khi Cache Miss: Cơ chế sync=true sẽ khóa đồng bộ hóa theo Key (Synchronized per Key).
     *      Chỉ duy nhất 1 luồng được phép thực thi thân hàm (truy vấn DB). 4.999 luồng khác sẽ block chờ
     *      và nhận kết quả ngay khi luồng đầu tiên nạp xong dữ liệu vào Cache. Triệt tiêu 100% Cache Stampede!
     * 2. @CircuitBreaker:
     *    - Khi Redis gặp sự cố (Timeout/Connection refused), Circuit Breaker chặn lỗi và điều hướng
     *      sang phương thức getProductFallback().
     */
    @Override
    @CircuitBreaker(name = "redisCircuitBreaker", fallbackMethod = "getProductFallback")
    @Cacheable(value = "flash_sale_products", key = "#id", sync = true)
    @Transactional(readOnly = true)
    public ProductResponse getProductDetail(Long id) {
        log.info("===> [CACHE MISS / SYNC EXECUTION] Luồng [{}] duy nhất đang truy vấn DB cho sản phẩm ID: {}",
                Thread.currentThread().getName(), id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm Flash Sale có ID: " + id));

        return mapToProductResponse(product, false, "DATABASE (VIA CACHE MISS)", "Dữ liệu được nạp từ Database");
    }

    /**
     * Phương thức Fallback kích hoạt khi Redis gặp sự cố:
     * Tích hợp Rate Limiter để bảo vệ Database (tối đa 200 RPS xuống DB).
     *
     * @param id ID sản phẩm
     * @param t Nguyên nhân lỗi (RedisConnectionFailureException, v.v.)
     * @return ProductResponse ở chế độ hạ cấp (Degraded Mode)
     */
    public ProductResponse getProductFallback(Long id, Throwable t) {
        log.warn("===> [REDIS FAILURE DETECTED] Redis gặp sự cố ({}). Kích hoạt Fallback & Rate Limiter bảo vệ DB cho ID: {}",
                t.getClass().getSimpleName(), id);

        RateLimiter dbRateLimiter = rateLimiterRegistry.rateLimiter("dbRateLimiter");

        // Bọc truy vấn DB trong RateLimiter để khống chế tối đa 200 RPS xuống DB
        Supplier<ProductResponse> guardedDbCall = RateLimiter.decorateSupplier(dbRateLimiter, () -> {
            log.info("===> [DEGRADED MODE] Request được cấp quota truy vấn Database trực tiếp cho ID: {}", id);
            Product product = productRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Sản phẩm không tồn tại: " + id));
            return mapToProductResponse(product, true, "DATABASE_FALLBACK",
                    "Hệ thống đang chạy ở chế độ hạ cấp (Degraded Mode) do sự cố bộ nhớ đệm.");
        });

        try {
            return guardedDbCall.get();
        } catch (RequestNotPermitted ex) {
            log.error("===> [RATE LIMIT EXCEEDED] Vượt quá hạn ngạch 200 RPS xuống DB khi Redis sập! Chặn nhanh request cho ID: {}", id);
            // Trả về đối tượng Degraded khẩn cấp hoặc ném ngoại lệ để GlobalExceptionHandler trả về HTTP 429
            throw ex;
        }
    }

    /**
     * Xóa cache sản phẩm (dùng để test Stampede hoặc khi cập nhật thông tin sản phẩm).
     */
    @Override
    @CacheEvict(value = "flash_sale_products", key = "#id")
    public void evictProductCache(Long id) {
        log.info("Đã xóa cache sản phẩm ID: {}", id);
    }

    /**
     * Chủ động làm nóng (Cache Warm-up) trước sự kiện Flash Sale:
     * Quét các sản phẩm ACTIVE và nạp trước vào Redis kèm kỹ thuật TTL Jitter.
     */
    @Override
    @Transactional(readOnly = true)
    public long warmUpFlashSaleProducts() {
        log.info("Bắt đầu tiến trình chủ động Warm-up Cache Flash Sale...");
        long startTime = System.currentTimeMillis();

        List<Product> activeProducts = productRepository.findActiveFlashSaleProducts();
        long count = 0;

        for (Product product : activeProducts) {
            ProductResponse response = mapToProductResponse(product, false, "REDIS_CACHE", "Dữ liệu sẵn sàng cho Flash Sale");

            // Tạo key Redis tương ứng với @Cacheable: "flash_sale_products::" + id
            String cacheKey = "flash_sale_products::" + product.getId();

            // Tính toán TTL kèm Jitter ngẫu nhiên tránh Mass Expiration
            long randomJitter = ThreadLocalRandom.current().nextLong(0, maxJitterSeconds + 1);
            long actualTtl = baseTtlSeconds + randomJitter;

            redisTemplate.opsForValue().set(cacheKey, response, Duration.ofSeconds(actualTtl));
            count++;
            log.info("Warm-up thành công sản phẩm: [{}] với TTL = {}s (Base: {}s, Jitter: +{}s)",
                    product.getName(), actualTtl, baseTtlSeconds, randomJitter);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("===> Hoàn tất Warm-up {} sản phẩm Flash Sale vào Redis trong {} ms. Hệ thống sẵn sàng!",
                count, duration);

        return count;
    }

    /**
     * Phương thức truy vấn trực tiếp DB không qua Cache để kiểm thử hiệu năng đối chứng (Benchmark Baseline).
     */
    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductDirectFromDb(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm: " + id));
        return mapToProductResponse(product, false, "DIRECT_DATABASE", "Truy vấn trực tiếp Database không qua Cache");
    }

    private ProductResponse mapToProductResponse(Product product, boolean isDegraded, String servedBy, String notice) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .originalPrice(product.getPrice())
                .flashSalePrice(product.getFlashSalePrice())
                .availableStock(product.getFlashSaleStock())
                .status(product.getStatus())
                .startTime(product.getStartTime())
                .endTime(product.getEndTime())
                .isDegraded(isDegraded)
                .servedBy(servedBy)
                .notice(notice)
                .responseTime(LocalDateTime.now())
                .build();
    }
}
