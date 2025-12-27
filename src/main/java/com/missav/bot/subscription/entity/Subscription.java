package com.missav.bot.subscription.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.missav.bot.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 订阅实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("subscriptions")
public class Subscription extends BaseEntity {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Telegram聊天ID（用户或群组）
     */
    private Long chatId;

    /**
     * 聊天类型：private-私聊，group-群组，supergroup-超级群组
     */
    private String chatType;

    /**
     * 订阅类型：ALL-全部，ACTRESS-演员，TAG-标签
     */
    private SubscriptionType type;

    /**
     * 订阅关键词（演员名或标签名）
     */
    private String keyword;

    /**
     * 是否启用：0-禁用，1-启用
     */
    private Boolean enabled;

    public enum SubscriptionType {
        ALL,      // 订阅全部
        ACTRESS,  // 订阅演员
        TAG       // 订阅标签
    }
}
