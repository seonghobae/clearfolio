package com.clearfolio.viewer.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configures the executor used by asynchronous conversion workers.
 */
@Configuration
public class ConversionExecutorConfig {

    /**
     * Creates the conversion executor backed by a bounded thread pool.
     *
     * @param conversionProperties conversion runtime properties
     * @return configured conversion executor
     */
    @Bean(name = "conversionExecutor")
    public Executor conversionExecutor(ConversionProperties conversionProperties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, conversionProperties.getWorkerThreads()));
        executor.setMaxPoolSize(Math.max(1, conversionProperties.getWorkerThreads()));
        executor.setQueueCapacity(Math.max(1, conversionProperties.getQueueCapacity()));
        executor.setThreadNamePrefix("conversion-worker-");
        executor.initialize();
        return executor;
    }
}
