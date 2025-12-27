package com.missav.bot.common.config;

import com.missav.bot.bot.MissavBot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Configuration
public class BotConfig {

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
