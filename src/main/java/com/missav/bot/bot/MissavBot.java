package com.missav.bot.bot;

import com.missav.bot.crawler.CrawlResult;
import com.missav.bot.push.service.IPushService;
import com.missav.bot.subscription.entity.Subscription;
import com.missav.bot.subscription.entity.Subscription.SubscriptionType;
import com.missav.bot.telegram.TelegramMessageService;
import com.missav.bot.video.entity.Video;
import com.missav.bot.video.mapper.VideoMapper;
import com.missav.bot.subscription.service.ISubscriptionService;
import com.missav.bot.crawler.service.ICrawlerService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;


@Slf4j
@Component
public class MissavBot extends TelegramLongPollingBot {

    private final ISubscriptionService subscriptionService;
    private final VideoMapper videoMapper;
    private final ICrawlerService crawlerService;
    private final IPushService pushService;
    private final TelegramMessageService telegramMessageService;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username:MissavBot}")
    private String botUsername;

    public MissavBot(ISubscriptionService subscriptionService, VideoMapper videoMapper,
                     ICrawlerService crawlerService, IPushService pushService,
                     TelegramMessageService telegramMessageService) {
        super(createBotOptions());
        this.subscriptionService = subscriptionService;
        this.videoMapper = videoMapper;
        this.crawlerService = crawlerService;
        this.pushService = pushService;
        this.telegramMessageService = telegramMessageService;

        // 设置 TelegramMessageService 的 bot 实例
        this.telegramMessageService.setBot(this);
    }

    private static DefaultBotOptions createBotOptions() {
        DefaultBotOptions options = new DefaultBotOptions();
        options.setGetUpdatesTimeout(75);  // 增加轮询超时到 75 秒
        options.setMaxThreads(1);  // 减少线程数
        return options;
    }

    @PostConstruct
    public void setupCommands() {
        try {
            List<BotCommand> commands = new ArrayList<>();
            commands.add(new BotCommand("start", "开始使用"));
            commands.add(new BotCommand("help", "查看帮助"));
            commands.add(new BotCommand("subscribe", "订阅新片"));
            commands.add(new BotCommand("unsubscribe", "取消订阅"));
            commands.add(new BotCommand("list", "我的订阅"));
            commands.add(new BotCommand("latest", "最新视频"));
            commands.add(new BotCommand("search", "搜索视频"));
            commands.add(new BotCommand("status", "机器人状态"));

            execute(new SetMyCommands(commands, new BotCommandScopeDefault(), null));
            log.info("Bot 命令菜单设置成功");
        } catch (TelegramApiException e) {
            log.error("设置 Bot 命令菜单失败", e);
        }
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        Message message = update.getMessage();
        String text = message.getText().trim();
        Long chatId = message.getChatId();
        String chatType = message.getChat().getType();

        log.info("收到消息: chatId={}, type={}, text={}", chatId, chatType, text);

        // 自动为新群组创建订阅（仅群组/超级群组，不包括私聊）
        if (("group".equals(chatType) || "supergroup".equals(chatType)) && text.startsWith("/")) {
            try {
                List<Subscription> existingSubs = subscriptionService.getSubscriptions(chatId);
                if (existingSubs.isEmpty()) {
                    subscriptionService.subscribe(chatId, chatType, SubscriptionType.ALL, null);
                    log.info("自动订阅新群组: chatId={}, type={}, title={}",
                        chatId, chatType, message.getChat().getTitle());
                }
            } catch (Exception e) {
                log.debug("自动订阅群组失败: chatId={}, error={}", chatId, e.getMessage());
            }
        }

        try {
            if (text.startsWith("/")) {
                handleCommand(chatId, chatType, text);
            }
        } catch (Exception e) {
            log.error("处理消息失败", e);
            sendText(chatId, "❌ 处理失败：" + e.getMessage());
        }
    }

    /**
     * 处理命令
     */
    private void handleCommand(Long chatId, String chatType, String text) {
        String[] parts = text.split("\\s+", 2);
        String command = parts[0].toLowerCase().replace("@" + botUsername.toLowerCase(), "");
        String args = parts.length > 1 ? parts[1].trim() : "";

        switch (command) {
            case "/start", "/help" -> sendHelp(chatId);
            case "/subscribe" -> handleSubscribe(chatId, chatType, args);
            case "/unsubscribe" -> handleUnsubscribe(chatId, args);
            case "/list" -> handleList(chatId);
            case "/search" -> handleSearch(chatId, args);
            case "/latest" -> handleLatest(chatId, args);
            case "/crawl" -> handleCrawl(chatId, chatType, args);
            case "/status" -> handleStatus(chatId);
            default -> sendText(chatId, "❓ 未知命令，输入 /help 查看帮助");
        }
    }

    /**
     * 发送帮助信息
     */
    private void sendHelp(Long chatId) {
        String help = """
            🎬 *MissAV 机器人*

            📌 *订阅命令*
            /subscribe - 订阅全部新片
            /subscribe 演员名 - 订阅指定演员
            /subscribe #标签 - 订阅指定标签

            📌 *管理命令*
            /unsubscribe - 取消全部订阅
            /unsubscribe 演员名 - 取消演员订阅
            /list - 查看我的订阅

            📌 *查询命令*
            /search 关键词 - 搜索视频
            /latest - 查看最新视频
            /status - 查看机器人状态

            📌 *手动爬取命令*
            /crawl actor 演员名 [数量]
              示例: /crawl actor 三上悠亚 10
            /crawl code 番号
              示例: /crawl code SSIS-001
            /crawl search 关键词 [数量]
              示例: /crawl search SSIS 20

            💡 有新视频时会自动推送到本群
            """;
        sendMarkdown(chatId, help);
    }

    /**
     * 处理订阅
     */
    private void handleSubscribe(Long chatId, String chatType, String args) {
        if (args.isEmpty()) {
            // 订阅全部
            subscriptionService.subscribe(chatId, chatType, SubscriptionType.ALL, null);
            sendText(chatId, "✅ 已订阅全部新片，有新视频会自动推送");
        } else if (args.startsWith("#")) {
            // 订阅标签
            String tag = args.substring(1).trim();
            subscriptionService.subscribe(chatId, chatType, SubscriptionType.TAG, tag);
            sendText(chatId, "✅ 已订阅标签: #" + tag);
        } else {
            // 订阅演员
            subscriptionService.subscribe(chatId, chatType, SubscriptionType.ACTRESS, args);
            sendText(chatId, "✅ 已订阅演员: " + args);
        }
    }

    /**
     * 处理取消订阅
     */
    private void handleUnsubscribe(Long chatId, String args) {
        if (args.isEmpty()) {
            subscriptionService.unsubscribeAll(chatId);
            sendText(chatId, "✅ 已取消全部订阅");
        } else if (args.startsWith("#")) {
            String tag = args.substring(1).trim();
            subscriptionService.unsubscribe(chatId, SubscriptionType.TAG, tag);
            sendText(chatId, "✅ 已取消标签订阅: #" + tag);
        } else {
            subscriptionService.unsubscribe(chatId, SubscriptionType.ACTRESS, args);
            sendText(chatId, "✅ 已取消演员订阅: " + args);
        }
    }

    /**
     * 查看订阅列表
     */
    private void handleList(Long chatId) {
        List<Subscription> subscriptions = subscriptionService.getSubscriptions(chatId);
        if (subscriptions.isEmpty()) {
            sendText(chatId, "📭 暂无订阅，使用 /subscribe 添加订阅");
            return;
        }

        StringBuilder sb = new StringBuilder("📋 *我的订阅*\n\n");
        for (Subscription sub : subscriptions) {
            switch (sub.getType()) {
                case ALL -> sb.append("• 全部新片\n");
                case ACTRESS -> sb.append("• 演员: ").append(sub.getKeyword()).append("\n");
                case TAG -> sb.append("• 标签: #").append(sub.getKeyword()).append("\n");
            }
        }
        sendMarkdown(chatId, sb.toString());
    }

    /**
     * 搜索视频
     */
    private void handleSearch(Long chatId, String keyword) {
        if (keyword.isEmpty()) {
            sendText(chatId, "请输入搜索关键词，例如: /search SSIS");
            return;
        }

        List<Video> videos = videoMapper.selectByActress(keyword);
        if (videos.isEmpty()) {
            videos = videoMapper.selectByTag(keyword);
        }

        if (videos.isEmpty()) {
            sendText(chatId, "🔍 未找到相关视频");
            return;
        }

        StringBuilder sb = new StringBuilder("🔍 *搜索结果*\n\n");
        int count = 0;
        for (Video video : videos) {
            if (count >= 10) break;
            sb.append("• ").append(video.getCode())
              .append(" - ").append(truncate(video.getTitle(), 30))
              .append("\n");
            count++;
        }
        if (videos.size() > 10) {
            sb.append("\n...共 ").append(videos.size()).append(" 个结果");
        }
        sendMarkdown(chatId, sb.toString());
    }

    /**
     * 查看最新视频
     */
    private void handleLatest(Long chatId, String args) {
        int page = 1;
        if (!args.isEmpty()) {
            try {
                page = Integer.parseInt(args);
                if (page < 1) page = 1;
            } catch (NumberFormatException e) {
                page = 1;
            }
        }

        List<Video> videos = videoMapper.selectTop50ByCreatedTimeDesc();
        if (videos.isEmpty()) {
            sendText(chatId, "暂无视频");
            return;
        }

        int pageSize = 5;
        int totalPages = (int) Math.ceil(videos.size() / (double) pageSize);
        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, videos.size());

        if (startIndex >= videos.size()) {
            sendText(chatId, "❌ 页码超出范围,共 " + totalPages + " 页");
            return;
        }

        sendText(chatId, String.format("📺 最新视频 (第 %d/%d 页):", page, totalPages));

        for (int i = startIndex; i < endIndex; i++) {
            Video video = videos.get(i);

            // 为每个视频发送带封面的消息
            String caption = formatVideoMessage(video);

            if (video.getCoverUrl() != null && !video.getCoverUrl().isEmpty()) {
                sendPhotoWithCaption(chatId, video.getCoverUrl(), caption);
            } else {
                sendMarkdown(chatId, caption);
            }

            // 避免发送过快
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // 提示翻页
        if (page < totalPages) {
            sendText(chatId, "💡 查看下一页: /latest " + (page + 1));
        }
    }

    /**
     * 查看状态
     */
    private void handleStatus(Long chatId) {
        long videoCount = videoMapper.selectCount(null);
        String status = String.format("""
            🤖 *机器人状态*

            📊 视频库: %d 个
            ⏰ 检查间隔: 15 分钟
            ✅ 运行正常
            """, videoCount);
        sendMarkdown(chatId, status);
    }

    /**
     * 处理手动爬取命令
     */
    private void handleCrawl(Long chatId, String chatType, String args) {
        if (args.isEmpty()) {
            sendText(chatId, """
                ❓ 请指定爬取方式:

                /crawl actor 演员名 [数量]
                /crawl code 番号
                /crawl search 关键词 [数量]

                示例:
                /crawl actor 三上悠亚 10
                /crawl code SSIS-001
                /crawl search SSIS 20
                """);
            return;
        }

        String[] parts = args.split("\\s+", 3);
        String crawlType = parts[0].toLowerCase();

        try {
            switch (crawlType) {
                case "actor", "actress" -> handleCrawlByActor(chatId, chatType, parts);
                case "code" -> handleCrawlByCode(chatId, parts);
                case "search", "keyword" -> handleCrawlBySearch(chatId, chatType, parts);
                default -> sendText(chatId, "❌ 未知的爬取类型，请使用: actor、code 或 search");
            }
        } catch (Exception e) {
            log.error("爬取失败", e);
            sendText(chatId, "❌ 爬取失败：" + e.getMessage());
        }
    }

    /**
     * 按演员爬取
     */
    private void handleCrawlByActor(Long chatId, String chatType, String[] parts) {
        // 安全检查：仅允许私聊使用
        if (!"private".equals(chatType)) {
            sendText(chatId, "⚠️ 为保护隐私安全，演员爬取功能仅支持私聊使用");
            return;
        }

        if (parts.length < 2) {
            sendText(chatId, "❌ 请指定演员名，例如: /crawl actor 三上悠亚 10");
            return;
        }

        String actorName = parts[1];
        Integer limit = null;

        if (parts.length >= 3) {
            try {
                limit = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                sendText(chatId, "❌ 数量必须是数字");
                return;
            }
        }

        sendText(chatId, String.format("⏳ 正在爬取演员「%s」的作品...", actorName));

        // 创建 final 变量供 lambda 使用
        final Integer finalLimit = limit;
        final ICrawlerService service = this.crawlerService;

        // 异步执行爬取，避免阻塞
        new Thread(() -> {
            try {
                CrawlResult result = service.crawlByActor(actorName, finalLimit);

                if (result.getNewVideos().isEmpty()) {
                    // 显示详细结果
                    if (result.getTotalCrawled() == 0) {
                        sendText(chatId, String.format("🔍 未找到演员「%s」的作品", actorName));
                    } else {
                        sendText(chatId, String.format("📊 爬取完成，但全部为重复视频\n" +
                            "总计: %d | 新增: 0 | 重复: %d",
                            result.getTotalCrawled(), result.getDuplicateCount()));
                    }
                    return;
                }

                // 显示详细结果
                sendText(chatId, String.format("✅ 爬取完成\n" +
                    "📊 总计: %d | 🆕 新增: %d | 🔄 重复: %d",
                    result.getTotalCrawled(), result.getNewCount(), result.getDuplicateCount()));

                // 推送每个新视频给触发者
                for (Video video : result.getNewVideos()) {
                    pushService.pushVideoToChat(video, chatId);
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                log.error("爬取演员作品失败", e);
                sendText(chatId, "❌ 爬取失败：" + e.getMessage());
            }
        }).start();
    }

    /**
     * 按番号爬取
     */
    private void handleCrawlByCode(Long chatId, String[] parts) {
        if (parts.length < 2) {
            sendText(chatId, "❌ 请指定番号，例如: /crawl code SSIS-001");
            return;
        }

        String code = parts[1].toUpperCase();
        sendText(chatId, String.format("⏳ 正在爬取番号「%s」...", code));

        // 创建 final 变量供 lambda 使用
        final ICrawlerService service = this.crawlerService;

        // 异步执行爬取
        new Thread(() -> {
            try {
                Video video = service.crawlByCode(code);

                if (video == null) {
                    sendText(chatId, String.format("🔍 未找到番号「%s」", code));
                    return;
                }

                sendText(chatId, "✅ 爬取成功");
                pushService.pushVideoToChat(video, chatId);
            } catch (Exception e) {
                log.error("爬取番号失败", e);
                sendText(chatId, "❌ 爬取失败：" + e.getMessage());
            }
        }).start();
    }

    /**
     * 按关键词搜索爬取
     */
    private void handleCrawlBySearch(Long chatId, String chatType, String[] parts) {
        // 安全检查：仅允许私聊使用
        if (!"private".equals(chatType)) {
            sendText(chatId, "⚠️ 为保护隐私安全，关键词搜索功能仅支持私聊使用");
            return;
        }

        if (parts.length < 2) {
            sendText(chatId, "❌ 请指定关键词，例如: /crawl search SSIS 20");
            return;
        }

        String keyword = parts[1];
        Integer limit = null;

        if (parts.length >= 3) {
            try {
                limit = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                sendText(chatId, "❌ 数量必须是数字");
                return;
            }
        }

        sendText(chatId, String.format("⏳ 正在搜索「%s」...", keyword));

        // 创建 final 变量供 lambda 使用
        final Integer finalLimit = limit;
        final ICrawlerService service = this.crawlerService;

        // 异步执行爬取
        new Thread(() -> {
            try {
                CrawlResult result = service.crawlByKeyword(keyword, finalLimit);

                if (result.getNewVideos().isEmpty()) {
                    // 显示详细结果
                    if (result.getTotalCrawled() == 0) {
                        sendText(chatId, String.format("🔍 未找到关键词「%s」相关的作品", keyword));
                    } else {
                        sendText(chatId, String.format("📊 搜索完成，但全部为重复视频\n" +
                            "总计: %d | 新增: 0 | 重复: %d",
                            result.getTotalCrawled(), result.getDuplicateCount()));
                    }
                    return;
                }

                // 显示详细结果
                sendText(chatId, String.format("✅ 搜索完成\n" +
                    "📊 总计: %d | 🆕 新增: %d | 🔄 重复: %d",
                    result.getTotalCrawled(), result.getNewCount(), result.getDuplicateCount()));

                // 推送每个新视频给触发者
                for (Video video : result.getNewVideos()) {
                    pushService.pushVideoToChat(video, chatId);
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                log.error("搜索爬取失败", e);
                sendText(chatId, "❌ 爬取失败：" + e.getMessage());
            }
        }).start();
    }

    /**
     * 推送视频到聊天
     */
    public boolean pushVideo(Long chatId, Video video) {
        try {
            String caption = formatVideoMessage(video);

            // 优先发送预览视频
            if (video.getPreviewUrl() != null && !video.getPreviewUrl().isEmpty()) {
                return sendVideoWithCaption(chatId, video.getPreviewUrl(), video.getCoverUrl(), caption);
            }

            // 其次发送封面图
            if (video.getCoverUrl() != null && !video.getCoverUrl().isEmpty()) {
                return sendPhotoWithCaption(chatId, video.getCoverUrl(), caption);
            }

            // 最后发送纯文本
            sendMarkdown(chatId, caption);
            return true;
        } catch (Exception e) {
            log.error("推送视频失败: chatId={}, code={}", chatId, video.getCode(), e);
            return false;
        }
    }

    /**
     * 格式化视频消息
     */
    private String formatVideoMessage(Video video) {
        StringBuilder sb = new StringBuilder();
        sb.append("🎬 *新片上架*\n\n");
        sb.append("📌 番号: `").append(escapeMarkdown(video.getCode())).append("`\n");

        if (video.getActresses() != null && !video.getActresses().isEmpty()) {
            sb.append("👩 演员: ").append(escapeMarkdown(video.getActresses())).append("\n");
        }

        if (video.getTags() != null && !video.getTags().isEmpty()) {
            sb.append("🏷️ 标签: ").append(formatTags(video.getTags())).append("\n");
        }

        if (video.getDuration() != null) {
            sb.append("⏱️ 时长: ").append(video.getDuration()).append(" 分钟\n");
        }

        sb.append("\n🔗 ").append(video.getDetailUrl());

        return sb.toString();
    }

    /**
     * 转义 Markdown 特殊字符
     */
    private String escapeMarkdown(String text) {
        if (text == null) return "";
        // Telegram Markdown 需要转义的特殊字符: _ * [ ] ( ) ~ ` > # + - = | { } . !
        return text.replace("_", "\\_")
                   .replace("*", "\\*")
                   .replace("[", "\\[")
                   .replace("]", "\\]")
                   .replace("(", "\\(")
                   .replace(")", "\\)")
                   .replace("~", "\\~")
                   .replace("`", "\\`")
                   .replace(">", "\\>")
                   .replace("#", "\\#")
                   .replace("+", "\\+")
                   .replace("-", "\\-")
                   .replace("=", "\\=")
                   .replace("|", "\\|")
                   .replace("{", "\\{")
                   .replace("}", "\\}")
                   .replace(".", "\\.")
                   .replace("!", "\\!");
    }

    private String formatTags(String tags) {
        if (tags == null) return "";
        String[] tagArr = tags.split(",\\s*");
        StringBuilder sb = new StringBuilder();
        for (String tag : tagArr) {
            sb.append("#").append(tag.trim()).append(" ");
        }
        return sb.toString().trim();
    }

    private boolean sendVideoWithCaption(Long chatId, String videoUrl, String thumbUrl, String caption) {
        try {
            SendVideo sendVideo = new SendVideo();
            sendVideo.setChatId(chatId.toString());
            sendVideo.setVideo(new InputFile(videoUrl));
            if (thumbUrl != null && !thumbUrl.isEmpty()) {
                sendVideo.setThumbnail(new InputFile(thumbUrl));
            }
            sendVideo.setCaption(caption);
            sendVideo.setParseMode("Markdown");
            execute(sendVideo);
            return true;
        } catch (TelegramApiException e) {
            log.warn("发送视频失败，尝试发送图片: {}", e.getMessage());
            return sendPhotoWithCaption(chatId, thumbUrl, caption);
        }
    }

    private boolean sendPhotoWithCaption(Long chatId, String photoUrl, String caption) {
        try {
            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(chatId.toString());
            sendPhoto.setPhoto(new InputFile(photoUrl));
            sendPhoto.setCaption(caption);
            sendPhoto.setParseMode("Markdown");
            execute(sendPhoto);
            return true;
        } catch (TelegramApiException e) {
            log.warn("发送图片失败，发送纯文本: {}", e.getMessage());
            sendMarkdown(chatId, caption);
            return true;
        }
    }

    private void sendText(Long chatId, String text) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text);
            execute(message);
        } catch (TelegramApiException e) {
            log.error("发送消息失败", e);
        }
    }

    private void sendMarkdown(Long chatId, String text) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text);
            message.setParseMode("Markdown");
            execute(message);
        } catch (TelegramApiException e) {
            log.error("发送消息失败", e);
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
