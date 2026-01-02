package com.missav.bot.common.config;

import com.missav.bot.bot.MissavBot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Configuration
public class BotConfig {

    @Value("${telegram.proxy.enabled:false}")
    private boolean proxyEnabled;

    @Value("${telegram.proxy.host:127.0.0.1}")
    private String proxyHost;

    @Value("${telegram.proxy.port:7890}")
    private int proxyPort;

    @Bean
    public DefaultBotOptions botOptions() {
        DefaultBotOptions options = new DefaultBotOptions();

        // 设置基本选项
        options.setGetUpdatesTimeout(75);  // 增加轮询超时到 75 秒
        options.setMaxThreads(1);  // 减少线程数

        if (proxyEnabled) {
            log.info("启用代理: {}:{}", proxyHost, proxyPort);
            options.setProxyHost(proxyHost);
            options.setProxyPort(proxyPort);
            options.setProxyType(DefaultBotOptions.ProxyType.HTTP);
        } else {
            log.info("未启用代理");
        }

        return options;
    }

    @Bean
    public TelegramBotsApi telegramBotsApi(MissavBot missavBot) throws TelegramApiException {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        try {
            botsApi.registerBot(missavBot);
            log.info("Telegram Bot 注册成功");
        } catch (TelegramApiException e) {
            log.error("Telegram Bot 注册失败", e);
            throw e;
        }
        return botsApi;
    }
}
