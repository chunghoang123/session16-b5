package com.flashsale.resilient;

import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ứng dụng mô phỏng hệ thống Flash Sale chịu tải cao với khả năng chống sập (Resilient System).
 * Tích hợp Spring Cache với Redis, Resilience4j Circuit Breaker & Rate Limiter,
 * và cơ chế chủ động Cache Warm-up.
 */
@SpringBootApplication(exclude = {
        RedisReactiveAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class
})
@EnableCaching
@EnableScheduling
public class ResilientFlashSaleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResilientFlashSaleApplication.class, args);
    }
}
