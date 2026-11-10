package com.yunpei.proxy.infra.temporal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.WorkerFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;

/**
 * 验证优雅停机顺序：WorkerFactory → WorkflowServiceStubs，以及 destroyMethod 清理。
 */
class TemporalWorkerGracefulShutdownTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TemporalWorkerGracefulShutdownConfiguration.class, Fixture.class)
            .withPropertyValues("spring.temporal.connection.target=127.0.0.1:4723");

    @Test
    void stop_shutsDownWorkersThenStubs() {
        WorkerFactory factory = mock(WorkerFactory.class);
        WorkflowServiceStubs stubs = mock(WorkflowServiceStubs.class);
        when(factory.isShutdown()).thenReturn(false);
        when(factory.isTerminated()).thenReturn(true);
        when(stubs.isShutdown()).thenReturn(false);
        when(stubs.isTerminated()).thenReturn(true);
        when(stubs.awaitTermination(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);

        TemporalWorkerGracefulShutdown lifecycle = new TemporalWorkerGracefulShutdown(factory, stubs, 5L);
        lifecycle.start();
        assertThat(lifecycle.isRunning()).isTrue();

        AtomicBoolean callbackRan = new AtomicBoolean(false);
        lifecycle.stop(() -> callbackRan.set(true));

        assertThat(lifecycle.isRunning()).isFalse();
        assertThat(callbackRan).isTrue();

        InOrder order = inOrder(factory, stubs);
        order.verify(factory).shutdown();
        order.verify(factory).awaitTermination(anyLong(), eq(TimeUnit.SECONDS));
        order.verify(stubs).shutdown();
        order.verify(stubs).awaitTermination(anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    void destroy_isIdempotentAfterStop() {
        WorkerFactory factory = mock(WorkerFactory.class);
        when(factory.isShutdown()).thenReturn(false);
        when(factory.isTerminated()).thenReturn(true);

        TemporalWorkerGracefulShutdown lifecycle = new TemporalWorkerGracefulShutdown(factory);
        lifecycle.start();
        lifecycle.stop();
        lifecycle.destroy();

        verify(factory).shutdown();
        verify(factory).awaitTermination(anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    void clearDestroyMethod_disablesInferredShutdown() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        RootBeanDefinition factoryBd = new RootBeanDefinition(Object.class);
        factoryBd.setDestroyMethodName("shutdown");
        ctx.registerBeanDefinition(TemporalWorkerGracefulShutdownConfiguration.WORKER_FACTORY_BEAN, factoryBd);

        RootBeanDefinition stubsBd = new RootBeanDefinition(Object.class);
        stubsBd.setDestroyMethodName("(inferred)");
        ctx.registerBeanDefinition(TemporalWorkerGracefulShutdownConfiguration.SERVICE_STUBS_BEAN, stubsBd);

        BeanFactoryPostProcessor pp =
                TemporalWorkerGracefulShutdownConfiguration.temporalClientDestroyMethodCustomizer();
        pp.postProcessBeanFactory(ctx.getBeanFactory());

        assertThat(ctx.getBeanDefinition(TemporalWorkerGracefulShutdownConfiguration.WORKER_FACTORY_BEAN)
                        .getDestroyMethodName())
                .isEmpty();
        assertThat(ctx.getBeanDefinition(TemporalWorkerGracefulShutdownConfiguration.SERVICE_STUBS_BEAN)
                        .getDestroyMethodName())
                .isEmpty();
        ctx.close();
    }

    @Test
    void configuration_registersLifecycleWhenFactoryPresent() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(TemporalWorkerGracefulShutdown.class);
            assertThat(ctx.getBean(TemporalWorkerGracefulShutdown.class).isRunning())
                    .isTrue();
            // BFPP 应清空 destroyMethod
            assertThat(ctx.getBeanFactory()
                            .getBeanDefinition("temporalWorkerFactory")
                            .getDestroyMethodName())
                    .isEmpty();
            assertThat(ctx.getBeanFactory()
                            .getBeanDefinition("temporalWorkflowServiceStubs")
                            .getDestroyMethodName())
                    .isEmpty();
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class Fixture {
        @Bean(name = "temporalWorkerFactory")
        WorkerFactory workerFactory() {
            WorkerFactory factory = mock(WorkerFactory.class);
            when(factory.isShutdown()).thenReturn(false);
            when(factory.isTerminated()).thenReturn(true);
            return factory;
        }

        @Bean(name = "temporalWorkflowServiceStubs")
        WorkflowServiceStubs stubs() {
            WorkflowServiceStubs stubs = mock(WorkflowServiceStubs.class);
            when(stubs.isShutdown()).thenReturn(false);
            when(stubs.isTerminated()).thenReturn(true);
            when(stubs.awaitTermination(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
            return stubs;
        }
    }
}
