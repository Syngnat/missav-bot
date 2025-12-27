package com.missav.bot.push.service.impl;

import com.missav.bot.bot.MissavBot;
import com.missav.bot.push.entity.PushRecord;
import com.missav.bot.push.mapper.PushRecordMapper;
import com.missav.bot.push.service.IPushService;
import com.missav.bot.subscription.entity.Subscription;
import com.missav.bot.subscription.service.ISubscriptionService;
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

    private final MissavBot missavBot;
    private final ISubscriptionService subscriptionService;
    private final ICrawlerService crawlerService;
    private final PushRecordMapper pushRecordMapper;

    @Override
    @Transactional
    public void pushVideoToSubscribers(Video video) {
        Set<Long> pushedChatIds = new HashSet<>();

        List<Subscription> allSubs = subscriptionService.getAllSubscriptions();
        for (Subscription sub : allSubs) {
            if (pushedChatIds.contains(sub.getChatId())) {
                continue;
            }
            pushToChat(video, sub.getChatId());
            pushedChatIds.add(sub.getChatId());
        }

        if (video.getActresses() != null) {
            String[] actresses = video.getActresses().split(",\\s*");
            for (String actress : actresses) {
                List<Subscription> actressSubs = subscriptionService.getActressSubscriptions(actress.trim());
                for (Subscription sub : actressSubs) {
                    if (pushedChatIds.contains(sub.getChatId())) {
                        continue;
                    }
                    pushToChat(video, sub.getChatId());
                    pushedChatIds.add(sub.getChatId());
                }
            }
        }

        if (video.getTags() != null) {
            String[] tags = video.getTags().split(",\\s*");
            for (String tag : tags) {
                List<Subscription> tagSubs = subscriptionService.getTagSubscriptions(tag.trim());
                for (Subscription sub : tagSubs) {
                    if (pushedChatIds.contains(sub.getChatId())) {
                        continue;
                    }
                    pushToChat(video, sub.getChatId());
                    pushedChatIds.add(sub.getChatId());
                }
            }
        }

        if (!pushedChatIds.isEmpty()) {
            crawlerService.markAsPushed(video.getId());
        }

        log.info("视频 {} 已推送给 {} 个订阅者", video.getCode(), pushedChatIds.size());
    }

    private void pushToChat(Video video, Long chatId) {
        if (pushRecordMapper.existsByVideoIdAndChatIdAndStatus(
                video.getId(), chatId, PushRecord.PushStatus.SUCCESS.name())) {
            log.debug("视频已推送过: videoId={}, chatId={}", video.getId(), chatId);
            return;
        }

        boolean success = missavBot.pushVideo(chatId, video);

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
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
