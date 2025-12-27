package com.missav.bot.subscription.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.missav.bot.common.Result;
import com.missav.bot.subscription.entity.Subscription;
import com.missav.bot.subscription.mapper.SubscriptionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 订阅管理接口
 */
@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionMapper subscriptionMapper;

    /**
     * 分页查询订阅列表
     */
    @GetMapping
    public Result<IPage<Subscription>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        Page<Subscription> pageParam = new Page<>(page, size);
        IPage<Subscription> result = subscriptionMapper.selectPage(pageParam, null);
        return Result.success(result);
    }

    /**
     * 根据ID查询订阅
     */
    @GetMapping("/{id}")
    public Result<Subscription> getById(@PathVariable Long id) {
        Subscription subscription = subscriptionMapper.selectById(id);
        if (subscription == null) {
            return Result.error(404, "订阅不存在");
        }
        return Result.success(subscription);
    }

    /**
     * 查询指定聊天的订阅
     */
    @GetMapping("/chat/{chatId}")
    public Result<List<Subscription>> getByChatId(@PathVariable Long chatId) {
        List<Subscription> subscriptions = subscriptionMapper.selectByChatIdAndEnabledTrue(chatId);
        return Result.success(subscriptions);
    }

    /**
     * 创建订阅
     */
    @PostMapping
    public Result<Subscription> create(@RequestBody Subscription subscription) {
        String typeStr = subscription.getType().name();
        if (subscriptionMapper.existsByChatIdAndTypeAndKeyword(
                subscription.getChatId(), typeStr, subscription.getKeyword())) {
            return Result.error(400, "订阅已存在");
        }
        subscriptionMapper.insert(subscription);
        return Result.success(subscription);
    }

    /**
     * 更新订阅
     */
    @PutMapping("/{id}")
    public Result<Subscription> update(@PathVariable Long id, @RequestBody Subscription subscription) {
        Subscription existing = subscriptionMapper.selectById(id);
        if (existing == null) {
            return Result.error(404, "订阅不存在");
        }
        subscription.setId(id);
        subscriptionMapper.updateById(subscription);
        return Result.success(subscription);
    }

    /**
     * 删除订阅
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Subscription existing = subscriptionMapper.selectById(id);
        if (existing == null) {
            return Result.error(404, "订阅不存在");
        }
        subscriptionMapper.deleteById(id);
        return Result.success();
    }

    /**
     * 删除指定聊天的所有订阅
     */
    @DeleteMapping("/chat/{chatId}")
    public Result<Void> deleteByChatId(@PathVariable Long chatId) {
        subscriptionMapper.deleteByChatId(chatId);
        return Result.success();
    }

    /**
     * 查询所有启用的聊天ID
     */
    @GetMapping("/active-chats")
    public Result<List<Long>> getActiveChats() {
        List<Long> chatIds = subscriptionMapper.selectDistinctChatIdByEnabledTrue();
        return Result.success(chatIds);
    }
}
