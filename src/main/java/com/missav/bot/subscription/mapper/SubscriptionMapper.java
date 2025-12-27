package com.missav.bot.subscription.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.missav.bot.subscription.entity.Subscription;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SubscriptionMapper extends BaseMapper<Subscription> {

    /**
     * 查找聊天的所有启用订阅
     */
    @Select("SELECT * FROM subscriptions WHERE chat_id = #{chatId} AND enabled = 1")
    List<Subscription> selectByChatIdAndEnabledTrue(@Param("chatId") Long chatId);

    /**
     * 查找所有启用的指定类型订阅
     */
    @Select("SELECT * FROM subscriptions WHERE type = #{type} AND enabled = 1")
    List<Subscription> selectByTypeAndEnabledTrue(@Param("type") String type);

    /**
     * 查找指定类型和关键词的订阅
     */
    @Select("SELECT * FROM subscriptions WHERE type = #{type} AND keyword = #{keyword} AND enabled = 1")
    List<Subscription> selectByTypeAndKeywordAndEnabledTrue(@Param("type") String type, @Param("keyword") String keyword);

    /**
     * 检查订阅是否存在
     */
    @Select("SELECT COUNT(*) > 0 FROM subscriptions WHERE chat_id = #{chatId} AND type = #{type} AND keyword = #{keyword}")
    boolean existsByChatIdAndTypeAndKeyword(@Param("chatId") Long chatId, @Param("type") String type, @Param("keyword") String keyword);

    /**
     * 查找特定订阅
     */
    @Select("SELECT * FROM subscriptions WHERE chat_id = #{chatId} AND type = #{type} AND keyword = #{keyword}")
    Subscription selectByChatIdAndTypeAndKeyword(@Param("chatId") Long chatId, @Param("type") String type, @Param("keyword") String keyword);

    /**
     * 删除聊天的所有订阅
     */
    @Delete("DELETE FROM subscriptions WHERE chat_id = #{chatId}")
    int deleteByChatId(@Param("chatId") Long chatId);

    /**
     * 获取所有需要推送的聊天ID（去重）
     */
    @Select("SELECT DISTINCT chat_id FROM subscriptions WHERE enabled = 1")
    List<Long> selectDistinctChatIdByEnabledTrue();
}
