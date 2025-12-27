package com.missav.bot.push.entity;

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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("push_records")
public class PushRecord extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long videoId;
    private Long chatId;
    private PushStatus status;
    private String failReason;
    private LocalDateTime pushedAt;
    private Integer messageId;

    public enum PushStatus {
        SUCCESS,
        FAILED
    }
}
