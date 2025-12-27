# MissAV Bot

一个基于 Telegram 的视频推送机器人,自动抓取 MissAV 最新视频并推送给订阅用户。

## 功能特性

- 🤖 **自动抓取** - 定时抓取最新视频信息
- 📺 **预览播放** - 支持视频预览和封面图展示
- 🔔 **智能订阅** - 支持订阅全部/演员/标签
- 🚫 **自动去重** - 避免重复抓取和推送
- 🔍 **视频搜索** - 支持按演员、标签搜索
- 📊 **推送记录** - 完整的推送历史记录
- 🎯 **自动发现群组** - 启动时自动发现并订阅所有 Bot 所在的群组
- 🛡️ **防刷屏机制** - 智能去重，避免重启时重复推送
- 🎬 **手动爬取** - 支持按演员、番号、关键词手动爬取视频

## 技术栈

- Spring Boot 3.3.5
- MyBatis-Plus 3.5.9
- MySQL 8.0
- Telegram Bot API
- Jsoup (网页解析)
- Spring Boot Actuator (健康检查)

## 环境要求

- JDK 21+
- Maven 3.6+
- MySQL 8.0+
- Telegram Bot Token

## 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/your-username/missav-bot.git
cd missav-bot
```

### 2. 创建数据库

```sql
CREATE DATABASE missav_bot CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

执行数据库初始化脚本:

```sql
-- 视频表
CREATE TABLE videos (
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
CREATE TABLE subscriptions (
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
CREATE TABLE push_records (
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
```

### 3. 配置文件

复制配置文件并修改:

```bash
cp src/main/resources/application-local.yaml.example src/main/resources/application-local.yaml
```

修改 `application-local.yaml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/missav_bot?useSSL=false&serverTimezone=Asia/Shanghai
    username: your_username
    password: your_password

telegram:
  bot:
    token: YOUR_BOT_TOKEN
    username: YOUR_BOT_USERNAME
    default-chat-id: YOUR_DEFAULT_CHAT_ID
```

### 4. 编译运行

```bash
# 编译
mvn clean package -DskipTests

# 运行
java -jar target/missav_bot_*.jar --spring.profiles.active=local
```

## 使用教程

### 创建 Telegram Bot

1. 在 Telegram 中搜索 [@BotFather](https://t.me/BotFather)
2. 发送 `/newbot` 创建新机器人
3. 按提示设置机器人名称和用户名
4. 获取 Bot Token 并配置到 `application-local.yaml`

### 获取 Chat ID

1. 将机器人添加到群组
2. 发送任意消息
3. 访问 `https://api.telegram.org/bot<YOUR_BOT_TOKEN>/getUpdates`
4. 在返回的 JSON 中找到 `chat.id`

### 机器人命令

#### 订阅管理

```
/subscribe              - 订阅全部新片
/subscribe 演员名       - 订阅指定演员
/subscribe #标签        - 订阅指定标签
/unsubscribe           - 取消全部订阅
/unsubscribe 演员名     - 取消演员订阅
/list                  - 查看我的订阅
```

#### 查询命令

```
/search 关键词          - 搜索视频
/latest                - 查看最新视频
/status                - 查看机器人状态
/help                  - 查看帮助信息
```

#### 手动爬取命令

```
/crawl actor 演员名 [数量]    - 爬取指定演员的作品
/crawl code 番号              - 爬取指定番号的作品
/crawl search 关键词 [数量]   - 按关键词搜索并爬取
```

**说明**：
- 手动爬取的视频会**立即推送给命令触发者**
- 支持指定爬取数量（可选参数）
- 所有用户都可以使用手动爬取功能

### 使用示例

1. **订阅全部新片**
   ```
   /subscribe
   ```

2. **订阅指定演员**
   ```
   /subscribe 三上悠亚
   ```

3. **订阅标签**
   ```
   /subscribe #中文字幕
   ```

4. **搜索视频**
   ```
   /search SSIS
   ```

5. **手动爬取演员作品**
   ```
   /crawl actor 三上悠亚 10
   ```

6. **手动爬取指定番号**
   ```
   /crawl code SSIS-001
   ```

7. **按关键词搜索爬取**
   ```
   /crawl search SSIS 20
   ```

## 配置说明

### 爬虫配置

```yaml
crawler:
  enabled: true              # 是否启用爬虫
  interval: 900000          # 抓取间隔(毫秒) 15分钟
  initial-pages: 2          # 初始抓取页数
  user-agent: Mozilla/5.0   # User-Agent
```

### 日志配置

```yaml
logging:
  level:
    root: INFO
    com.missav.bot: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: logs/missav-bot.log
```

## Docker 部署(推荐)

### 一键部署

**只需 3 步,5 分钟完成部署!**

#### 1. 获取 Telegram Bot Token

1. 在 Telegram 搜索 [@BotFather](https://t.me/BotFather)
2. 发送 `/newbot` 创建机器人
3. 按提示设置名称和用户名
4. 复制获得的 Token(格式: `123456789:ABCdefGHIjklMNOpqrsTUVwxyz`)
5. **将 Bot 加入你的群组并发送任意消息**（重要！这样启动时会自动订阅）

#### 2. 配置环境变量

```bash
# 复制环境变量模板
cp .env.example .env

# 编辑 .env 文件,填入你的配置
nano .env
```

`.env` 文件内容：

```bash
# 数据库密码（必填）
DB_PASSWORD=your_secure_password

# Telegram Bot Token（必填，从 @BotFather 获取）
BOT_TOKEN=123456789:ABCdefGHIjklMNOpqrsTUVwxyz

# Telegram Bot 用户名（必填）
BOT_USERNAME=YourBotUsername

# Telegram 群组 Chat ID（可选，推荐配置）
# 获取方法：
# 1. 将 bot 加入群组后，发送一条消息
# 2. 访问：https://api.telegram.org/bot<YOUR_BOT_TOKEN>/getUpdates
# 3. 在返回的 JSON 中找到 "chat":{"id": -100xxxxxxxxx}
BOT_CHAT_ID=-1001234567890
```

#### 3. 启动服务

```bash
# 一键启动(自动构建镜像、创建数据库、启动服务)
docker compose up -d --build

# 查看日志
docker compose logs -f app
```

**完成!** 🎉

**自动发现群组功能**：
- ✅ 如果在启动前已将 Bot 加入群组并发送过消息，Bot 会自动发现并订阅所有群组
- ✅ 也可以手动配置 `BOT_CHAT_ID`，确保万无一失
- ✅ 首次抓取的视频会自动推送到所有已订阅的群组

**端口配置**：
- MySQL: `3308`（主机） → `3308`（容器）
- Java 应用: `8000`（主机） → `8000`（容器）
- 健康检查: `http://localhost:8000/actuator/health`

### 日志配置

**调整日志级别**（可选，按需配置）：

编辑 `.env` 文件：

```bash
# 查看所有 SQL（调试数据库问题）
LOG_LEVEL_SQL=DEBUG

# 查看应用详细日志（调试业务逻辑）
LOG_LEVEL_APP=DEBUG

# 查看爬虫详细日志（调试爬虫问题）
LOG_LEVEL_CRAWLER=DEBUG

# 查看推送详细日志（调试推送问题）
LOG_LEVEL_PUSH=DEBUG
```

然后重启服务：

```bash
docker compose restart app
```

**日志级别说明**：
- `ERROR` - 只显示错误
- `WARN` - 警告 + 错误
- `INFO` - 信息 + 警告 + 错误（默认，推荐生产环境）
- `DEBUG` - 调试 + 信息 + 警告 + 错误（开发/调试用）
- `TRACE` - 最详细，包含所有日志（不推荐）

**常见场景**：
- 🔍 **调试爬虫 403 问题**：`LOG_LEVEL_CRAWLER=DEBUG`
- 🔍 **查看 SQL 执行**：`LOG_LEVEL_SQL=DEBUG`
- 🔍 **调试推送失败**：`LOG_LEVEL_PUSH=DEBUG`
- 🔍 **全局调试**：`LOG_LEVEL_APP=DEBUG`

### 常用命令

```bash
# 查看运行状态
docker compose ps

# 查看日志
docker compose logs -f app

# 重启服务
docker compose restart app

# 停止服务
docker compose down

# 停止并删除数据
docker compose down -v

# 重新构建（强制不使用缓存）
docker compose build --no-cache
```

### 更新版本

```bash
# 拉取最新代码
git pull

# 重新构建并启动
docker compose down
docker compose up -d --build
```

## 常见问题

### 1. 启动失败: 数据源配置问题

**现象**: `Failed to configure a DataSource: 'url' attribute is not specified`

**原因**: Docker 镜像中缺少配置文件

**解决方案**:
- 确保已拉取最新代码（包含完整的 application.yaml）
- 重新构建镜像：`docker compose build --no-cache`

### 2. 预览视频无法播放

**原因**: 爬虫被网站拦截（403 错误）

**解决方案**:
- 已改进请求头，模拟真实浏览器
- 如仍然被拦截，可能需要：
  - 增加爬取间隔时间
  - 使用代理
  - 考虑使用浏览器自动化工具

### 3. 会不会重复推送？

**不会！** 项目有完善的去重机制：

- ✅ **推送记录表**: 每次推送都会记录到 `push_records` 表
- ✅ **精确去重**: 基于 `(video_id, chat_id, status)` 三元组
- ✅ **持久化**: 推送记录保存在数据库，服务重启不丢失
- ✅ **防刷屏**: 即使反复重启，已推送的视频不会重复推送

**场景示例**：
- 首次启动：抓取 40 个视频 → 推送 → 记录到数据库 ✅
- 立即重启：检查数据库 → 已推送，跳过 ✅
- 推送一半崩溃：重启后只推送未推送的部分 ✅

### 4. 首次部署没有推送

**原因**: 启动时没有订阅者，视频被标记为已推送

**解决方案**:
- ✅ **已修复！** 现在启动时会自动发现并订阅所有群组
- 建议：部署前先将 Bot 加入群组并发送消息
- 或者：配置 `BOT_CHAT_ID` 环境变量

### 5. 健康检查失败

**现象**: Docker 容器显示 unhealthy

**原因**: 缺少 Actuator 依赖

**解决方案**:
- ✅ **已修复！** 最新版本已包含 Actuator
- 健康检查地址：`http://localhost:8000/actuator/health`

### 6. MySQL 端口冲突

**现象**: 3306 端口被占用

**解决方案**:
- ✅ **已调整！** 默认使用 3308 端口
- 容器内外都是 3308，避免冲突

## 开发

### 运行测试

```bash
mvn test
```

### 代码结构

```
src/main/java/com/missav/bot/
├── bot/              # Telegram Bot
├── common/           # 公共组件
├── crawler/          # 爬虫模块
├── push/             # 推送模块
├── subscription/     # 订阅模块
├── video/            # 视频模块
└── scheduler/        # 定时任务
```

## 许可证

MIT License

## 免责声明

本项目仅供学习交流使用,请勿用于非法用途。使用本项目所产生的一切后果由使用者自行承担。

## 贡献

欢迎提交 Issue 和 Pull Request!

## 联系方式

如有问题,请提交 Issue。
