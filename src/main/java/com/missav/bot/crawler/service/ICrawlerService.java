package com.missav.bot.crawler.service;

import com.missav.bot.crawler.CrawlResult;
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

    /**
     * 按演员名爬取作品
     * @param actorName 演员名
     * @param limit 限制数量，null 表示全部
     * @return 爬取结果（包含新增、重复、总数等信息）
     */
    CrawlResult crawlByActor(String actorName, Integer limit);

    /**
     * 按番号爬取作品
     * @param code 番号
     * @return 爬取的视频，不存在返回 null
     */
    Video crawlByCode(String code);

    /**
     * 按关键词搜索爬取
     * @param keyword 关键词
     * @param limit 限制数量，null 表示全部
     * @return 爬取结果（包含新增、重复、总数等信息）
     */
    CrawlResult crawlByKeyword(String keyword, Integer limit);
}
