package com.flashsale.resilient.runner;

import com.flashsale.resilient.service.FlashSaleProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Cache Warm-up Runner:
 * Tự động chạy ngay khi ứng dụng Spring Boot khởi động hoàn tất (CommandLineRunner)
 * nhằm giải quyết triệt để vấn đề COLD START.
 *
 * Toàn bộ dữ liệu của các sản phẩm Flash Sale đang hoạt động sẽ được truy vấn từ Database
 * và nạp sẵn vào Redis Cache trước khi hệ thống mở cổng đón nhận 5.000 RPS từ người dùng.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class CacheWarmupRunner implements CommandLineRunner {

    private final FlashSaleProductService flashSaleProductService;

    @Override
    public void run(String... args) {
        log.info("================================================================================");
        log.info(">>> [COLD START DEFENSE] KHỞI ĐỘNG TIẾN TRÌNH CACHE WARM-UP FLASH SALE...");
        log.info("================================================================================");

        try {
            long totalWarmedUp = flashSaleProductService.warmUpFlashSaleProducts();
            log.info(">>> [WARM-UP SUCCESS] Đã nạp sẵn {} sản phẩm vào Redis.", totalWarmedUp);
            log.info(">>> Hệ thống Flash Sale đã sẵn sàng chịu tải 5.000 RPS an toàn, loại bỏ Cold Start!");
        } catch (Exception ex) {
            log.error(">>> [WARM-UP WARNING] Không thể kết nối Redis trong giai đoạn khởi động: {}. " +
                    "Hệ thống sẽ chuyển sang chế độ tự bảo vệ DB bằng Fallback Rate Limiter.", ex.getMessage());
        }

        log.info("================================================================================");
    }
}
