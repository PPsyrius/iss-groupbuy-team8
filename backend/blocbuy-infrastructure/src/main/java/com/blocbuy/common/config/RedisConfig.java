package com.blocbuy.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * Redis wiring. Values are serialized as JSON strings without polymorphic type headers — dangerous
 * polymorphic deserialization is forbidden. Redis is a cache/session store, never the source of
 * truth for orders or deal state.
 */
@Configuration
public class RedisConfig {

  @Bean
  @ConditionalOnMissingBean(name = "jsonRedisTemplate")
  public RedisTemplate<String, Object> jsonRedisTemplate(RedisConnectionFactory factory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(factory);
    template.setKeySerializer(RedisSerializer.string());
    template.setHashKeySerializer(RedisSerializer.string());
    template.setValueSerializer(RedisSerializer.json());
    template.setHashValueSerializer(RedisSerializer.json());
    template.afterPropertiesSet();
    return template;
  }
}
