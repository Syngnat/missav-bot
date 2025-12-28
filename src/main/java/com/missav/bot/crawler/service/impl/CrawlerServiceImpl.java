package com.missav.bot.crawler.service.impl;

import com.missav.bot.crawler.CrawlResult;
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
        int duplicateCount = 0;
        int invalidCount = 0;

        log.info("开始处理爬取到的 {} 个视频", crawledVideos.size());

        // 过滤无效视频
        List<Video> validVideos = new ArrayList<>();
        for (Video video : crawledVideos) {
            if (video.getCode() == null) {
                invalidCount++;
                log.debug("跳过无效视频（无番号）: {}", video.getTitle());
            } else {
                validVideos.add(video);
            }
        }

        if (validVideos.isEmpty()) {
            log.info("没有有效视频需要处理");
            log.info("本次抓取完成 - 总计: {}, 新增: 0, 重复: 0, 无效: {}",
                crawledVideos.size(), invalidCount);
            return newVideos;
        }

        // 批量检查重复
        List<String> codes = validVideos.stream().map(Video::getCode).toList();
        List<String> existingCodes = videoMapper.selectExistingCodes(codes);
        java.util.Set<String> existingCodeSet = new java.util.HashSet<>(existingCodes);
        duplicateCount = existingCodes.size();

        // 过滤出新视频
        for (Video video : validVideos) {
            if (existingCodeSet.contains(video.getCode())) {
                log.debug("视频已存在，跳过: {}", video.getCode());
            } else {
                newVideos.add(video);
            }
        }

        if (newVideos.isEmpty()) {
            log.info("本次抓取完成 - 总计: {}, 新增: 0, 重复: {}, 无效: {}",
                crawledVideos.size(), duplicateCount, invalidCount);
            return newVideos;
        }

        // 补充详情信息（如需要）
        for (Video video : newVideos) {
            if (video.getDetailUrl() != null &&
                (video.getActresses() == null || video.getPreviewUrl() == null)) {
                try {
                    Video detail = crawler.crawlVideoDetail(video.getDetailUrl());
                    if (detail != null) {
                        mergeVideoInfo(video, detail);
                    }
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            video.setPushed(false);
        }

        // 批量插入
        for (Video video : newVideos) {
            videoMapper.insert(video);
            log.info("新视频入库: {} - {}", video.getCode(), video.getTitle());
        }

        log.info("本次抓取完成 - 总计: {}, 新增: {}, 重复: {}, 无效: {}",
            crawledVideos.size(), newVideos.size(), duplicateCount, invalidCount);

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

    @Override
    @Transactional
    public CrawlResult crawlByActor(String actorName, Integer limit) {
        log.info("开始按演员爬取: {}, 限制: {}", actorName, limit);
        List<Video> crawledVideos = crawler.crawlByActor(actorName, limit);
        return saveAndReturnResult(crawledVideos);
    }

    @Override
    @Transactional
    public Video crawlByCode(String code) {
        log.info("开始按番号爬取: {}", code);

        // 先检查数据库是否已存在
        if (videoMapper.existsByCode(code)) {
            log.info("番号 {} 已存在于数据库", code);
            return videoMapper.selectByCode(code);
        }

        Video video = crawler.crawlByCode(code);
        if (video == null) {
            log.warn("未找到番号: {}", code);
            return null;
        }

        video.setPushed(false);
        videoMapper.insert(video);
        log.info("新视频入库: {} - {}", video.getCode(), video.getTitle());

        return video;
    }

    @Override
    @Transactional
    public CrawlResult crawlByKeyword(String keyword, Integer limit) {
        log.info("开始按关键词搜索: {}, 限制: {}", keyword, limit);
        List<Video> crawledVideos = crawler.crawlByKeyword(keyword, limit);
        return saveAndReturnResult(crawledVideos);
    }

    /**
     * 保存爬取的视频并返回爬取结果（去重）
     */
    private CrawlResult saveAndReturnResult(List<Video> crawledVideos) {
        List<Video> newVideos = new ArrayList<>();
        int duplicateCount = 0;
        int invalidCount = 0;

        log.info("开始处理爬取到的 {} 个视频", crawledVideos.size());

        // 过滤无效视频
        List<Video> validVideos = new ArrayList<>();
        for (Video video : crawledVideos) {
            if (video.getCode() == null) {
                invalidCount++;
                log.debug("跳过无效视频（无番号）: {}", video.getTitle());
            } else {
                validVideos.add(video);
            }
        }

        if (validVideos.isEmpty()) {
            log.info("没有有效视频需要处理");
            return new CrawlResult(newVideos, crawledVideos.size(), duplicateCount, invalidCount);
        }

        // 批量检查重复
        List<String> codes = validVideos.stream().map(Video::getCode).toList();
        List<String> existingCodes = videoMapper.selectExistingCodes(codes);
        java.util.Set<String> existingCodeSet = new java.util.HashSet<>(existingCodes);
        duplicateCount = existingCodes.size();

        // 过滤出新视频
        for (Video video : validVideos) {
            if (existingCodeSet.contains(video.getCode())) {
                log.debug("视频已存在，跳过: {}", video.getCode());
            } else {
                newVideos.add(video);
            }
        }

        if (newVideos.isEmpty()) {
            log.info("本次抓取完成 - 总计: {}, 新增: 0, 重复: {}, 无效: {}",
                crawledVideos.size(), duplicateCount, invalidCount);
            return new CrawlResult(newVideos, crawledVideos.size(), duplicateCount, invalidCount);
        }

        // 补充详情信息（如需要）
        for (Video video : newVideos) {
            if (video.getDetailUrl() != null &&
                (video.getActresses() == null || video.getPreviewUrl() == null)) {
                try {
                    Video detail = crawler.crawlVideoDetail(video.getDetailUrl());
                    if (detail != null) {
                        mergeVideoInfo(video, detail);
                    }
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            video.setPushed(false);
        }

        // 批量插入
        for (Video video : newVideos) {
            videoMapper.insert(video);
            log.info("新视频入库: {} - {}", video.getCode(), video.getTitle());
        }

        log.info("本次抓取完成 - 总计: {}, 新增: {}, 重复: {}, 无效: {}",
            crawledVideos.size(), newVideos.size(), duplicateCount, invalidCount);

        return new CrawlResult(newVideos, crawledVideos.size(), duplicateCount, invalidCount);
    }
}
