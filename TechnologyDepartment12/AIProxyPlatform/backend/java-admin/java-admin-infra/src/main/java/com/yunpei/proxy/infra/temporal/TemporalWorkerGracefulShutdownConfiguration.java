package com.yunpei.proxy.infra.temporal;

import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.WorkerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Temporal 优雅停机装配（组件扫描路径 {@code com.yunpei.proxy.infra}，不依赖 AutoConfiguration 发现）。
 *
 * <p>核心动作：清空 starter 注册的 {@code temporalWorkerFactory} /
 * {@code temporalWorkflowServiceStubs} 自动 destroyMethod，改由
 * {@link TemporalWorkerGracefulShutdown} 按「Worker → Channel」顺序关闭。
 *
 * @author yunpei
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression(
        "${spring.temporal.test-server.enabled:false} || '${spring.temporal.connection.target:}'.length() > 0")
public class TemporalWorkerGracefulShutdownConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TemporalWorkerGracefulShutdownConfiguration.class);

    static final String WORKER_FACTORY_BEAN = "temporalWorkerFactory";
    static final String SERVICE_STUBS_BEAN = "temporalWorkflowServiceStubs";

    /**
     * 去掉 factory/stubs 的自动 destroy，避免 Spring 在 Worker 未 await 完时关闭 Channel。
     *
     * <p>必须为 static：BFPP 要在其他 @Bean 实例化前注册。
     */
    @Bean
    public static BeanFactoryPostProcessor temporalClientDestroyMethodCustomizer() {
        return (ConfigurableListableBeanFactory beanFactory) -> {
            clearDestroyMethod(beanFactory, WORKER_FACTORY_BEAN);
            clearDestroyMethod(beanFactory, SERVICE_STUBS_BEAN);
        };
    }

    static void clearDestroyMethod(ConfigurableListableBeanFactory beanFactory, String beanName) {
        if (!beanFactory.containsBeanDefinition(beanName)) {
            log.atDebug().addKeyValue("bean", beanName).log("Skip clear destroyMethod: bean not defined");
            return;
        }
        BeanDefinition definition = beanFactory.getBeanDefinition(beanName);
        String previous = definition.getDestroyMethodName();
        // 空字符串：禁止推断 close/shutdown，也不调用显式 destroyMethod
        if (definition instanceof AbstractBeanDefinition abstractBeanDefinition) {
            abstractBeanDefinition.setDestroyMethodName("");
        } else {
            definition.setDestroyMethodName("");
        }
        log.atInfo()
                .addKeyValue("bean", beanName)
                .addKeyValue("previousDestroyMethod", previous)
                .log("Cleared destroyMethod on Temporal bean");
    }

    /**
     * 使用可选注入：无 WorkerFactory 时注册 noop，避免 user-config 阶段
     * {@code @ConditionalOnBean} 看不到 auto-config 的假阴性。
     */
    @Bean
    @ConditionalOnMissingBean(TemporalWorkerGracefulShutdown.class)
    public TemporalWorkerGracefulShutdown temporalWorkerGracefulShutdown(
            @Autowired(required = false) WorkerFactory workerFactory,
            @Autowired(required = false) WorkflowServiceStubs workflowServiceStubs,
            @Value("${spring.temporal.worker.shutdown-await-seconds:30}") long awaitSeconds) {
        if (workerFactory == null) {
            log.info("WorkerFactory not available; TemporalWorkerGracefulShutdown is noop " + "(no workers to stop)");
            return TemporalWorkerGracefulShutdown.noop();
        }
        return new TemporalWorkerGracefulShutdown(workerFactory, workflowServiceStubs, awaitSeconds);
    }
}
