package com.yunpei.proxy.service.entity;

import com.easy.query.core.annotation.LogicDelete;
import com.easy.query.core.annotation.UpdateIgnore;
import com.easy.query.core.basic.extension.logicdel.LogicDeleteStrategyEnum;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 核心表公共字段基类（审计 + 软删）。
 *
 * <p>对齐 {@code backend/db/docs/db-conventions.md} 核心 7 字段中的：
 * {@code deleted_at} / {@code created_at} / {@code updated_at} / {@code created_by} / {@code updated_by}。
 * {@code remark} / {@code is_enabled} 因各表语义差异，由具体实体声明。
 *
 * <p>Easy-Query 约定：
 * <ul>
 *     <li>{@code deletedAt} 使用 {@link LogicDeleteStrategyEnum#DELETE_LONG_TIMESTAMP}：
 *         未删=0，删除=当前毫秒时间戳；查询/更新自动过滤，{@code deletable} 改写为 UPDATE</li>
 *     <li>{@code createdAt}/{@code createdBy} 标注 {@link UpdateIgnore}，对象更新永不写入 SET</li>
 *     <li>插入/更新时的时间与操作人填充见 {@code AuditEntityInterceptor}</li>
 * </ul>
 *
 * @author yunpei
 */
@Data
public abstract class BaseEntity {

    /**
     * 软删毫秒时间戳；0=未删；非 0=删除时刻。
     *
     * <p>策略为 DELETE_LONG_TIMESTAMP，与 schema 一致。
     */
    @LogicDelete(strategy = LogicDeleteStrategyEnum.DELETE_LONG_TIMESTAMP)
    private Long deletedAt;

    /** 创建时间；插入后不可被对象更新覆盖。 */
    @UpdateIgnore
    private LocalDateTime createdAt;

    /** 最后更新时间；插入/更新由拦截器填充。 */
    private LocalDateTime updatedAt;

    /** 创建人；0=系统操作；插入后不可被对象更新覆盖。 */
    @UpdateIgnore
    private Long createdBy;

    /** 最后修改人；0=系统操作。 */
    private Long updatedBy;
}
