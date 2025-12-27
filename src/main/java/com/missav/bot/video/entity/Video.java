package com.missav.bot.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.missav.bot.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 视频实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("videos")
public class Video extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;
    private String title;
    private String actresses;
    private String tags;
    private Integer duration;
    private LocalDateTime releaseDate;
    private String coverUrl;
    private String previewUrl;
    private String detailUrl;
    private Boolean pushed;
}
