package com.flashsale.resilient.repository;

import com.flashsale.resilient.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Tìm tất cả sản phẩm Flash Sale đang hoạt động để phục vụ Cache Warm-up.
     */
    List<Product> findByStatus(String status);

    /**
     * Tìm các sản phẩm Flash Sale đang active có tồn kho > 0.
     */
    @Query("SELECT p FROM Product p WHERE p.status = 'ACTIVE' AND p.flashSaleStock > 0")
    List<Product> findActiveFlashSaleProducts();
}
