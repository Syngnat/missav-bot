package com.missav.bot.crawler.service;

import com.missav.bot.video.entity.Video;

import java.util.List;

/**
 * 爬虫服务接口
 */
public interface ICrawlerService {

    /**
     * 抓取并保存新视频
     * @return 新增的视频列表
     */
    List<Video> crawlAndSaveNewVideos();

    /**
     * 抓取并保存指定页数的新视频
     * @param pages 页数
     * @return 新增的视频列表
     */
    List<Video> crawlAndSaveNewVideos(int pages);

    /**
     * 获取未推送的视频
     * @return 未推送的视频列表
     */
    List<Video> getUnpushedVideos();

    /**
     * 标记视频为已推送
     * @param videoId 视频ID
     */
    void markAsPushed(Long videoId);
}
