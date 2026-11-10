package com.yunpei.proxy.infra.temporal.workflow;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * 执行记录镜像 Activity：list/describe Temporal 并 upsert DB。
 *
 * @author yunpei
 */
@ActivityInterface
public interface ExecutionMirrorTickActivities {

    /**
     * 跑一轮双轨镜像。
     *
     * @return 本轮 touch（insert/update）的行数
     */
    @ActivityMethod
    int mirrorOnce();
}
