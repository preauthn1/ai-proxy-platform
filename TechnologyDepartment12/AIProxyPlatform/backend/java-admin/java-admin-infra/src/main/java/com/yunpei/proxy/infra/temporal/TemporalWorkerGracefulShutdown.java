package com.yunpei.proxy.infra.temporal;

import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.WorkerFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.lang.Nullable;

/**
 * Temporal Worker / gRPC Channel 有序优雅停机。
 *
 * <h3>问题</h3>
 * {@code temporal-spring-boot-starter} 对 {@link WorkerFactory} 的 destroyMethod 仅为
 * {@code shutdown()}（异步、不等待），随后 {@link WorkflowServiceStubs#shutdown()} 立刻关闭
 * gRPC Channel。此时 poller 的 {@code pollExecutor} 尚未 isShutdown，
 * {@code BasePoller} 会把 {@code UNAVAILABLE: Channel shutdown invoked} 打成 WARN
 * （仅当 pollExecutor 已 shutdown 时才会把同类异常降为 TRACE）。
 *
 * <h3>策略</h3>
 * <ol>
 *   <li>由 {@link TemporalWorkerGracefulShutdownConfiguration} 清空 factory/stubs 的
 *       自动 destroyMethod，避免 Spring 乱序关闭 Channel；</li>
 *   <li>本类在 SmartLifecycle.stop / DisposableBean.destroy 中严格：
 *       {@code factory.shutdown + await} → {@code stubs.shutdown + await}。</li>
 * </ol>
 *
 * @author yunpei
 */
public class TemporalWorkerGracefulShutdown implements SmartLifecycle, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(TemporalWorkerGracefulShutdown.class);

    /** 等待终止的默认秒数（应 ≤ spring.lifecycle.timeout-per-shutdown-phase）。 */
    public static final long DEFAULT_AWAIT_SECONDS = 30L;

    @Nullable
    private final WorkerFactory workerFactory;

    @Nullable
    private final WorkflowServiceStubs serviceStubs;

    private final long awaitSeconds;

    /** SmartLifecycle 运行标记。 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** 保证 stop/destroy 只执行一次。 */
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    public TemporalWorkerGracefulShutdown(WorkerFactory workerFactory) {
        this(workerFactory, null, DEFAULT_AWAIT_SECONDS);
    }

    public TemporalWorkerGracefulShutdown(
            @Nullable WorkerFactory workerFactory, @Nullable WorkflowServiceStubs serviceStubs, long awaitSeconds) {
        this.workerFactory = workerFactory;
        this.serviceStubs = serviceStubs;
        this.awaitSeconds = awaitSeconds > 0 ? awaitSeconds : DEFAULT_AWAIT_SECONDS;
    }

    /** 无 Worker 时的空实现（Temporal 启用但未创建 WorkerFactory）。 */
    public static TemporalWorkerGracefulShutdown noop() {
        return new TemporalWorkerGracefulShutdown(null, null, DEFAULT_AWAIT_SECONDS);
    }

    @Override
    public void start() {
        // WorkerFactory 由 starter 的 WorkerFactoryStarter 在 ApplicationReady 时 start。
        // 这里只标记生命周期，确保 stop 会被调度；真正停机不依赖 start 是否成功。
        running.set(true);
        log.atInfo()
                .addKeyValue("awaitSeconds", awaitSeconds)
                .addKeyValue("hasFactory", workerFactory != null)
                .addKeyValue("hasStubs", serviceStubs != null)
                .log("TemporalWorkerGracefulShutdown registered");
    }

    @Override
    public void stop() {
        doStop("SmartLifecycle.stop");
    }

    @Override
    public void stop(Runnable callback) {
        try {
            doStop("SmartLifecycle.stop(callback)");
        } finally {
            callback.run();
        }
    }

    @Override
    public void destroy() {
        // 兜底：若 lifecycle 阶段未跑到，destroy 阶段仍按序关闭
        doStop("DisposableBean.destroy");
    }

    private void doStop(String trigger) {
        running.set(false);
        if (!stopped.compareAndSet(false, true)) {
            log.atDebug().addKeyValue("trigger", trigger).log("Temporal graceful shutdown already done, skip");
            return;
        }
        log.atInfo().addKeyValue("trigger", trigger).log("Temporal graceful shutdown begin");
        try {
            shutdownWorkerFactory();
        } catch (RuntimeException ex) {
            log.atWarn()
                    .addKeyValue("msg", ex.getMessage())
                    .setCause(ex)
                    .log("Error shutting down Temporal WorkerFactory");
        }
        try {
            shutdownServiceStubs();
        } catch (RuntimeException ex) {
            log.atWarn()
                    .addKeyValue("msg", ex.getMessage())
                    .setCause(ex)
                    .log("Error shutting down Temporal WorkflowServiceStubs");
        }
        log.atInfo().addKeyValue("trigger", trigger).log("Temporal graceful shutdown end");
    }

    private void shutdownWorkerFactory() {
        if (workerFactory == null) {
            return;
        }
        if (!workerFactory.isShutdown()) {
            log.info("Shutting down Temporal WorkerFactory (stop pollers before gRPC channel) ...");
            workerFactory.shutdown();
        } else {
            log.debug("Temporal WorkerFactory already shut down, awaiting termination");
        }
        awaitWorkerFactory();
        if (!workerFactory.isTerminated()) {
            log.atWarn()
                    .addKeyValue("awaitSeconds", awaitSeconds)
                    .log("Temporal WorkerFactory not terminated, calling shutdownNow");
            workerFactory.shutdownNow();
            awaitWorkerFactory();
        }
        if (workerFactory.isTerminated()) {
            log.info("Temporal WorkerFactory terminated");
        } else {
            log.warn("Temporal WorkerFactory still not terminated after shutdownNow");
        }
    }

    private void awaitWorkerFactory() {
        if (workerFactory == null) {
            return;
        }
        workerFactory.awaitTermination(awaitSeconds, TimeUnit.SECONDS);
    }

    private void shutdownServiceStubs() {
        if (serviceStubs == null) {
            return;
        }
        if (serviceStubs.isShutdown()) {
            log.debug("Temporal WorkflowServiceStubs already shut down");
            return;
        }
        log.info("Shutting down Temporal WorkflowServiceStubs (gRPC channel) ...");
        serviceStubs.shutdown();
        awaitServiceStubs();
        if (!serviceStubs.isTerminated()) {
            log.atWarn()
                    .addKeyValue("awaitSeconds", awaitSeconds)
                    .log("Temporal WorkflowServiceStubs not terminated, calling shutdownNow");
            serviceStubs.shutdownNow();
            awaitServiceStubs();
        }
        if (serviceStubs.isTerminated()) {
            log.info("Temporal WorkflowServiceStubs terminated");
        } else {
            log.warn("Temporal WorkflowServiceStubs still not terminated after shutdownNow");
        }
    }

    private void awaitServiceStubs() {
        if (serviceStubs == null) {
            return;
        }
        // WorkflowServiceStubs.awaitTermination 返回 boolean，不抛 InterruptedException 到受检
        boolean ok = serviceStubs.awaitTermination(awaitSeconds, TimeUnit.SECONDS);
        if (!ok) {
            log.atDebug()
                    .addKeyValue("awaitSeconds", awaitSeconds)
                    .log("WorkflowServiceStubs.awaitTermination timed out");
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 最高 phase：最后启动、最先停止，尽量在其他组件销毁前完成 Worker 停机。
     */
    @Override
    public int getPhase() {
        return DEFAULT_PHASE;
    }
}
