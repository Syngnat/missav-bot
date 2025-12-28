package com.missav.bot.push.service.impl;

import com.missav.bot.push.entity.PushRecord;
import com.missav.bot.push.mapper.PushRecordMapper;
import com.missav.bot.push.service.IPushService;
import com.missav.bot.subscription.entity.Subscription;
import com.missav.bot.subscription.service.ISubscriptionService;
import com.missav.bot.telegram.TelegramMessageService;
import com.missav.bot.video.entity.Video;
import com.missav.bot.crawler.service.ICrawlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushServiceImpl implements IPushService {

    private final TelegramMessageService telegramMessageService;
    private final ISubscriptionService subscriptionService;
    private final ICrawlerService crawlerService;
    private final PushRecordMapper pushRecordMapper;

    @Override
    @Transactional
    public void pushVideoToSubscribers(Video video) {
        // 一次性加载所有订阅
        List<Subscription> allSubscriptions = subscriptionService.list();
        if (allSubscriptions.isEmpty()) {
            crawlerService.markAsPushed(video.getId());
            log.debug("没有任何订阅，视频 {} 已标记为已推送", video.getCode());
            return;
        }

        // 在内存中匹配订阅，收集所有需要推送的 chatId
        Set<Long> targetChatIds = new HashSet<>();

        for (Subscription sub : allSubscriptions) {
            boolean matches = false;

            if (sub.getType() == Subscription.SubscriptionType.ALL) {
                matches = true;
            } else if (sub.getType() == Subscription.SubscriptionType.ACTRESS && video.getActresses() != null) {
                String[] actresses = video.getActresses().split(",\\s*");
                for (String actress : actresses) {
                    if (actress.trim().equals(sub.getKeyword())) {
                        matches = true;
                        break;
                    }
                }
            } else if (sub.getType() == Subscription.SubscriptionType.TAG && video.getTags() != null) {
                String[] tags = video.getTags().split(",\\s*");
                for (String tag : tags) {
                    if (tag.trim().equals(sub.getKeyword())) {
                        matches = true;
                        break;
                    }
                }
            }

            if (matches) {
                targetChatIds.add(sub.getChatId());
            }
        }

        if (targetChatIds.isEmpty()) {
            crawlerService.markAsPushed(video.getId());
            log.debug("视频 {} 没有匹配的订阅者，已标记为已推送", video.getCode());
            return;
        }

        // 批量查询已推送的 chatId
        List<Long> pushedChatIds = pushRecordMapper.selectPushedChatIds(
            video.getId(),
            new java.util.ArrayList<>(targetChatIds),
            PushRecord.PushStatus.SUCCESS.name()
        );
        targetChatIds.removeAll(pushedChatIds);

        if (targetChatIds.isEmpty()) {
            crawlerService.markAsPushed(video.getId());
            log.debug("视频 {} 已推送给所有订阅者", video.getCode());
            return;
        }

        // 推送给剩余的 chatId
        log.info("视频 {} 需要推送给 {} 个订阅者", video.getCode(), targetChatIds.size());
        for (Long chatId : targetChatIds) {
            pushToChatInternal(video, chatId);
        }

        crawlerService.markAsPushed(video.getId());
        log.info("视频 {} 推送完成，共推送给 {} 个订阅者", video.getCode(), targetChatIds.size());
    }

    private void pushToChatInternal(Video video, Long chatId) {
        boolean success = telegramMessageService.pushVideo(chatId, video);

        PushRecord record = PushRecord.builder()
                .videoId(video.getId())
                .chatId(chatId)
                .status(success ? PushRecord.PushStatus.SUCCESS : PushRecord.PushStatus.FAILED)
                .failReason(success ? null : "推送失败")
                .pushedAt(LocalDateTime.now())
                .build();
        pushRecordMapper.insert(record);

        if (success) {
            log.info("推送成功: {} -> chatId={}", video.getCode(), chatId);
        } else {
            log.warn("推送失败: {} -> chatId={}", video.getCode(), chatId);
        }

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void pushVideoToChat(Video video, Long chatId) {
        pushToChatInternal(video, chatId);
    }

    @Override
    @Transactional
    public void pushUnpushedVideos() {
        List<Video> unpushedVideos = crawlerService.getUnpushedVideos();
        log.info("待推送视频数: {}", unpushedVideos.size());

        for (Video video : unpushedVideos) {
            pushVideoToSubscribers(video);
        }
    }
}
