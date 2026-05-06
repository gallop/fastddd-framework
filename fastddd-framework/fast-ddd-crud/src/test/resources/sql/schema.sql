-- Schema for BaseRepository integration tests
-- This script creates the necessary tables for testing BaseRepository functionality

-- Drop table if exists
DROP TABLE IF EXISTS t_user;

-- Create user table
CREATE TABLE t_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    department VARCHAR(50),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_user VARCHAR(50),
    update_user VARCHAR(50),
    deleted TINYINT DEFAULT 0
);

-- Create indexes for better query performance
CREATE INDEX idx_user_status ON t_user(status);
CREATE INDEX idx_user_department ON t_user(department);
CREATE INDEX idx_user_create_time ON t_user(create_time);
CREATE INDEX idx_user_update_time ON t_user(update_time);

-- Insert some test data
INSERT INTO t_user (username, email, status, department, create_user, update_user) VALUES
('admin', 'admin@example.com', 'ACTIVE', 'IT', 'system', 'system'),
('test_user', 'test@example.com', 'ACTIVE', 'HR', 'system', 'system');

COMMIT;