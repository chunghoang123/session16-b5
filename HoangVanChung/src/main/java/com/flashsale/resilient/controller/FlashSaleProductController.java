package com.flashsale.resilient.controller;

import com.flashsale.resilient.dto.ApiResponse;
import com.flashsale.resilient.dto.ProductResponse;
import com.flashsale.resilient.service.FlashSaleProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/flash-sale")
@RequiredArgsConstructor
public class FlashSaleProductController {

    private final FlashSaleProductService flashSaleProductService;

    /**
     * Endpoint chính phục vụ 5.000 RPS Flash Sale:
     * - Tích hợp @Cacheable(sync = true) chống Cache Stampede
     * - Tích hợp Circuit Breaker & Rate Limiter bảo vệ DB khi Redis sập
     */
    @GetMapping("/products/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductDetail(@PathVariable Long id) {
        ProductResponse response = flashSaleProductService.getProductDetail(id);
        String message = Boolean.TRUE.equals(response.getIsDegraded()) 
                ? "Dữ liệu phục vụ ở chế độ suy giảm (Degraded Mode)"
                : "Lấy thông tin sản phẩm Flash Sale thành công";
        return ResponseEntity.ok(ApiResponse.success(response, message));
    }

    /**
     * Endpoint đo lường đối chứng (Benchmark Baseline):
     * Truy vấn trực tiếp DB không qua Redis để so sánh chỉ số hiệu năng trước và sau tối ưu.
     */
    @GetMapping("/benchmark/products/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductDirectFromDb(@PathVariable Long id) {
        ProductResponse response = flashSaleProductService.getProductDirectFromDb(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Truy vấn trực tiếp Database (Baseline test)"));
    }

    /**
     * Endpoint cho Ops / Quản trị viên kích hoạt chủ động Warm-up trước giờ Flash Sale.
     */
    @PostMapping("/warm-up")
    public ResponseEntity<ApiResponse<String>> triggerWarmUp() {
        long count = flashSaleProductService.warmUpFlashSaleProducts();
        return ResponseEntity.ok(ApiResponse.success(
                "Đã hoàn thành nạp " + count + " sản phẩm vào Redis",
                "Cache Warm-up thành công!"
        ));
    }

    /**
     * Endpoint xóa cache sản phẩm dùng để giả lập tình huống Key vừa hết hạn (Cache Stampede test).
     */
    @DeleteMapping("/products/{id}/cache")
    public ResponseEntity<ApiResponse<Void>> evictCache(@PathVariable Long id) {
        flashSaleProductService.evictProductCache(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa cache sản phẩm ID: " + id));
    }
}
