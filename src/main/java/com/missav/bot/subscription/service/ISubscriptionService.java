package com.missav.bot.subscription.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.missav.bot.subscription.entity.Subscription;
import com.missav.bot.subscription.entity.Subscription.SubscriptionType;

import java.util.List;

/**
 * 订阅服务接口
 */
public interface ISubscriptionService extends IService<Subscription> {

    /**
     * 添加订阅
     */
    Subscription subscribe(Long chatId, String chatType, SubscriptionType type, String keyword);

    /**
     * 取消特定订阅
     */
    void unsubscribe(Long chatId, SubscriptionType type, String keyword);

    /**
     * 取消全部订阅
     */
    void unsubscribeAll(Long chatId);

    /**
     * 获取用户的订阅列表
     */
    List<Subscription> getSubscriptions(Long chatId);

    /**
     * 获取所有订阅全部的聊天
     */
    List<Subscription> getAllSubscriptions();

    /**
     * 获取订阅指定演员的聊天
     */
    List<Subscription> getActressSubscriptions(String actress);

    /**
     * 获取订阅指定标签的聊天
     */
    List<Subscription> getTagSubscriptions(String tag);

    /**
     * 检查视频是否匹配订阅
     */
    boolean matchesSubscription(Subscription subscription, String actresses, String tags);
}
