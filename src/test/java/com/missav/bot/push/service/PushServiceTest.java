package com.missav.bot.push.service;

import com.missav.bot.push.mapper.PushRecordMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PushServiceTest {

    @Autowired
    private PushRecordMapper pushRecordMapper;

    @Test
    void testExistsByVideoIdAndChatIdAndStatus() {
        // 测试推送记录查询
        boolean exists = pushRecordMapper.existsByVideoIdAndChatIdAndStatus(1L, 123456L, "SUCCESS");
        assertNotNull(exists);
    }
}
