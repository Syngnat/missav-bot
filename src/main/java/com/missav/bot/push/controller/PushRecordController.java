package com.missav.bot.push.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.missav.bot.common.Result;
import com.missav.bot.push.entity.PushRecord;
import com.missav.bot.push.mapper.PushRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/push-records")
@RequiredArgsConstructor
public class PushRecordController {

    private final PushRecordMapper pushRecordMapper;

    @GetMapping
    public Result<IPage<PushRecord>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        Page<PushRecord> pageParam = new Page<>(page, size);
        IPage<PushRecord> result = pushRecordMapper.selectPage(pageParam, null);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<PushRecord> getById(@PathVariable Long id) {
        PushRecord record = pushRecordMapper.selectById(id);
        if (record == null) {
            return Result.error(404, "推送记录不存在");
        }
        return Result.success(record);
    }

    @GetMapping("/chat/{chatId}")
    public Result<List<PushRecord>> getByChatId(@PathVariable Long chatId) {
        List<PushRecord> records = pushRecordMapper.selectByChatIdOrderByPushedAtDesc(chatId);
        return Result.success(records);
    }

    @GetMapping("/video/{videoId}")
    public Result<List<PushRecord>> getByVideoId(@PathVariable Long videoId) {
        List<PushRecord> records = pushRecordMapper.selectByVideoId(videoId);
        return Result.success(records);
    }

    @GetMapping("/failed")
    public Result<List<PushRecord>> getFailed() {
        List<PushRecord> records = pushRecordMapper.selectByStatusOrderByPushedAtAsc(
                PushRecord.PushStatus.FAILED.name());
        return Result.success(records);
    }
}
