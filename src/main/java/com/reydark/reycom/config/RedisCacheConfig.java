package com.reydark.reycom.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.Map;

@Configuration
@Profile("!test")
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
public class RedisCacheConfig {

    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory redisConnectionFactory,
            ObjectMapper objectMapper,
            @Value("${reycom-cache.ttl.products:10m}") Duration productsTtl,
            @Value("${reycom-cache.ttl.product-details:10m}") Duration productDetailsTtl,
            @Value("${reycom-cache.ttl.categories:30m}") Duration categoriesTtl,
            @Value("${reycom-cache.ttl.category-details:30m}") Duration categoryDetailsTtl
    ) {
        GenericJackson2JsonRedisSerializer serializer = GenericJackson2JsonRedisSerializer.builder()
                .objectMapper(objectMapper.copy().findAndRegisterModules())
                .defaultTyping(true)
                .typeHintPropertyName("@class")
                .build();

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .entryTtl(Duration.ofMinutes(10));

        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
                "products", defaultConfig.entryTtl(productsTtl),
                "productDetails", defaultConfig.entryTtl(productDetailsTtl),
                "categories", defaultConfig.entryTtl(categoriesTtl),
                "categoryDetails", defaultConfig.entryTtl(categoryDetailsTtl)
        );

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}
