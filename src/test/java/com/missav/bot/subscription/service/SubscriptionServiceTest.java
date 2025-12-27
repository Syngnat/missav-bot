package com.missav.bot.subscription.service;

import com.missav.bot.subscription.mapper.SubscriptionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SubscriptionServiceTest {

    @Autowired
    private SubscriptionMapper subscriptionMapper;

    @Test
    void testExistsByChatIdAndTypeAndKeyword() {
        // 测试订阅查询
        boolean exists = subscriptionMapper.existsByChatIdAndTypeAndKeyword(123456L, "ALL", null);
        assertNotNull(exists);
    }
}
