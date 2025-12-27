package com.missav.bot.video.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.missav.bot.common.Result;
import com.missav.bot.video.entity.Video;
import com.missav.bot.video.mapper.VideoMapper;
import com.missav.bot.video.service.IVideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 视频管理接口
 */
@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final IVideoService videoService;
    private final VideoMapper videoMapper;

    /**
     * 分页查询视频列表
     */
    @GetMapping
    public Result<IPage<Video>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        Page<Video> pageParam = new Page<>(page, size);
        IPage<Video> result = videoService.page(pageParam);
        return Result.success(result);
    }

    /**
     * 根据ID查询视频
     */
    @GetMapping("/{id}")
    public Result<Video> getById(@PathVariable Long id) {
        Video video = videoService.getById(id);
        if (video == null) {
            return Result.error(404, "视频不存在");
        }
        return Result.success(video);
    }

    /**
     * 根据番号查询视频
     */
    @GetMapping("/code/{code}")
    public Result<Video> getByCode(@PathVariable String code) {
        Video video = videoMapper.selectByCode(code);
        if (video == null) {
            return Result.error(404, "视频不存在");
        }
        return Result.success(video);
    }

    /**
     * 搜索视频（按演员）
     */
    @GetMapping("/search/actress")
    public Result<List<Video>> searchByActress(@RequestParam String actress) {
        List<Video> videos = videoMapper.selectByActress(actress);
        return Result.success(videos);
    }

    /**
     * 搜索视频（按标签）
     */
    @GetMapping("/search/tag")
    public Result<List<Video>> searchByTag(@RequestParam String tag) {
        List<Video> videos = videoMapper.selectByTag(tag);
        return Result.success(videos);
    }

    /**
     * 创建视频
     */
    @PostMapping
    public Result<Video> create(@RequestBody Video video) {
        if (videoMapper.existsByCode(video.getCode())) {
            return Result.error(400, "番号已存在");
        }
        videoService.save(video);
        return Result.success(video);
    }

    /**
     * 更新视频
     */
    @PutMapping("/{id}")
    public Result<Video> update(@PathVariable Long id, @RequestBody Video video) {
        Video existing = videoService.getById(id);
        if (existing == null) {
            return Result.error(404, "视频不存在");
        }
        video.setId(id);
        videoService.updateById(video);
        return Result.success(video);
    }

    /**
     * 删除视频
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Video existing = videoService.getById(id);
        if (existing == null) {
            return Result.error(404, "视频不存在");
        }
        videoService.removeById(id);
        return Result.success();
    }

    /**
     * 查询未推送的视频
     */
    @GetMapping("/unpushed")
    public Result<List<Video>> getUnpushed() {
        List<Video> videos = videoMapper.selectUnpushedVideos();
        return Result.success(videos);
    }

    /**
     * 查询最新视频
     */
    @GetMapping("/latest")
    public Result<List<Video>> getLatest() {
        List<Video> videos = videoMapper.selectTop50ByCreatedTimeDesc();
        return Result.success(videos);
    }
}
