package com.flashsale.resilient;

import com.flashsale.resilient.dto.ProductResponse;
import com.flashsale.resilient.entity.Product;
import com.flashsale.resilient.repository.ProductRepository;
import com.flashsale.resilient.service.FlashSaleProductService;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import com.flashsale.resilient.runner.CacheWarmupRunner;

@SpringBootTest(properties = {
        "spring.cache.type=simple",
        "management.health.redis.enabled=false"
})
class FlashSaleProductServiceTest {

    @Autowired
    private FlashSaleProductService flashSaleProductService;

    @MockBean
    private ProductRepository productRepository;

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockBean
    private CacheWarmupRunner cacheWarmupRunner;

    @MockBean
    private ValueOperations<String, Object> valueOperations;

    @Autowired
    private RateLimiterRegistry rateLimiterRegistry;

    private Product mockProduct;

    @BeforeEach
    void setUp() {
        mockProduct = Product.builder()
                .id(1L)
                .name("iPhone 15 Pro Max 256GB")
                .description("Flagship smartphone")
                .price(BigDecimal.valueOf(34990000))
                .flashSalePrice(BigDecimal.valueOf(24990000))
                .stockQuantity(100)
                .flashSaleStock(50)
                .status("ACTIVE")
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now().plusDays(1))
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Kiểm tra Cache Miss: Truy vấn DB và trả về kết quả chính xác")
    void testGetProductDetail_CacheMiss_Success() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));

        ProductResponse response = flashSaleProductService.getProductDetail(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("iPhone 15 Pro Max 256GB", response.getName());
        assertEquals(BigDecimal.valueOf(24990000), response.getFlashSalePrice());
        verify(productRepository, atLeastOnce()).findById(1L);
    }

    @Test
    @DisplayName("Kiểm tra Fallback khi Redis sập: Chuyển sang Degraded Mode và bảo vệ DB bằng Rate Limiter")
    void testGetProductFallback_WhenRedisFails() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));

        // Giả lập ngoại lệ RedisConnectionFailureException
        RedisConnectionFailureException redisException = new RedisConnectionFailureException("Connection refused to Redis");

        // Gọi trực tiếp phương thức xử lý sự cố fallback
        var serviceImpl = (com.flashsale.resilient.service.impl.FlashSaleProductServiceImpl) flashSaleProductService;
        ProductResponse degradedResponse = serviceImpl.getProductFallback(1L, redisException);

        assertNotNull(degradedResponse);
        assertTrue(degradedResponse.getIsDegraded());
        assertEquals("DATABASE_FALLBACK", degradedResponse.getServedBy());
        assertTrue(degradedResponse.getNotice().contains("Degraded Mode"));
    }

    @Test
    @DisplayName("Kiểm tra mô phỏng đồng thời 20 luồng: Đảm bảo dữ liệu nhất quán và không lỗi")
    void testConcurrentRequestsSimulation() throws InterruptedException {
        when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    ProductResponse res = flashSaleProductService.getProductDetail(1L);
                    if (res != null && res.getId().equals(1L)) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(threadCount, successCount.get(), "Toàn bộ 20 luồng đồng thời đều phải nhận kết quả thành công!");
    }
}
