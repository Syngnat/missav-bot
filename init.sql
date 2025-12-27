-- 视频表
CREATE TABLE IF NOT EXISTS videos (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) UNIQUE NOT NULL COMMENT '番号',
    title VARCHAR(500) COMMENT '标题',
    actresses VARCHAR(500) COMMENT '演员',
    tags VARCHAR(500) COMMENT '标签',
    duration INT COMMENT '时长(分钟)',
    release_date DATETIME COMMENT '发布日期',
    cover_url VARCHAR(500) COMMENT '封面URL',
    preview_url VARCHAR(500) COMMENT '预览视频URL',
    detail_url VARCHAR(500) COMMENT '详情页URL',
    pushed BOOLEAN DEFAULT FALSE COMMENT '是否已推送',
    created_id VARCHAR(50),
    created_name VARCHAR(100),
    created_time DATETIME,
    updated_id VARCHAR(50),
    updated_name VARCHAR(100),
    updated_time DATETIME,
    remark VARCHAR(500),
    INDEX idx_code (code),
    INDEX idx_pushed (pushed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频表';

-- 订阅表
CREATE TABLE IF NOT EXISTS subscriptions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    chat_id BIGINT NOT NULL COMMENT 'Telegram聊天ID',
    chat_type VARCHAR(20) COMMENT '聊天类型',
    type VARCHAR(20) NOT NULL COMMENT '订阅类型:ALL/ACTRESS/TAG',
    keyword VARCHAR(100) COMMENT '关键词',
    enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    created_id VARCHAR(50),
    created_name VARCHAR(100),
    created_time DATETIME,
    updated_id VARCHAR(50),
    updated_name VARCHAR(100),
    updated_time DATETIME,
    remark VARCHAR(500),
    INDEX idx_chat_id (chat_id),
    INDEX idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订阅表';

-- 推送记录表
CREATE TABLE IF NOT EXISTS push_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    video_id BIGINT NOT NULL COMMENT '视频ID',
    chat_id BIGINT NOT NULL COMMENT '聊天ID',
    status VARCHAR(20) NOT NULL COMMENT '状态:SUCCESS/FAILED',
    fail_reason VARCHAR(500) COMMENT '失败原因',
    pushed_at DATETIME COMMENT '推送时间',
    message_id INT COMMENT '消息ID',
    created_id VARCHAR(50),
    created_name VARCHAR(100),
    created_time DATETIME,
    updated_id VARCHAR(50),
    updated_name VARCHAR(100),
    updated_time DATETIME,
    remark VARCHAR(500),
    INDEX idx_video_id (video_id),
    INDEX idx_chat_id (chat_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推送记录表';
