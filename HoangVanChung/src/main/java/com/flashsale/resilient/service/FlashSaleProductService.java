package com.flashsale.resilient.service;

import com.flashsale.resilient.dto.ProductResponse;

public interface FlashSaleProductService {

    /**
     * Lấy thông tin chi tiết sản phẩm Flash Sale với cơ chế chống Cache Stampede (@Cacheable sync=true)
     * và bảo vệ DB bằng Fallback Rate Limiter khi Redis gặp sự cố.
     *
     * @param id ID sản phẩm
     * @return ProductResponse thông tin chi tiết sản phẩm
     */
    ProductResponse getProductDetail(Long id);

    /**
     * Xóa cache sản phẩm (dùng khi admin cập nhật giá hoặc dùng cho kịch bản test Stampede).
     *
     * @param id ID sản phẩm
     */
    void evictProductCache(Long id);

    /**
     * Chủ động làm nóng (Warm-up) toàn bộ sản phẩm Flash Sale đang hoạt động vào Redis cache.
     *
     * @return số lượng sản phẩm được nạp vào cache
     */
    long warmUpFlashSaleProducts();

    /**
     * Phương thức truy vấn trực tiếp DB (dùng để đo lường benchmark so sánh trước và sau khi tối ưu).
     *
     * @param id ID sản phẩm
     * @return ProductResponse
     */
    ProductResponse getProductDirectFromDb(Long id);
}
