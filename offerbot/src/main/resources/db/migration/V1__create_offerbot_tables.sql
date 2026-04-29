CREATE TABLE wx_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    openid VARCHAR(128) NOT NULL,
    state VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    role VARCHAR(32) NOT NULL DEFAULT 'USER',
    request_count INT NOT NULL DEFAULT 0,
    window_start DATETIME NULL,
    banned_reason VARCHAR(255) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_wx_user_openid UNIQUE (openid)
);

CREATE TABLE offer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company VARCHAR(128) NOT NULL,
    city VARCHAR(64) NOT NULL,
    position VARCHAR(128) NOT NULL,
    salary VARCHAR(128) NOT NULL,
    education VARCHAR(64) NULL,
    industry VARCHAR(64) NULL,
    type VARCHAR(32) NULL,
    source_openid VARCHAR(128) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE INDEX idx_offer_source_openid ON offer (source_openid);
CREATE INDEX idx_offer_company ON offer (company);
CREATE INDEX idx_offer_city_position ON offer (city, position);

CREATE TABLE command_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    openid VARCHAR(128) NOT NULL,
    raw_command TEXT NOT NULL,
    parsed_action VARCHAR(64) NULL,
    status VARCHAR(32) NOT NULL,
    result_text TEXT NULL,
    error_message TEXT NULL,
    created_at DATETIME NOT NULL
);

CREATE INDEX idx_command_log_openid_created_at ON command_log (openid, created_at);
