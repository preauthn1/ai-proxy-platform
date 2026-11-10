package com.yunpei.proxy.infra.temporal.workflow;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import java.util.Map;

/**
 * 测试用 Activity：将进程内 count +1 并 log。
 *
 * <p>入参/出参均为 Map，便于联调执行记录的 input_summary / result_summary。
 *
 * @author yunpei
 */
@ActivityInterface
public interface LogCountTickActivities {

    /**
     * 计数 +1 并输出日志。
     *
     * @param input 业务入参（可空；会原样回显到返回 Map 的 {@code input} 字段）
     * @return 摘要 Map：{@code count}、{@code message}，以及回显的 {@code input}
     */
    @ActivityMethod
    Map<String, Object> incrementAndLog(Map<String, Object> input);
}
