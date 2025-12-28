package com.missav.bot.crawler;

import com.missav.bot.video.entity.Video;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
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
    private volatile LocalDateTime cookieInitTime = null;  // Cookie 初始化时间
    private static final Duration COOKIE_EXPIRE_DURATION = Duration.ofMinutes(10);  // Cookie 有效期10分钟

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
     * 初始化 Cookie - 多次请求建立会话
     */
    private void initCookies() {
        // 检查 Cookie 是否需要刷新
        boolean needRefresh = cookieStore.isEmpty()
                || cookieInitTime == null
                || Duration.between(cookieInitTime, LocalDateTime.now()).compareTo(COOKIE_EXPIRE_DURATION) > 0;

        if (!needRefresh) {
            log.debug("Cookie 仍然有效，跳过初始化（已使用 {} 分钟）",
                Duration.between(cookieInitTime, LocalDateTime.now()).toMinutes());
            return;
        }

        try {
            if (cookieStore.isEmpty()) {
                log.info("初始化 Cookie（预热会话）...");
            } else {
                log.info("Cookie 已过期，重新初始化（上次初始化: {}）", cookieInitTime);
                cookieStore.clear();  // 清除过期 Cookie
            }

            // 网站需要多次请求才能建立有效会话，进行 2-3 次预热请求
            for (int i = 1; i <= 3; i++) {
                fetchHtml(NEW_VIDEOS_URL + "?page=2");
                if (i < 3) {
                    Thread.sleep(1000); // 请求间隔 1 秒
                }
            }

            cookieInitTime = LocalDateTime.now();  // 记录初始化时间
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
            log.debug("HTTP 响应状态: {} - {}", response.code(), response.message());
            log.debug("最终 URL (重定向后): {}", response.request().url());

            if (!response.isSuccessful()) {
                log.warn("请求失败: {} - {}", url, response.code());
                return null;
            }

            String html = response.body() != null ? response.body().string() : null;
            if (html != null) {
                log.debug("HTML 长度: {} 字符", html.length());
                log.debug("HTML 前500字符: {}", html.substring(0, Math.min(500, html.length())));
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

        log.info("页面标题: {}", doc.title());

        // 首先尝试从 script 标签中提取 JSON 数据（处理客户端渲染）
        log.info("开始尝试 JSON 提取...");
        List<Video> jsonVideos = extractVideosFromJson(doc);
        if (!jsonVideos.isEmpty()) {
            log.info("✓ JSON 提取成功，获得 {} 个视频", jsonVideos.size());
            return jsonVideos;
        }

        // 降级方案：使用传统 HTML 解析
        log.info("✗ JSON 提取失败（返回 0 个视频），降级到 HTML 解析");
        log.info("页面包含的主要 div 类: {}", doc.select("div[class]").stream()
            .limit(10)
            .map(e -> e.className())
            .distinct()
            .toList());

        // 尝试多种选择器
        Elements videoCards = doc.select("div.video-card, article.video, div[class*=thumbnail]");
        log.info("选择器1匹配到 {} 个元素", videoCards.size());

        if (videoCards.isEmpty()) {
            videoCards = doc.select("div.group");
            log.info("选择器2(div.group)匹配到 {} 个元素", videoCards.size());
        }

        if (videoCards.isEmpty()) {
            videoCards = doc.select("a[href*='/']");
            log.info("选择器3(a[href])匹配到 {} 个元素", videoCards.size());

            if (!videoCards.isEmpty()) {
                videoCards = videoCards.stream()
                    .filter(e -> {
                        String href = e.attr("href");
                        return href != null && CODE_PATTERN.matcher(href).find();
                    })
                    .collect(Elements::new, Elements::add, Elements::addAll);
                log.info("过滤后包含番号的链接: {} 个", videoCards.size());
            }
        }

        log.info("最终使用的选择器匹配到 {} 个视频卡片", videoCards.size());

        if (videoCards.isEmpty()) {
            log.warn("未找到任何视频卡片，输出HTML前1000字符用于调试:");
            log.warn(html.substring(0, Math.min(1000, html.length())));
        }

        for (Element card : videoCards) {
            try {
                Video video = parseVideoCard(card);
                if (video != null && video.getCode() != null) {
                    videos.add(video);
                } else {
                    log.warn("解析视频卡片失败，未提取到番号。元素HTML: {}",
                        card.html().substring(0, Math.min(500, card.html().length())));
                    if (video != null) {
                        log.warn("提取到的信息: title={}, detailUrl={}, code={}",
                            video.getTitle(), video.getDetailUrl(), video.getCode());
                    }
                }
            } catch (Exception e) {
                log.warn("解析视频卡片异常", e);
            }
        }

        return videos;
    }

    /**
     * 使用 Selenium 提取客户端渲染的视频列表
     */
    private List<Video> extractVideosWithSelenium(String url) {
        log.warn("========== 启动无头浏览器提取视频数据 ==========");
        WebDriver driver = null;
        try {
            WebDriverManager.chromedriver().setup();

            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless=new");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
            options.addArguments("--user-agent=" + userAgent);

            driver = new ChromeDriver(options);
            driver.get(url);

            log.info("等待页面加载完成...");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("div.group")));

            Thread.sleep(3000);

            log.info("提取渲染后的 HTML");
            String renderedHtml = driver.getPageSource();
            Document doc = Jsoup.parse(renderedHtml);

            List<Video> videos = new ArrayList<>();
            Elements videoCards = doc.select("div.group");
            log.info("Selenium 提取到 {} 个视频卡片", videoCards.size());

            if (!videoCards.isEmpty()) {
                log.warn("========== 第一个视频卡片的 HTML（用于调试）==========");
                log.warn(videoCards.first().html().substring(0, Math.min(1000, videoCards.first().html().length())));
                log.warn("========== 第一个视频卡片的 HTML 结束 ==========");
            }

            for (Element card : videoCards) {
                try {
                    Video video = new Video();

                    Element link = card.selectFirst("a[href]");
                    if (link != null) {
                        String href = link.attr("href");
                        if (href != null && !href.isEmpty() && !href.equals("#") && !href.equals("javascript:;")) {
                            video.setDetailUrl(href.startsWith("http") ? href : BASE_URL + href);
                            String code = extractCode(href);
                            if (code == null) {
                                code = extractCodeFromUrl(href);
                            }
                            video.setCode(code);
                        }
                    }

                    Element img = card.selectFirst("img[src], img[data-src]");
                    if (img != null) {
                        String coverUrl = extractImageUrl(img);
                        if (coverUrl != null && !coverUrl.isEmpty()) {
                            video.setCoverUrl(coverUrl);
                        }
                    }

                    if (video.getCode() != null) {
                        videos.add(video);
                    }
                } catch (Exception e) {
                    log.warn("解析视频卡片异常", e);
                }
            }

            log.warn("✓ Selenium 提取完成，获得 {} 个视频", videos.size());
            return videos;

        } catch (Exception e) {
            log.error("Selenium 提取失败", e);
            return new ArrayList<>();
        } finally {
            if (driver != null) {
                driver.quit();
            }
            log.warn("========== 无头浏览器已关闭 ==========");
        }
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

        // 如果仍然没有提取到番号，尝试从URL路径中提取作为备用标识
        if (code == null && video.getDetailUrl() != null) {
            code = extractCodeFromUrl(video.getDetailUrl());
        }
        video.setCode(code);

        // 提取封面图
        Element img = card.selectFirst("img");
        if (img != null) {
            String coverUrl = extractImageUrl(img);
            if (coverUrl != null && !coverUrl.isEmpty()) {
                video.setCoverUrl(coverUrl);
            }
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
        // 假设每页平均 12 个视频，计算需要的最大页数（向上取整并多爬1页以确保足够）
        int maxPages = limit != null ? ((limit + 11) / 12 + 1) : Integer.MAX_VALUE;

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
                log.info("演员 {} 第{}页抓取到{}个视频，当前总数: {}", actorName, page, pageVideos.size(), videos.size());

                if (limit != null && videos.size() >= limit) {
                    videos = videos.subList(0, limit);
                    log.info("已达到限制数量 {}，停止抓取", limit);
                    break;
                }

                page++;
                Thread.sleep(2000);
            }
        } catch (Exception e) {
            log.error("抓取演员 {} 的作品失败", actorName, e);
        }

        log.info("演员作品抓取完成，共抓取 {} 个视频", videos.size());
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
        int maxPages = limit != null ? ((limit + 11) / 12 + 1) : Integer.MAX_VALUE;

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

                // 如果第一页解析失败，尝试使用 Selenium
                if (pageVideos.isEmpty() && page == 1) {
                    log.warn("第一页 HTML 解析失败，尝试使用 Selenium 无头浏览器");
                    pageVideos = extractVideosWithSelenium(url);
                }

                if (pageVideos.isEmpty()) {
                    break;
                }

                videos.addAll(pageVideos);
                log.info("关键词 {} 第{}页抓取到{}个视频，当前总数: {}", keyword, page, pageVideos.size(), videos.size());

                if (limit != null && videos.size() >= limit) {
                    videos = videos.subList(0, limit);
                    log.info("已达到限制数量 {}，停止抓取", limit);
                    break;
                }

                page++;
                Thread.sleep(2000);
            }
        } catch (Exception e) {
            log.error("搜索关键词 {} 失败", keyword, e);
        }

        log.info("关键词搜索完成，共抓取 {} 个视频", videos.size());
        return videos;
    }

    /**
     * 从 img 元素中提取真实的图片 URL
     * 尝试多个可能的属性，过滤掉 base64 占位符
     */
    private String extractImageUrl(Element img) {
        if (img == null) {
            return null;
        }

        // 按优先级尝试多个属性
        String[] attributes = {"data-original", "data-lazy-src", "data-src", "srcset", "src"};

        for (String attr : attributes) {
            String url = img.attr(attr);
            if (url != null && !url.isEmpty() && !url.startsWith("data:")) {
                // 如果是 srcset，取第一个 URL
                if (attr.equals("srcset") && url.contains(" ")) {
                    url = url.split("\\s+")[0];
                }
                // 验证是否为有效的 HTTP/HTTPS URL
                if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("/")) {
                    return url;
                }
            }
        }

        return null;
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
     * 从URL路径中提取标识符作为备用番号
     * 例如: https://missav.ai/xxx/yyy/zzz → zzz
     */
    private String extractCodeFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        try {
            // 移除查询参数和锚点
            String path = url.split("\\?")[0].split("#")[0];
            // 移除末尾的斜杠
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            // 提取最后一段路径
            String[] parts = path.split("/");
            if (parts.length > 0) {
                String lastPart = parts[parts.length - 1];
                // 如果最后一段不为空，使用它作为标识符
                if (!lastPart.isEmpty()) {
                    log.debug("从URL提取备用标识符: {} -> {}", url, lastPart);
                    return lastPart.toUpperCase();
                }
            }
        } catch (Exception e) {
            log.warn("从URL提取标识符失败: {}", url, e);
        }
        return null;
    }

    /**
     * 从页面 script 标签中提取 JSON 数据（处理客户端渲染页面）
     */
    private List<Video> extractVideosFromJson(Document doc) {
        List<Video> videos = new ArrayList<>();

        // 查找所有 script 标签
        Elements scripts = doc.select("script");
        log.info("找到 {} 个 script 标签", scripts.size());

        int scriptIndex = 0;
        Element obfuscatedScript = null;
        int obfuscatedScriptIndex = -1;

        for (Element script : scripts) {
            String scriptContent = script.html();
            scriptIndex++;

            // 检测混淆代码（eval + function packing）
            boolean isObfuscated = scriptContent.contains("eval(function(p,a,c,k,e,d)");
            boolean hasVideoData = scriptContent.contains("dvd_id") || scriptContent.contains("uuid");

            // 输出 script 信息
            if (scriptContent.length() > 0) {
                String prefix = isObfuscated ? "【混淆代码】" : "";
                log.info("{}Script #{} (长度: {} 字符) 前200字符: {}",
                    prefix,
                    scriptIndex,
                    scriptContent.length(),
                    scriptContent.substring(0, Math.min(200, scriptContent.length())));

                // 如果是混淆代码且长度超过3000字符，保存引用（可能包含视频数据）
                if (isObfuscated && scriptContent.length() > 3000) {
                    obfuscatedScript = script;
                    obfuscatedScriptIndex = scriptIndex;
                    log.warn("检测到大型混淆代码 Script #{}，稍后输出完整内容", scriptIndex);
                }
            } else {
                log.info("Script #{} 为空", scriptIndex);
            }

            // 查找可能包含视频数据的 JSON
            if (hasVideoData) {
                log.info("发现可能包含视频数据的 script 标签（长度: {} 字符）", scriptContent.length());

                try {
                    // 尝试提取 JSON 数据
                    // 模式1: window.xxx = {...}
                    String jsonPattern1 = "window\\.[\\w_]+\\s*=\\s*(\\{.*?\\});?$";
                    java.util.regex.Pattern p1 = java.util.regex.Pattern.compile(jsonPattern1, java.util.regex.Pattern.DOTALL);
                    Matcher m1 = p1.matcher(scriptContent);

                    if (m1.find()) {
                        String jsonStr = m1.group(1);
                        log.info("提取到 JSON 字符串（前200字符）: {}",
                            jsonStr.substring(0, Math.min(200, jsonStr.length())));

                        // TODO: 使用 JSON 解析库（如 Jackson 或 Gson）解析数据
                        // 这里先简单提取 dvd_id
                        videos.addAll(parseJsonToVideos(jsonStr));
                    } else {
                        log.info("未匹配到 window.xxx = {{...}} 模式");
                    }
                } catch (Exception e) {
                    log.warn("JSON 提取失败", e);
                }
            }
        }

        // 如果发现混淆代码，输出完整内容用于分析
        if (obfuscatedScript != null) {
            String fullContent = obfuscatedScript.html();
            log.warn("========== 混淆代码 Script #{} 完整内容开始 ==========", obfuscatedScriptIndex);
            log.warn(fullContent);
            log.warn("========== 混淆代码 Script #{} 完整内容结束 ==========", obfuscatedScriptIndex);

            // 尝试分析混淆代码的特征
            analyzeObfuscatedCode(fullContent, obfuscatedScriptIndex);
        }

        log.info("JSON 提取完成，共获得 {} 个视频", videos.size());
        return videos;
    }

    /**
     * 分析混淆代码，尝试找到视频数据的加载方式
     */
    private void analyzeObfuscatedCode(String code, int scriptIndex) {
        log.info("========== 开始分析混淆代码 Script #{} ==========", scriptIndex);

        // 检测是否包含 fetch/axios/ajax 等网络请求
        if (code.contains("fetch(") || code.contains("axios") || code.contains("$.ajax") || code.contains("XMLHttpRequest")) {
            log.warn("✓ 检测到网络请求相关代码（fetch/axios/ajax/XMLHttpRequest）");

            // 尝试提取 API URL 模式
            Pattern urlPattern = Pattern.compile("(['\"])(https?://[^'\"]+|/api/[^'\"]+)\\1");
            Matcher matcher = urlPattern.matcher(code);
            Set<String> urls = new HashSet<>();
            while (matcher.find()) {
                String url = matcher.group(2);
                if (url.contains("api") || url.contains("search") || url.contains("video")) {
                    urls.add(url);
                }
            }
            if (!urls.isEmpty()) {
                log.warn("✓ 发现可能的 API 端点:");
                urls.forEach(url -> log.warn("  - {}", url));
            }
        }

        // 检测 Alpine.js 或 Vue.js 相关代码
        if (code.contains("Alpine") || code.contains("x-data") || code.contains("Vue")) {
            log.warn("✓ 检测到 Alpine.js/Vue.js 框架代码");
        }

        // 检测数据挂载到 window 对象
        Pattern windowPattern = Pattern.compile("window\\.([\\w_]+)\\s*=");
        Matcher windowMatcher = windowPattern.matcher(code);
        Set<String> windowVars = new HashSet<>();
        while (windowMatcher.find()) {
            windowVars.add(windowMatcher.group(1));
        }
        if (!windowVars.isEmpty()) {
            log.warn("✓ 发现挂载到 window 的变量:");
            windowVars.forEach(var -> log.warn("  - window.{}", var));
        }

        // 检测解混淆后的函数调用特征
        if (code.contains("eval(function(p,a,c,k,e,d)")) {
            log.warn("✓ 确认为 eval 函数打包混淆");
            log.warn("  建议方案:");
            log.warn("  1. 在浏览器控制台执行此代码并拦截网络请求，找到真实 API");
            log.warn("  2. 使用无头浏览器（Selenium/Playwright）执行 JavaScript 并提取渲染后的 DOM");
            log.warn("  3. 反向分析混淆代码，提取关键变量和函数");
        }

        log.info("========== 混淆代码分析完成 ==========");
    }

    /**
     * 解析 JSON 字符串为视频列表（简化版）
     */
    private List<Video> parseJsonToVideos(String jsonStr) {
        List<Video> videos = new ArrayList<>();

        // 简单的正则提取（临时方案）
        // 更好的做法是使用 Jackson/Gson，但需要先分析具体的 JSON 结构
        Pattern dvdIdPattern = Pattern.compile("\"dvd_id\"\\s*:\\s*\"([^\"]+)\"");
        Pattern uuidPattern = Pattern.compile("\"uuid\"\\s*:\\s*\"([^\"]+)\"");

        Matcher matcher = dvdIdPattern.matcher(jsonStr);
        while (matcher.find()) {
            String dvdId = matcher.group(1);
            Video video = new Video();
            video.setCode(dvdId.toUpperCase());
            video.setDetailUrl(BASE_URL + "/" + dvdId);
            videos.add(video);
            log.debug("从 JSON 提取到视频: {}", dvdId);
        }

        // 如果没有 dvd_id，尝试 uuid
        if (videos.isEmpty()) {
            matcher = uuidPattern.matcher(jsonStr);
            while (matcher.find()) {
                String uuid = matcher.group(1);
                Video video = new Video();
                video.setCode(uuid.toUpperCase());
                video.setDetailUrl(BASE_URL + "/" + uuid);
                videos.add(video);
                log.debug("从 JSON 提取到视频（UUID）: {}", uuid);
            }
        }

        return videos;
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
