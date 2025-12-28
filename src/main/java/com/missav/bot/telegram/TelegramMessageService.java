package com.missav.bot.telegram;

import com.missav.bot.video.entity.Video;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Telegram 消息发送服务
 * 负责所有 Telegram 消息的格式化和发送
 */
@Slf4j
@Service
public class TelegramMessageService {

    private AbsSender bot;

    @Value("${telegram.bot.username:MissavBot}")
    private String botUsername;

    /**
     * 设置 Bot 实例（由 MissavBot 启动时调用）
     */
    public void setBot(AbsSender bot) {
        this.bot = bot;
    }

    /**
     * 推送视频到指定聊天
     * @return 是否推送成功
     */
    public boolean pushVideo(Long chatId, Video video) {
        try {
            String caption = formatVideoMessage(video);

            // 优先发送预览视频
            if (video.getPreviewUrl() != null && !video.getPreviewUrl().isEmpty()) {
                return sendVideoWithCaption(chatId, video.getPreviewUrl(), video.getCoverUrl(), caption);
            }

            // 其次发送封面图
            if (video.getCoverUrl() != null && !video.getCoverUrl().isEmpty()) {
                return sendPhotoWithCaption(chatId, video.getCoverUrl(), caption);
            }

            // 最后发送纯文本
            sendMarkdown(chatId, caption);
            return true;
        } catch (Exception e) {
            log.error("推送视频失败: chatId={}, code={}", chatId, video.getCode(), e);
            return false;
        }
    }

    /**
     * 格式化视频消息
     */
    private String formatVideoMessage(Video video) {
        StringBuilder sb = new StringBuilder();
        sb.append("🎬 *新片上架*\n\n");
        sb.append("📌 番号: `").append(escapeMarkdown(video.getCode())).append("`\n");

        if (video.getActresses() != null && !video.getActresses().isEmpty()) {
            sb.append("👩 演员: ").append(escapeMarkdown(video.getActresses())).append("\n");
        }

        if (video.getTags() != null && !video.getTags().isEmpty()) {
            sb.append("🏷️ 标签: ").append(formatTags(video.getTags())).append("\n");
        }

        if (video.getDuration() != null) {
            sb.append("⏱️ 时长: ").append(video.getDuration()).append(" 分钟\n");
        }

        sb.append("\n🔗 ").append(escapeMarkdown(video.getDetailUrl()));

        return sb.toString();
    }

    /**
     * 转义 Markdown 特殊字符
     */
    private String escapeMarkdown(String text) {
        if (text == null) return "";
        return text.replace("_", "\\_")
                   .replace("*", "\\*")
                   .replace("[", "\\[")
                   .replace("]", "\\]")
                   .replace("(", "\\(")
                   .replace(")", "\\)")
                   .replace("~", "\\~")
                   .replace("`", "\\`")
                   .replace(">", "\\>")
                   .replace("#", "\\#")
                   .replace("+", "\\+")
                   .replace("-", "\\-")
                   .replace("=", "\\=")
                   .replace("|", "\\|")
                   .replace("{", "\\{")
                   .replace("}", "\\}")
                   .replace(".", "\\.")
                   .replace("!", "\\!");
    }

    private String formatTags(String tags) {
        if (tags == null) return "";
        String[] tagArr = tags.split(",\\s*");
        StringBuilder sb = new StringBuilder();
        for (String tag : tagArr) {
            sb.append("#").append(escapeMarkdown(tag.trim())).append(" ");
        }
        return sb.toString().trim();
    }

    private boolean sendVideoWithCaption(Long chatId, String videoUrl, String thumbUrl, String caption) {
        try {
            SendVideo sendVideo = new SendVideo();
            sendVideo.setChatId(chatId.toString());
            sendVideo.setVideo(new InputFile(videoUrl));
            if (thumbUrl != null && !thumbUrl.isEmpty()) {
                sendVideo.setThumbnail(new InputFile(thumbUrl));
            }
            sendVideo.setCaption(caption);
            sendVideo.setParseMode("Markdown");
            bot.execute(sendVideo);
            return true;
        } catch (TelegramApiException e) {
            log.warn("发送视频失败，尝试发送图片: {}", e.getMessage());
            return sendPhotoWithCaption(chatId, thumbUrl, caption);
        }
    }

    private boolean sendPhotoWithCaption(Long chatId, String photoUrl, String caption) {
        try {
            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(chatId.toString());
            sendPhoto.setPhoto(new InputFile(photoUrl));
            sendPhoto.setCaption(caption);
            sendPhoto.setParseMode("Markdown");
            bot.execute(sendPhoto);
            return true;
        } catch (TelegramApiException e) {
            log.warn("发送图片失败，发送纯文本: {}", e.getMessage());
            sendMarkdown(chatId, caption);
            return true;
        }
    }

    private void sendMarkdown(Long chatId, String text) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text);
            message.setParseMode("Markdown");
            bot.execute(message);
        } catch (TelegramApiException e) {
            log.error("发送消息失败", e);
        }
    }
}
