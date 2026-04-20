-- Schema for integration test
CREATE TABLE IF NOT EXISTS test_entity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    create_by VARCHAR(255),
    update_by VARCHAR(255)
);