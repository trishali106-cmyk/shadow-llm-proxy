package com.digitalocean.llmproxy;

import com.digitalocean.llmproxy.config.LlmProperties;
import com.digitalocean.llmproxy.config.MetricsProperties;
import com.digitalocean.llmproxy.config.RateLimitProperties;
import com.digitalocean.llmproxy.config.SecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Spring Boot entry point for the shadow LLM proxy service.
 *
 * <p>Redis auto-configuration is excluded by default and enabled selectively via
 * {@link com.digitalocean.llmproxy.config.RedisMetricsConfiguration} when
 * {@code metrics.store=redis}.
 */
@SpringBootApplication(exclude = {
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class
})
@EnableConfigurationProperties({
        LlmProperties.class,
        SecurityProperties.class,
        RateLimitProperties.class,
        MetricsProperties.class
})
public class LlmProxyApplication {

    public static void main(String[] args) {
        SpringApplication.run(LlmProxyApplication.class, args);
    }
}
