package com.missav.bot.crawler;

import com.missav.bot.video.entity.Video;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 爬取结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrawlResult {
    /**
     * 新增的视频列表
     */
    private List<Video> newVideos;

    /**
     * 爬取总数
     */
    private int totalCrawled;

    /**
     * 重复数量
     */
    private int duplicateCount;

    /**
     * 无效数量（无番号）
     */
    private int invalidCount;

    /**
     * 获取新增数量
     */
    public int getNewCount() {
        return newVideos != null ? newVideos.size() : 0;
    }
}
