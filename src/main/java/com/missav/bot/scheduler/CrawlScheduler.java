package com.missav.bot.scheduler;

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
        // 如果配置了默认 Chat ID，自动创建订阅
        if (defaultChatId != 0) {
            try {
                subscriptionService.subscribe(defaultChatId, "supergroup", SubscriptionType.ALL, null);
                log.info("已自动订阅默认群组: {}", defaultChatId);
            } catch (Exception e) {
                log.warn("订阅默认群组失败: {}", e.getMessage());
            }
        } else {
            log.info("未配置 BOT_CHAT_ID，Bot 会在收到群组消息时自动订阅");
        }

        // 执行首次抓取
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
