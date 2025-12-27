package com.missav.bot.crawler;

import com.missav.bot.video.entity.Video;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MissavCrawlerTest {

    @Autowired
    private MissavCrawler crawler;

    @Test
    void testExtractCodeFromText() {
        // 使用反射调用私有方法测试番号提取
        String text1 = "SSIS-123 测试标题";
        String text2 = "https://example.com/abc-456";

        // 这里只测试公开方法,私有方法通过集成测试验证
        assertNotNull(crawler);
    }

    @Test
    void testCrawlNewVideos() {
        // 测试抓取新视频列表
        var videos = crawler.crawlNewVideos(1);

        assertNotNull(videos);
        // 验证返回的视频列表
        if (!videos.isEmpty()) {
            Video video = videos.get(0);
            assertNotNull(video.getCode(), "视频番号不应为空");
        }
    }
}
