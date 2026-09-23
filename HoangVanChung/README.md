# Hệ Thống Flash Sale Chống Sập (Resilient Flash Sale System)

Dự án mẫu triển khai kiến trúc **Resilient Microservice** cho sự kiện Flash Sale thương mại điện tử chịu tải **5.000 RPS**, giải quyết triệt để 2 vấn đề lớn:
1. **Cache Stampede (Thundering Herd)** bằng `@Cacheable(sync = true)`.
2. **Cold Start** bằng **Cache Warm-up chủ động** (`CommandLineRunner`) kèm kỹ thuật **Staggered TTL / Jitter**.
3. **Phòng thủ suy giảm tính năng (Degraded Mode)** bằng **Resilience4j Circuit Breaker & Rate Limiter** bảo vệ Database khi Redis gặp sự cố.

---

## 1. Công nghệ sử dụng
- **Java 21 (LTS)**
- **Spring Boot 3.3.4** (Spring Web, Spring Data JPA, Spring Data Redis, Spring Cache, Actuator, AOP)
- **Lombok**
- **Resilience4j 2.2.0** (CircuitBreaker & RateLimiter)
- **Jackson Datatype JSR-310** (Xử lý Java 8 Date/Time trong Redis)
- **H2 In-Memory Database** (có sẵn dữ liệu mẫu trong `data.sql`)
- **Redis 7.2** (Docker Compose)
- **Gradle** build tool

---

## 2. Cấu trúc thư mục dự án

```text
ss16_5/
├── BAO_CAO_THIET_KE_FLASH_SALE.md     # Báo cáo phân tích chuyên sâu 3-4 trang
├── build.gradle                       # Gradle dependencies và build config
├── settings.gradle                    # Gradle project name
├── docker-compose.yml                 # Khởi chạy Redis cục bộ
├── README.md                          # Tài liệu hướng dẫn sử dụng
└── src/
    ├── main/
    │   ├── java/com/flashsale/resilient/
    │   │   ├── ResilientFlashSaleApplication.java   # Main class
    │   │   ├── config/
    │   │   │   └── RedisConfig.java                 # Jackson Serializer & CacheManager
    │   │   ├── controller/
    │   │   │   └── FlashSaleProductController.java  # REST API Endpoints
    │   │   ├── dto/
    │   │   │   ├── ApiResponse.java                 # Generic response wrapper
    │   │   │   └── ProductResponse.java             # DTO trả về kèm Resilience metadata
    │   │   ├── entity/
    │   │   │   └── Product.java                     # JPA Entity
    │   │   ├── exception/
    │   │   │   ├── GlobalExceptionHandler.java      # Bắt lỗi 429, 503, 404
    │   │   │   └── ResourceNotFoundException.java
    │   │   ├── repository/
    │   │   │   └── ProductRepository.java           # Spring Data JPA Repo
    │   │   ├── runner/
    │   │   │   └── CacheWarmupRunner.java           # CommandLineRunner chống Cold Start
    │   │   └── service/
    │   │       ├── FlashSaleProductService.java
    │   │       └── impl/
    │   │           └── FlashSaleProductServiceImpl.java # Core logic sync=true & fallback
    │   └── resources/
    │       ├── application.yml                      # Cấu hình Redis, Hikari, Resilience4j
    │       └── data.sql                             # Dữ liệu Flash Sale khởi tạo
    └── test/
        └── java/com/flashsale/resilient/
            └── FlashSaleProductServiceTest.java     # Unit/Integration tests
```

---

## 3. Hướng dẫn khởi chạy

### Bước 1: Khởi động Redis
Sử dụng docker-compose để chạy Redis container:
```bash
docker compose up -d
```
Kiểm tra Redis hoạt động:
```bash
docker exec -it flashsale-redis redis-cli ping
# Phản hồi: PONG
```

### Bước 2: Chạy ứng dụng Spring Boot
```bash
gradle bootRun
```
Khi ứng dụng khởi động, bạn sẽ thấy log của `CacheWarmupRunner`:
```text
>>> [COLD START DEFENSE] KHỞI ĐỘNG TIẾN TRÌNH CACHE WARM-UP FLASH SALE...
Warm-up thành công sản phẩm: [iPhone 15 Pro Max 256GB Titan Tự Nhiên] với TTL = 3942s (Base: 3600s, Jitter: +342s)
...
>>> [WARM-UP SUCCESS] Đã nạp sẵn 5 sản phẩm vào Redis.
>>> Hệ thống Flash Sale đã sẵn sàng chịu tải 5.000 RPS an toàn, loại bỏ Cold Start!
```

---

## 4. Các API chính

| Phương thức | Đường dẫn | Mô tả |
| :--- | :--- | :--- |
| `GET` | `/api/v1/flash-sale/products/{id}` | Lấy chi tiết sản phẩm Flash Sale (Có `@Cacheable(sync=true)` & Fallback Rate Limiter) |
| `GET` | `/api/v1/flash-sale/benchmark/products/{id}` | Truy vấn trực tiếp DB không qua cache (Đo lường baseline) |
| `POST` | `/api/v1/flash-sale/warm-up` | Kích hoạt chủ động nạp lại cache trước khung giờ Flash Sale |
| `DELETE` | `/api/v1/flash-sale/products/{id}/cache` | Xóa cache sản phẩm (Dùng để test kịch bản Cache Stampede) |
| `GET` | `/actuator/health` | Kiểm tra trạng thái hệ thống và Circuit Breaker |

---

## 5. Hướng dẫn kiểm thử & Tái hiện các kịch bản

### 5.1. Kiểm tra Cold Start Defense
Khi vừa chạy ứng dụng, truy vấn ngay sản phẩm:
```bash
curl -i http://localhost:8080/api/v1/flash-sale/products/1
```
Phản hồi:
- Thời gian phản hồi: `< 5ms`
- Log cho thấy dữ liệu được lấy từ Redis do đã được `CacheWarmupRunner` làm nóng từ trước, không hề có câu lệnh SQL nào bắn vào DB!

### 5.2. Kiểm tra Cache Stampede Defense với `@Cacheable(sync = true)`
1. Xóa cache của sản phẩm ID = 1 để đưa về trạng thái hết hạn:
   ```bash
   curl -X DELETE http://localhost:8080/api/v1/flash-sale/products/1/cache
   ```
2. Sử dụng công cụ bắn tải Apache Bench (ab) hoặc k6 bắn đồng thời 500 requests vào sản phẩm:
   ```bash
   ab -n 500 -c 50 http://localhost:8080/api/v1/flash-sale/products/1
   ```
3. Quan sát Log ứng dụng:
   - Dòng log `[CACHE MISS / SYNC EXECUTION]` **chỉ xuất hiện đúng 1 lần duy nhất** cho luồng đầu tiên lấy lock!
   - 499 requests còn lại nhận dữ liệu từ cache ngay khi luồng đầu tiên hoàn thành.
   - Database chỉ chịu đúng 1 query SQL!

### 5.3. Kiểm tra Degraded Mode & Rate Limiter khi Redis gặp sự cố
1. Tắt Redis container:
   ```bash
   docker stop flashsale-redis
   ```
2. Gửi request:
   ```bash
   curl -i http://localhost:8080/api/v1/flash-sale/products/1
   ```
3. Kết quả:
   - Hệ thống không bị sập hay ném 500 error!
   - Circuit Breaker phát hiện Redis sập và điều hướng sang phương thức Fallback.
   - Dữ liệu trả về có cờ `"isDegraded": true` và `"servedBy": "DATABASE_FALLBACK"`.
4. Nếu số lượng request fallback vượt quá 200 RPS:
   - Rate Limiter sẽ chặn ngay lập tức và trả về mã lỗi `HTTP 429 Too Many Requests`.
   - Database được bảo vệ an toàn, không bao giờ bị quá tải connection pool.

---

## 6. Tài liệu báo cáo phân tích chi tiết
Xem toàn bộ báo cáo phân tích kiến trúc, sơ đồ chi tiết, bảng so sánh hiệu năng và giải trình kỹ thuật chuyên sâu tại file:  
👉 **[BAO_CAO_THIET_KE_FLASH_SALE.md](file:///d:/code/Microservice/ss16/ss16_5/BAO_CAO_THIET_KE_FLASH_SALE.md)**
