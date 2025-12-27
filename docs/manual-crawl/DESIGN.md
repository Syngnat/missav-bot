# 手动爬取功能设计文档

## 需求背景

当前系统只支持定时自动爬取，用户需要支持手动爬取特定内容的功能，包括：
- 按演员名字爬取所有作品或指定数量的作品
- 按番号精确爬取
- 按关键词搜索爬取

## 功能设计

### 1. Bot 命令设计

```
/crawl actor <演员名> [数量]
  - 示例: /crawl actor 三上悠亚
  - 示例: /crawl actor 三上悠亚 10
  - 说明: 爬取指定演员的作品，可选指定数量（默认全部）

/crawl code <番号>
  - 示例: /crawl code SSIS-001
  - 说明: 爬取指定番号的单个作品

/crawl search <关键词> [数量]
  - 示例: /crawl search 三上
  - 示例: /crawl search SSIS 20
  - 说明: 按关键词模糊搜索并爬取
```

### 2. 核心流程

```
用户发送命令
    ↓
Bot 解析命令参数
    ↓
调用 CrawlerService 按条件爬取
    ↓
MissavCrawler 访问对应 URL 爬取
    ↓
保存到数据库（去重）
    ↓
只推送给命令触发者（不触发全局推送）
    ↓
返回爬取结果给用户
```

**推送策略说明**：
- 手动爬取的视频**只推送给命令触发者**，不推送给其他订阅者
- 避免手动爬取影响订阅用户的正常推送流程
- 命令触发者会收到爬取结果的详细信息和视频预览

### 3. URL 规则（基于 MissAV 网站）

- 演员列表页: `https://missav.ai/actresses/{演员名}?page={页码}`
- 番号搜索: `https://missav.ai/search/{番号}`
- 关键词搜索: `https://missav.ai/search/{关键词}?page={页码}`

### 4. 接口设计

#### ICrawlerService 新增接口

```java
/**
 * 按演员名爬取作品
 * @param actorName 演员名
 * @param limit 限制数量，null 表示全部
 * @return 新爬取的视频列表
 */
List<Video> crawlByActor(String actorName, Integer limit);

/**
 * 按番号爬取作品
 * @param code 番号
 * @return 爬取的视频，不存在返回 null
 */
Video crawlByCode(String code);

/**
 * 按关键词搜索爬取
 * @param keyword 关键词
 * @param limit 限制数量，null 表示全部
 * @return 新爬取的视频列表
 */
List<Video> crawlByKeyword(String keyword, Integer limit);
```

### 5. 技术要点

1. **去重机制**: 复用现有的番号去重逻辑
2. **推送机制**: 直接推送给命令触发者（chatId），不触发全局推送
3. **反爬策略**: 复用现有的随机延迟、请求头配置
4. **错误处理**: 网络错误、解析失败返回友好提示给用户
5. **排序规则**: 网站默认按发布时间 DESC 排序
6. **权限控制**: 所有用户都可以触发（无权限限制）

### 6. 数据库影响

- 无需新增表
- 复用现有 `videos` 表和 `push_records` 表
- 去重依据: `code` 字段

### 7. 安全考虑

- 无权限控制：所有用户都可以触发手动爬取
- 推送隔离：手动爬取只推送给命令触发者，不影响其他订阅用户
- 频率限制：复用现有反爬策略中的随机延迟机制

## 验收标准

- [ ] 支持 `/crawl actor` 命令爬取指定演员作品
- [ ] 支持 `/crawl code` 命令爬取指定番号作品
- [ ] 支持 `/crawl search` 命令按关键词搜索爬取
- [ ] 爬取结果自动去重
- [ ] 爬取完成后只推送给命令触发者
- [ ] 返回爬取统计信息给触发用户
- [ ] 每个视频都推送预览（封面+信息）给触发用户
- [ ] 错误情况下返回友好提示
- [ ] README 文档已更新
