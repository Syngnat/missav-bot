package com.missav.bot.scheduler;

import com.missav.bot.bot.MissavBot;
import com.missav.bot.subscription.entity.Subscription.SubscriptionType;
import com.missav.bot.video.entity.Video;
import com.missav.bot.crawler.service.ICrawlerService;
import com.missav.bot.push.service.IPushService;
import com.missav.bot.subscription.service.ISubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlScheduler {

    private final ICrawlerService crawlerService;
    private final IPushService pushService;
    private final ISubscriptionService subscriptionService;
    private final MissavBot missavBot;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    @Value("${crawler.enabled:true}")
    private boolean crawlerEnabled;

    @Value("${crawler.initial-pages:2}")
    private int initialPages;

    @Value("${telegram.bot.default-chat-id:0}")
    private Long defaultChatId;

    /**
     * 启动时执行首次抓取
     */
    @PostConstruct
    public void init() {
        // 1. 自动发现并订阅所有群组/频道
        log.info("正在自动发现 Bot 所在的群组/频道...");
        List<Long> chatIds = missavBot.getAllChatIds();

        if (!chatIds.isEmpty()) {
            log.info("发现 {} 个群组/频道，正在创建订阅...", chatIds.size());
            for (Long chatId : chatIds) {
                try {
                    subscriptionService.subscribe(chatId, "supergroup", SubscriptionType.ALL, null);
                    log.info("已自动订阅群组: {}", chatId);
                } catch (Exception e) {
                    log.warn("订阅群组失败: chatId={}, error={}", chatId, e.getMessage());
                }
            }
        } else {
            log.warn("未发现任何群组/频道。建议：");
            log.warn("1. 将 Bot 加入群组");
            log.warn("2. 在群组中发送任意消息（如 /start）");
            log.warn("3. 重启应用，或在 .env 中配置 BOT_CHAT_ID");
        }

        // 2. 如果配置了默认 Chat ID，也创建订阅
        if (defaultChatId != 0 && !chatIds.contains(defaultChatId)) {
            subscriptionService.subscribe(defaultChatId, "supergroup", SubscriptionType.ALL, null);
            log.info("已自动订阅默认群组: {}", defaultChatId);
        }

        // 3. 执行首次抓取
        if (crawlerEnabled) {
            log.info("机器人启动，执行首次抓取...");
            // 异步执行，避免阻塞启动
            new Thread(() -> {
                try {
                    Thread.sleep(5000); // 等待5秒确保Bot完全启动
                    executeCrawlAndPush(initialPages);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }

    /**
     * 定时抓取新视频 - 每15分钟执行一次
     */
    @Scheduled(fixedDelayString = "${crawler.interval:900000}", initialDelay = 60000)
    public void scheduledCrawl() {
        if (!crawlerEnabled) {
            log.debug("爬虫已禁用");
            return;
        }
        executeCrawlAndPush(1);
    }

    /**
     * 执行抓取和推送
     */
    private void executeCrawlAndPush(int pages) {
        // 防止并发执行
        if (!isRunning.compareAndSet(false, true)) {
            log.warn("上一次抓取任务尚未完成，跳过本次执行");
            return;
        }

        try {
            log.info("开始定时抓取任务...");
            long startTime = System.currentTimeMillis();

            // 1. 抓取新视频
            List<Video> newVideos = crawlerService.crawlAndSaveNewVideos(pages);
            log.info("抓取完成，发现{}个新视频", newVideos.size());

            // 2. 推送未推送的视频
            pushService.pushUnpushedVideos();

            long duration = System.currentTimeMillis() - startTime;
            log.info("定时任务完成，耗时{}ms", duration);

        } catch (Exception e) {
            log.error("定时抓取任务执行失败", e);
        } finally {
            isRunning.set(false);
        }
    }

    /**
     * 每天凌晨3点清理30天前的推送记录
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupOldRecords() {
        log.info("执行清理任务...");
        // 可以在这里添加清理逻辑
    }
}
