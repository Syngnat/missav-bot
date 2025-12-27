package com.missav.bot.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 基础实体类，包含公共字段
 */
@Data
public abstract class BaseEntity implements Serializable {

    /**
     * 创建人ID
     */
    @TableField(fill = FieldFill.INSERT)
    private String createdId;

    /**
     * 创建人姓名
     */
    @TableField(fill = FieldFill.INSERT)
    private String createdName;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /**
     * 更新人ID
     */
    @TableField(fill = FieldFill.UPDATE)
    private String updatedId;

    /**
     * 更新人姓名
     */
    @TableField(fill = FieldFill.UPDATE)
    private String updatedName;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updatedTime;

    /**
     * 备注
     */
    private String remark;
}
