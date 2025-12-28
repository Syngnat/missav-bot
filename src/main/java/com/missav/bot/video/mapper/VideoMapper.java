package com.missav.bot.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.missav.bot.video.entity.Video;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface VideoMapper extends BaseMapper<Video> {

    /**
     * 根据番号查找视频
     */
    @Select("SELECT * FROM videos WHERE code = #{code}")
    Video selectByCode(@Param("code") String code);

    /**
     * 检查番号是否存在
     */
    @Select("SELECT COUNT(*) > 0 FROM videos WHERE code = #{code}")
    boolean existsByCode(@Param("code") String code);

    /**
     * 查找未推送的视频
     */
    @Select("SELECT * FROM videos WHERE pushed = 0 ORDER BY created_time ASC")
    List<Video> selectUnpushedVideos();

    /**
     * 根据演员查找视频
     */
    @Select("SELECT * FROM videos WHERE actresses LIKE CONCAT('%', #{actress}, '%')")
    List<Video> selectByActress(@Param("actress") String actress);

    /**
     * 根据标签查找视频
     */
    @Select("SELECT * FROM videos WHERE tags LIKE CONCAT('%', #{tag}, '%')")
    List<Video> selectByTag(@Param("tag") String tag);

    /**
     * 查找指定时间后创建的视频
     */
    @Select("SELECT * FROM videos WHERE created_time > #{time} ORDER BY created_time DESC")
    List<Video> selectByCreatedTimeAfter(@Param("time") LocalDateTime time);

    /**
     * 查找最新的N条视频
     */
    @Select("SELECT * FROM videos ORDER BY created_time DESC LIMIT 50")
    List<Video> selectTop50ByCreatedTimeDesc();

    /**
     * 批量检查番号是否存在
     */
    @Select("<script>" +
            "SELECT code FROM videos WHERE code IN " +
            "<foreach collection='codes' item='code' open='(' separator=',' close=')'>" +
            "#{code}" +
            "</foreach>" +
            "</script>")
    List<String> selectExistingCodes(@Param("codes") List<String> codes);
}
