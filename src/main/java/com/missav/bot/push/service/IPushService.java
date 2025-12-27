package com.missav.bot.push.service;

import com.missav.bot.video.entity.Video;

/**
 * 推送服务接口
 */
public interface IPushService {

    /**
     * 推送视频到所有匹配的订阅者
     * @param video 视频
     */
    void pushVideoToSubscribers(Video video);

    /**
     * 推送所有未推送的视频
     */
    void pushUnpushedVideos();
}
