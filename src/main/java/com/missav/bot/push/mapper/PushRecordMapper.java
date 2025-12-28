package com.missav.bot.push.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.missav.bot.push.entity.PushRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PushRecordMapper extends BaseMapper<PushRecord> {

    @Select("SELECT COUNT(*) > 0 FROM push_records WHERE video_id = #{videoId} AND chat_id = #{chatId} AND status = #{status}")
    boolean existsByVideoIdAndChatIdAndStatus(@Param("videoId") Long videoId, @Param("chatId") Long chatId, @Param("status") String status);

    @Select("SELECT * FROM push_records WHERE chat_id = #{chatId} ORDER BY pushed_at DESC")
    List<PushRecord> selectByChatIdOrderByPushedAtDesc(@Param("chatId") Long chatId);

    @Select("SELECT * FROM push_records WHERE video_id = #{videoId}")
    List<PushRecord> selectByVideoId(@Param("videoId") Long videoId);

    @Select("SELECT COUNT(*) FROM push_records WHERE chat_id = #{chatId} AND pushed_at > #{since}")
    long countByChatIdSince(@Param("chatId") Long chatId, @Param("since") LocalDateTime since);

    @Select("SELECT * FROM push_records WHERE status = #{status} ORDER BY pushed_at ASC")
    List<PushRecord> selectByStatusOrderByPushedAtAsc(@Param("status") String status);

    @Select("<script>" +
            "SELECT chat_id FROM push_records WHERE video_id = #{videoId} AND status = #{status} " +
            "AND chat_id IN " +
            "<foreach collection='chatIds' item='chatId' open='(' separator=',' close=')'>" +
            "#{chatId}" +
            "</foreach>" +
            "</script>")
    List<Long> selectPushedChatIds(@Param("videoId") Long videoId, @Param("chatIds") List<Long> chatIds, @Param("status") String status);
}
