package com.missav.bot.subscription.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.missav.bot.subscription.entity.Subscription;
import com.missav.bot.subscription.entity.Subscription.SubscriptionType;
import com.missav.bot.subscription.mapper.SubscriptionMapper;
import com.missav.bot.subscription.service.ISubscriptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class SubscriptionServiceImpl extends ServiceImpl<SubscriptionMapper, Subscription> implements ISubscriptionService {

    @Override
    @Transactional
    public Subscription subscribe(Long chatId, String chatType, SubscriptionType type, String keyword) {
        String typeStr = type.name();
        if (baseMapper.existsByChatIdAndTypeAndKeyword(chatId, typeStr, keyword)) {
            log.info("订阅已存在: chatId={}, type={}, keyword={}", chatId, type, keyword);
            return baseMapper.selectByChatIdAndTypeAndKeyword(chatId, typeStr, keyword);
        }

        Subscription subscription = Subscription.builder()
                .chatId(chatId)
                .chatType(chatType)
                .type(type)
                .keyword(keyword)
                .enabled(true)
                .build();

        save(subscription);
        log.info("添加订阅: chatId={}, type={}, keyword={}", chatId, type, keyword);
        return subscription;
    }

    @Override
    @Transactional
    public void unsubscribe(Long chatId, SubscriptionType type, String keyword) {
        String typeStr = type.name();
        Subscription sub = baseMapper.selectByChatIdAndTypeAndKeyword(chatId, typeStr, keyword);
        if (sub != null) {
            sub.setEnabled(false);
            updateById(sub);
            log.info("取消订阅: chatId={}, type={}, keyword={}", chatId, type, keyword);
        }
    }

    @Override
    @Transactional
    public void unsubscribeAll(Long chatId) {
        List<Subscription> subscriptions = baseMapper.selectByChatIdAndEnabledTrue(chatId);
        for (Subscription sub : subscriptions) {
            sub.setEnabled(false);
        }
        updateBatchById(subscriptions);
        log.info("取消全部订阅: chatId={}, count={}", chatId, subscriptions.size());
    }

    @Override
    public List<Subscription> getSubscriptions(Long chatId) {
        return baseMapper.selectByChatIdAndEnabledTrue(chatId);
    }

    @Override
    public List<Subscription> getAllSubscriptions() {
        return baseMapper.selectByTypeAndEnabledTrue(SubscriptionType.ALL.name());
    }

    @Override
    public List<Subscription> getActressSubscriptions(String actress) {
        return baseMapper.selectByTypeAndKeywordAndEnabledTrue(SubscriptionType.ACTRESS.name(), actress);
    }

    @Override
    public List<Subscription> getTagSubscriptions(String tag) {
        return baseMapper.selectByTypeAndKeywordAndEnabledTrue(SubscriptionType.TAG.name(), tag);
    }

    @Override
    public boolean matchesSubscription(Subscription subscription, String actresses, String tags) {
        if (subscription.getType() == SubscriptionType.ALL) {
            return true;
        }

        String keyword = subscription.getKeyword();
        if (keyword == null || keyword.isEmpty()) {
            return false;
        }

        if (subscription.getType() == SubscriptionType.ACTRESS) {
            return actresses != null && actresses.contains(keyword);
        }

        if (subscription.getType() == SubscriptionType.TAG) {
            return tags != null && tags.contains(keyword);
        }

        return false;
    }
}
