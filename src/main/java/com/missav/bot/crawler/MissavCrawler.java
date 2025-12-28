package com.missav.bot.crawler;

import com.missav.bot.video.entity.Video;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class MissavCrawler {

    private static final String BASE_URL = "https://missav.ai";
    private static final String NEW_VIDEOS_URL = BASE_URL + "/new";

    private static final Pattern CODE_PATTERN = Pattern.compile("([A-Z]+-\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)\\s*分");

    private final OkHttpClient httpClient;
    private final Map<String, List<Cookie>> cookieStore = new HashMap<>();

    @Value("${crawler.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36}")
    private String userAgent;

    public MissavCrawler() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .cookieJar(new CookieJar() {
                    @Override
                    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                        cookieStore.put(url.host(), cookies);
                    }

                    @Override
                    public List<Cookie> loadForRequest(HttpUrl url) {
                        List<Cookie> cookies = cookieStore.get(url.host());
                        return cookies != null ? cookies : new ArrayList<>();
                    }
                })
                .build();
    }

    /**
     * 初始化 Cookie - 访问一个可以访问的页面获取 Cookie
     */
    private void initCookies() {
        // 如果已有 Cookie，跳过初始化
        if (!cookieStore.isEmpty()) {
            log.debug("Cookie 已存在，跳过初始化");
            return;
        }

        try {
            log.info("初始化 Cookie...");
            // 访问第二页获取 Cookie（第一页会 403）
            fetchHtml(NEW_VIDEOS_URL + "?page=2");
            log.info("Cookie 初始化完成");
        } catch (Exception e) {
            log.warn("Cookie 初始化失败", e);
        }
    }

    /**
     * 抓取最新视频列表
     */
    public List<Video> crawlNewVideos() {
        return crawlNewVideos(1);
    }

    /**
     * 抓取指定页数的最新视频
     */
    public List<Video> crawlNewVideos(int pages) {
        List<Video> videos = new ArrayList<>();

        initCookies();

        for (int page = 1; page <= pages; page++) {
            try {
                String url = page == 1 ? NEW_VIDEOS_URL : NEW_VIDEOS_URL + "?page=" + page;
                log.info("正在抓取: {}", url);

                String html = fetchHtml(url);
                if (html == null) {
                    log.warn("获取页面失败: {}", url);
                    continue;
                }

                List<Video> pageVideos = parseVideoList(html);
                videos.addAll(pageVideos);
                log.info("第{}页抓取到{}个视频", page, pageVideos.size());

                // 避免请求过于频繁
                if (page < pages) {
                    Thread.sleep(2000);
                }
            } catch (Exception e) {
                log.error("抓取第{}页失败", page, e);
            }
        }

        return videos;
    }

    /**
     * 抓取视频详情
     */
    public Video crawlVideoDetail(String detailUrl) {
        try {
            log.info("正在抓取视频详情: {}", detailUrl);
            String html = fetchHtml(detailUrl);
            if (html == null) {
                return null;
            }
            return parseVideoDetail(html, detailUrl);
        } catch (Exception e) {
            log.error("抓取视频详情失败: {}", detailUrl, e);
            return null;
        }
    }

    private String fetchHtml(String url) {
        // 添加随机延迟，避免请求过快
        try {
            Thread.sleep(1000 + (long)(Math.random() * 2000)); // 1-3秒随机延迟
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")
                // 删除 Accept-Encoding，让 OkHttp 自动处理压缩
                .header("Referer", "https://missav.ai/")
                .header("Connection", "keep-alive")
                .header("Cache-Control", "max-age=0")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            log.info("HTTP 响应状态: {} - {}", response.code(), response.message());
            log.info("最终 URL (重定向后): {}", response.request().url());

            if (!response.isSuccessful()) {
                log.warn("请求失败: {} - {}", url, response.code());
                return null;
            }

            String html = response.body() != null ? response.body().string() : null;
            if (html != null) {
                log.info("HTML 长度: {} 字符", html.length());
                log.info("HTML 前500字符: {}", html.substring(0, Math.min(500, html.length())));
            } else {
                log.warn("响应体为空");
            }

            return html;
        } catch (IOException e) {
            log.error("请求异常: {}", url, e);
            return null;
        }
    }

    /**
     * 解析视频列表页
     */
    private List<Video> parseVideoList(String html) {
        List<Video> videos = new ArrayList<>();
        Document doc = Jsoup.parse(html);

        // 输出页面基本信息用于调试
        log.info("页面标题: {}", doc.title());
        log.info("页面包含的主要 div 类: {}", doc.select("div[class]").stream()
            .limit(10)
            .map(e -> e.className())
            .distinct()
            .toList());

        // 尝试多种选择器
        Elements videoCards = doc.select("div.video-card, article.video, div[class*=thumbnail]");
        log.debug("选择器1匹配到 {} 个元素", videoCards.size());

        if (videoCards.isEmpty()) {
            // 尝试更通用的选择器
            videoCards = doc.select("div.group");
            log.debug("选择器2(div.group)匹配到 {} 个元素", videoCards.size());
        }

        if (videoCards.isEmpty()) {
            // 尝试查找所有包含视频链接的元素
            videoCards = doc.select("a[href*='/']");
            log.debug("选择器3(a[href])匹配到 {} 个元素", videoCards.size());

            // 如果找到了链接，过滤出可能是视频的链接
            if (!videoCards.isEmpty()) {
                videoCards = videoCards.stream()
                    .filter(e -> {
                        String href = e.attr("href");
                        // 过滤出包含番号模式的链接
                        return href != null && CODE_PATTERN.matcher(href).find();
                    })
                    .collect(Elements::new, Elements::add, Elements::addAll);
                log.info("过滤后包含番号的链接: {} 个", videoCards.size());
            }
        }

        log.info("最终使用的选择器匹配到 {} 个视频卡片", videoCards.size());

        for (Element card : videoCards) {
            try {
                Video video = parseVideoCard(card);
                if (video != null && video.getCode() != null) {
                    videos.add(video);
                }
            } catch (Exception e) {
                log.debug("解析视频卡片失败", e);
            }
        }

        return videos;
    }

    /**
     * 解析视频卡片
     */
    private Video parseVideoCard(Element card) {
        Video video = new Video();

        // 提取链接
        Element link = card.selectFirst("a[href*=missav]");
        if (link == null) {
            link = card.selectFirst("a");
        }
        if (link != null) {
            video.setDetailUrl(link.attr("abs:href"));
        }

        // 提取标题
        Element titleEl = card.selectFirst("h3, h4, .title, [class*=title]");
        if (titleEl != null) {
            video.setTitle(titleEl.text().trim());
        }

        // 从标题或链接中提取番号
        String code = extractCode(video.getTitle());
        if (code == null && video.getDetailUrl() != null) {
            code = extractCode(video.getDetailUrl());
        }
        video.setCode(code);

        // 提取封面图
        Element img = card.selectFirst("img");
        if (img != null) {
            String coverUrl = img.attr("data-src");
            if (coverUrl.isEmpty()) {
                coverUrl = img.attr("src");
            }
            video.setCoverUrl(coverUrl);
        }

        // 提取时长
        Element durationEl = card.selectFirst(".duration, [class*=duration], span:contains(分)");
        if (durationEl != null) {
            video.setDuration(extractDuration(durationEl.text()));
        }

        return video;
    }

    /**
     * 解析视频详情页
     */
    private Video parseVideoDetail(String html, String detailUrl) {
        Document doc = Jsoup.parse(html);
        Video video = new Video();
        video.setDetailUrl(detailUrl);

        // 提取标题
        Element titleEl = doc.selectFirst("h1, .video-title, [class*=title]");
        if (titleEl != null) {
            video.setTitle(titleEl.text().trim());
        }

        // 提取番号
        video.setCode(extractCode(video.getTitle()));
        if (video.getCode() == null) {
            video.setCode(extractCode(detailUrl));
        }

        // 提取演员
        Elements actressEls = doc.select("a[href*=actress], a[href*=actor], .actress");
        if (!actressEls.isEmpty()) {
            List<String> actresses = new ArrayList<>();
            for (Element el : actressEls) {
                actresses.add(el.text().trim());
            }
            video.setActresses(String.join(", ", actresses));
        }

        // 提取标签
        Elements tagEls = doc.select("a[href*=tag], a[href*=genre], .tag");
        if (!tagEls.isEmpty()) {
            List<String> tags = new ArrayList<>();
            for (Element el : tagEls) {
                tags.add(el.text().trim());
            }
            video.setTags(String.join(", ", tags));
        }

        // 提取封面图
        Element coverEl = doc.selectFirst("meta[property=og:image], img.cover, .video-cover img");
        if (coverEl != null) {
            video.setCoverUrl(coverEl.attr("content"));
            if (video.getCoverUrl().isEmpty()) {
                video.setCoverUrl(coverEl.attr("src"));
            }
        }

        // 提取预览视频 - 尝试多种方式
        Element videoEl = doc.selectFirst("video");
        if (videoEl != null) {
            // 尝试从video标签的各种属性中获取
            String previewUrl = videoEl.attr("data-src");
            if (previewUrl.isEmpty()) previewUrl = videoEl.attr("src");
            if (previewUrl.isEmpty()) {
                Element source = videoEl.selectFirst("source");
                if (source != null) {
                    previewUrl = source.attr("src");
                    if (previewUrl.isEmpty()) previewUrl = source.attr("data-src");
                }
            }
            if (!previewUrl.isEmpty()) {
                video.setPreviewUrl(previewUrl);
            }
        }

        // 如果还是没找到,尝试从script标签中提取
        if (video.getPreviewUrl() == null || video.getPreviewUrl().isEmpty()) {
            Elements scripts = doc.select("script");
            for (Element script : scripts) {
                String scriptText = script.html();
                if (scriptText.contains(".mp4") || scriptText.contains("preview")) {
                    // 尝试提取URL模式
                    int start = scriptText.indexOf("https://");
                    if (start != -1) {
                        int end = scriptText.indexOf(".mp4", start);
                        if (end != -1) {
                            String url = scriptText.substring(start, end + 4);
                            video.setPreviewUrl(url);
                            break;
                        }
                    }
                }
            }
        }

        // 提取时长
        Element durationEl = doc.selectFirst(".duration, [class*=duration], span:contains(分钟)");
        if (durationEl != null) {
            video.setDuration(extractDuration(durationEl.text()));
        }

        return video;
    }

    /**
     * 按演员名爬取作品
     */
    public List<Video> crawlByActor(String actorName, Integer limit) {
        List<Video> videos = new ArrayList<>();
        int page = 1;
        int maxPages = limit != null ? (limit / 30 + 1) : Integer.MAX_VALUE;

        try {
            initCookies();
            String encodedName = URLEncoder.encode(actorName, StandardCharsets.UTF_8);
            while (page <= maxPages) {
                String url = BASE_URL + "/actresses/" + encodedName + (page > 1 ? "?page=" + page : "");
                log.info("正在抓取演员作品: {}", url);

                String html = fetchHtml(url);
                if (html == null) {
                    break;
                }

                List<Video> pageVideos = parseVideoList(html);
                if (pageVideos.isEmpty()) {
                    break;
                }

                videos.addAll(pageVideos);
                log.info("演员 {} 第{}页抓取到{}个视频", actorName, page, pageVideos.size());

                if (limit != null && videos.size() >= limit) {
                    videos = videos.subList(0, limit);
                    break;
                }

                page++;
                Thread.sleep(2000);
            }
        } catch (Exception e) {
            log.error("抓取演员 {} 的作品失败", actorName, e);
        }

        return videos;
    }

    /**
     * 按番号爬取作品
     */
    public Video crawlByCode(String code) {
        try {
            // MissAV 的番号详情页 URL 格式通常是 /番号
            String url = BASE_URL + "/" + code;
            log.info("正在按番号爬取: {}", url);

            String html = fetchHtml(url);
            if (html == null) {
                return null;
            }

            Video video = parseVideoDetail(html, url);
            if (video != null && video.getCode() == null) {
                video.setCode(code.toUpperCase());
            }

            return video;
        } catch (Exception e) {
            log.error("按番号 {} 爬取失败", code, e);
            return null;
        }
    }

    /**
     * 按关键词搜索爬取
     */
    public List<Video> crawlByKeyword(String keyword, Integer limit) {
        List<Video> videos = new ArrayList<>();
        int page = 1;
        int maxPages = limit != null ? (limit / 30 + 1) : Integer.MAX_VALUE;

        try {
            initCookies();
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            while (page <= maxPages) {
                String url = BASE_URL + "/search/" + encodedKeyword + (page > 1 ? "?page=" + page : "");
                log.info("正在搜索关键词: {}", url);

                String html = fetchHtml(url);
                if (html == null) {
                    break;
                }

                List<Video> pageVideos = parseVideoList(html);
                if (pageVideos.isEmpty()) {
                    break;
                }

                videos.addAll(pageVideos);
                log.info("关键词 {} 第{}页抓取到{}个视频", keyword, page, pageVideos.size());

                if (limit != null && videos.size() >= limit) {
                    videos = videos.subList(0, limit);
                    break;
                }

                page++;
                Thread.sleep(2000);
            }
        } catch (Exception e) {
            log.error("搜索关键词 {} 失败", keyword, e);
        }

        return videos;
    }

    /**
     * 从文本中提取番号
     */
    private String extractCode(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        Matcher matcher = CODE_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        return null;
    }

    /**
     * 从文本中提取时长（分钟）
     */
    private Integer extractDuration(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        Matcher matcher = DURATION_PATTERN.matcher(text);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        // 尝试直接解析数字
        try {
            return Integer.parseInt(text.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
