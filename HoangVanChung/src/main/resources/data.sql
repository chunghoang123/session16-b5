-- Khởi tạo dữ liệu mẫu cho chương trình Flash Sale
INSERT INTO products (id, name, description, price, flash_sale_price, stock_quantity, flash_sale_stock, status, start_time, end_time) VALUES
(1, 'iPhone 15 Pro Max 256GB Titan Tự Nhiên', 'Điện thoại thông minh Apple cao cấp', 34990000, 24990000, 100, 50, 'ACTIVE', CURRENT_TIMESTAMP, TIMESTAMPADD('DAY', 1, CURRENT_TIMESTAMP)),
(2, 'Laptop Apple MacBook Air 13 M3 8GB/256GB', 'Máy tính xách tay mỏng nhẹ hiệu năng vượt trội', 27990000, 19990000, 80, 30, 'ACTIVE', CURRENT_TIMESTAMP, TIMESTAMPADD('DAY', 1, CURRENT_TIMESTAMP)),
(3, 'Tai nghe không dây Apple AirPods Pro 2 MagSafe', 'Chống ồn chủ động chuyên nghiệp 2x', 6190000, 4490000, 200, 100, 'ACTIVE', CURRENT_TIMESTAMP, TIMESTAMPADD('DAY', 1, CURRENT_TIMESTAMP)),
(4, 'Máy chơi game Sony PlayStation 5 Slim Standard', 'Phiên bản ổ đĩa chơi game 4K đỉnh cao', 15990000, 11990000, 50, 20, 'ACTIVE', CURRENT_TIMESTAMP, TIMESTAMPADD('DAY', 1, CURRENT_TIMESTAMP)),
(5, 'Smart Tivi Samsung Crystal UHD 4K 55 inch', 'Hình ảnh sắc nét Dynamic Crystal Color', 13400000, 8990000, 60, 25, 'ACTIVE', CURRENT_TIMESTAMP, TIMESTAMPADD('DAY', 1, CURRENT_TIMESTAMP));
