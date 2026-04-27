package com.trigyn.OTPUtil.config;

import com.trigyn.OTPUtil.dto.OtpCacheEntry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration.
 * Registers a typed RedisTemplate<String, OtpCacheEntry> that (de)serializes
 * values using Jackson JSON.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, OtpCacheEntry> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, OtpCacheEntry> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use plain String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Use Jackson JSON serializer for values
        Jackson2JsonRedisSerializer<OtpCacheEntry> valueSerializer =
                new Jackson2JsonRedisSerializer<>(OtpCacheEntry.class);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();
        return template;
    }
}

