CREATE DATABASE IF NOT EXISTS seckill_product
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS seckill_order
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE seckill_product;

DROP TABLE IF EXISTS product;

CREATE TABLE product (
                         id BIGINT PRIMARY KEY AUTO_INCREMENT,
                         name VARCHAR(128) NOT NULL,
                         price DECIMAL(10, 2) NOT NULL,
                         stock INT NOT NULL,
                         created_time DATETIME NOT NULL,
                         updated_time DATETIME NOT NULL
);

INSERT INTO product (id, name, price, stock, created_time, updated_time)
VALUES
    (1, 'iPhone 15', 5999.00, 100, NOW(), NOW()),
    (2, 'MacBook Pro', 12999.00, 50, NOW(), NOW());

USE seckill_order;

DROP TABLE IF EXISTS order_info;

CREATE TABLE order_info (
                            id BIGINT PRIMARY KEY AUTO_INCREMENT,
                            order_no VARCHAR(64) NOT NULL,
                            user_id BIGINT NOT NULL,
                            product_id BIGINT NOT NULL,
                            product_name VARCHAR(128) NOT NULL,
                            quantity INT NOT NULL,
                            amount DECIMAL(10, 2) NOT NULL,
                            status VARCHAR(32) NOT NULL,
                            created_time DATETIME NOT NULL,
                            updated_time DATETIME NOT NULL,
                            UNIQUE KEY uk_order_no (order_no)
);