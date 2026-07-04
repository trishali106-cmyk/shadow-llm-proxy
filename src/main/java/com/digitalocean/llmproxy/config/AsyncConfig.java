package com.digitalocean.llmproxy.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

/**
 * Async configuration: virtual-thread executor, shadow concurrency limiter, and uncaught handler.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Bean(name = "shadowTaskExecutor")
    Executor shadowTaskExecutor() {
        return new VirtualThreadTaskExecutor("shadow-vt-");
    }

    @Bean
    ShadowConcurrencyLimiter shadowConcurrencyLimiter(LlmProperties llmProperties) {
        return new ShadowConcurrencyLimiter(llmProperties.shadow().maxConcurrency());
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new LoggingAsyncUncaughtExceptionHandler();
    }

    /**
     * Limits concurrent in-flight shadow comparisons to protect upstream LLM capacity.
     */
    public static final class ShadowConcurrencyLimiter {

        private final Semaphore semaphore;

        public ShadowConcurrencyLimiter(int maxConcurrency) {
            this.semaphore = new Semaphore(Math.max(1, maxConcurrency));
        }

        public boolean tryAcquire() {
            return semaphore.tryAcquire();
        }

        public void release() {
            semaphore.release();
        }
    }

    private static final class LoggingAsyncUncaughtExceptionHandler implements AsyncUncaughtExceptionHandler {

        @Override
        public void handleUncaughtException(Throwable ex, Method method, Object... params) {
            String requestId = params.length > 0 ? String.valueOf(params[0]) : "unknown";
            log.error("Uncaught async error in {} requestId={}: {}", method.getName(), requestId, ex.getMessage(), ex);
        }
    }
}
