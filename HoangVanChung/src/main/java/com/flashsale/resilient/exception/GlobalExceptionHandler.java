package com.flashsale.resilient.exception;

import com.flashsale.resilient.dto.ApiResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Không tìm thấy tài nguyên: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

    /**
     * Xử lý khi Rate Limiter từ chối request (vượt quá 200 RPS fallback xuống DB).
     */
    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimitExceeded(RequestNotPermitted ex) {
        log.warn("Rate limit vượt ngưỡng bảo vệ DB: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error(
                        HttpStatus.TOO_MANY_REQUESTS.value(),
                        "Hệ thống đang phục vụ lượng truy cập khổng lồ (Peak Load). Vui lòng thử lại sau giây lát!"
                ));
    }

    /**
     * Xử lý khi Circuit Breaker đang OPEN (ngắt mạch bảo vệ Redis).
     */
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ApiResponse<Void>> handleCircuitBreakerOpen(CallNotPermittedException ex) {
        log.error("Circuit Breaker đang mở mạch do lỗi hạ tầng: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "Dịch vụ bộ nhớ đệm đang tạm thời bảo trì. Hệ thống đang hoạt động ở chế độ dự phòng."
                ));
    }

    /**
     * Xử lý lỗi mất kết nối Redis đột ngột.
     */
    @ExceptionHandler(RedisConnectionFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleRedisFailure(RedisConnectionFailureException ex) {
        log.error("Lỗi mất kết nối Redis: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "Redis Cache không phản hồi. Hệ thống tự động chuyển sang chế độ suy giảm (Degraded Mode)."
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        log.error("Lỗi hệ thống không mong muốn: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Lỗi nội bộ hệ thống: " + ex.getMessage()));
    }
}
