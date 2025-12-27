package com.missav.bot.video.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.missav.bot.video.entity.Video;
import com.missav.bot.video.mapper.VideoMapper;
import com.missav.bot.video.service.IVideoService;
import org.springframework.stereotype.Service;

/**
 * 视频服务实现
 */
@Service
public class VideoServiceImpl extends ServiceImpl<VideoMapper, Video> implements IVideoService {
}
