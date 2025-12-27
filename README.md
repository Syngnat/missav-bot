# MissAV Bot

一个基于 Telegram 的视频推送机器人,自动抓取 MissAV 最新视频并推送给订阅用户。

## 功能特性

- 🤖 **自动抓取** - 定时抓取最新视频信息
- 📺 **预览播放** - 支持视频预览和封面图展示
- 🔔 **智能订阅** - 支持订阅全部/演员/标签
- 🚫 **自动去重** - 避免重复抓取和推送
- 🔍 **视频搜索** - 支持按演员、标签搜索
- 📊 **推送记录** - 完整的推送历史记录

## 技术栈

- Spring Boot 3.3.5
- MyBatis-Plus 3.5.9
- MySQL 8.0
- Telegram Bot API
- Jsoup (网页解析)

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

#### 2. 配置环境变量

```bash
# 复制环境变量模板
cp .env.example .env

# 编辑 .env 文件,填入你的配置
# DB_PASSWORD=设置一个安全的数据库密码
# BOT_TOKEN=你的Bot Token
# BOT_USERNAME=你的Bot用户名
```

#### 3. 启动服务

```bash
# 一键启动(自动构建镜像、创建数据库、启动服务)
docker-compose up -d

# 查看日志
docker-compose logs -f app
```

**完成!** 🎉 现在可以在 Telegram 中使用你的机器人了!

### 常用命令

```bash
# 查看运行状态
docker-compose ps

# 查看日志
docker-compose logs -f app

# 重启服务
docker-compose restart app

# 停止服务
docker-compose down

# 停止并删除数据
docker-compose down -v
```

### 更新版本

```bash
# 拉取最新代码
git pull

# 重新构建并启动
docker-compose up -d --build
```

## 常见问题

### 1. 启动失败: MyBatis-Plus 兼容性问题

**解决方案**: 确保使用 mybatis-spring 3.0.3+ 版本

```xml
<dependency>
    <groupId>org.mybatis</groupId>
    <artifactId>mybatis-spring</artifactId>
    <version>3.0.3</version>
</dependency>
```

### 2. 预览视频无法播放

**原因**: 爬虫未能正确提取预览视频URL

**解决方案**: 检查日志,确认爬虫是否成功抓取详情页

### 3. 重复推送

**原因**: 推送记录表未正确记录

**解决方案**: 检查数据库连接和推送记录表

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
