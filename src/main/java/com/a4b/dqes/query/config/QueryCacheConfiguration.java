package com.a4b.dqes.query.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis cache configuration for Dynamic Query Engine
 * Caches metadata and query results for improved performance
 */
@Configuration
@EnableCaching
public class QueryCacheConfiguration {
    
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.activateDefaultTyping(
            BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build(),
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );
        
        GenericJackson2JsonRedisSerializer serializer = 
            new GenericJackson2JsonRedisSerializer(objectMapper);
        
        // Default cache configuration
        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(serializer)
            )
            .disableCachingNullValues();
        
        // Custom cache configurations
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // Metadata caches (longer TTL - metadata changes infrequently)
        cacheConfigurations.put("objectMetaByCode", 
            defaultCacheConfig.entryTtl(Duration.ofHours(24)));
        cacheConfigurations.put("objectMetaByAliasHint", 
            defaultCacheConfig.entryTtl(Duration.ofHours(24)));
        cacheConfigurations.put("objectMetaByDbconn", 
            defaultCacheConfig.entryTtl(Duration.ofHours(24)));
        cacheConfigurations.put("fieldMetaByObject", 
            defaultCacheConfig.entryTtl(Duration.ofHours(24)));
        cacheConfigurations.put("fieldMetaByCode", 
            defaultCacheConfig.entryTtl(Duration.ofHours(24)));
        cacheConfigurations.put("relationInfoByCode", 
            defaultCacheConfig.entryTtl(Duration.ofHours(24)));
        cacheConfigurations.put("relationInfoByFromObject", 
            defaultCacheConfig.entryTtl(Duration.ofHours(24)));
        cacheConfigurations.put("relationInfoByToObject", 
            defaultCacheConfig.entryTtl(Duration.ofHours(24)));
        cacheConfigurations.put("relationInfoByPair", 
            defaultCacheConfig.entryTtl(Duration.ofHours(24)));
        cacheConfigurations.put("relationInfoNavigable", 
            defaultCacheConfig.entryTtl(Duration.ofHours(24)));
        cacheConfigurations.put("relationJoinKeysByRelation", 
            defaultCacheConfig.entryTtl(Duration.ofHours(24)));
        cacheConfigurations.put("operationMetaByCode", 
            defaultCacheConfig.entryTtl(Duration.ofHours(24)));
        cacheConfigurations.put("operationMetaAll", 
            defaultCacheConfig.entryTtl(Duration.ofHours(24)));
        
        // Query result cache (shorter TTL - data changes more frequently)
        cacheConfigurations.put("queryResults", 
            defaultCacheConfig.entryTtl(Duration.ofMinutes(15)));
        
        // Query path cache (medium TTL - used for graph traversal optimization)
        cacheConfigurations.put("queryPaths", 
            defaultCacheConfig.entryTtl(Duration.ofHours(6)));
        
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultCacheConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build();
    }
}
