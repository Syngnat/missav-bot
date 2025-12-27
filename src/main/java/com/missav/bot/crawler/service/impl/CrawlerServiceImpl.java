package com.missav.bot.crawler.service.impl;

import com.missav.bot.crawler.MissavCrawler;
import com.missav.bot.crawler.service.ICrawlerService;
import com.missav.bot.video.entity.Video;
import com.missav.bot.video.mapper.VideoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerServiceImpl implements ICrawlerService {

    private final MissavCrawler crawler;
    private final VideoMapper videoMapper;

    @Override
    @Transactional
    public List<Video> crawlAndSaveNewVideos() {
        return crawlAndSaveNewVideos(1);
    }

    @Override
    @Transactional
    public List<Video> crawlAndSaveNewVideos(int pages) {
        List<Video> crawledVideos = crawler.crawlNewVideos(pages);
        List<Video> newVideos = new ArrayList<>();

        for (Video video : crawledVideos) {
            if (video.getCode() == null) {
                continue;
            }

            if (videoMapper.existsByCode(video.getCode())) {
                log.debug("视频已存在: {}", video.getCode());
                continue;
            }

            if (video.getDetailUrl() != null &&
                (video.getActresses() == null || video.getPreviewUrl() == null)) {
                try {
                    Video detail = crawler.crawlVideoDetail(video.getDetailUrl());
                    if (detail != null) {
                        mergeVideoInfo(video, detail);
                    }
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            video.setPushed(false);
            videoMapper.insert(video);
            newVideos.add(video);
            log.info("新视频入库: {} - {}", video.getCode(), video.getTitle());
        }

        log.info("本次抓取完成，新增{}个视频", newVideos.size());
        return newVideos;
    }

    private void mergeVideoInfo(Video target, Video source) {
        if (source.getActresses() != null && target.getActresses() == null) {
            target.setActresses(source.getActresses());
        }
        if (source.getTags() != null && target.getTags() == null) {
            target.setTags(source.getTags());
        }
        if (source.getCoverUrl() != null && target.getCoverUrl() == null) {
            target.setCoverUrl(source.getCoverUrl());
        }
        if (source.getPreviewUrl() != null && target.getPreviewUrl() == null) {
            target.setPreviewUrl(source.getPreviewUrl());
        }
        if (source.getDuration() != null && target.getDuration() == null) {
            target.setDuration(source.getDuration());
        }
        if (source.getTitle() != null && target.getTitle() == null) {
            target.setTitle(source.getTitle());
        }
    }

    @Override
    public List<Video> getUnpushedVideos() {
        return videoMapper.selectUnpushedVideos();
    }

    @Override
    @Transactional
    public void markAsPushed(Long videoId) {
        Video video = videoMapper.selectById(videoId);
        if (video != null) {
            video.setPushed(true);
            videoMapper.updateById(video);
        }
    }
}
