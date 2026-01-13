package com.a4b.dqes.query.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Hybrid cache configuration using Caffeine for L1 (local) cache
 * Provides faster access than Redis for frequently accessed metadata
 * Suitable for single-instance deployments or when Redis is not available
 */
@Configuration
@EnableCaching
@ConditionalOnProperty(name = "app.cache.type", havingValue = "hybrid", matchIfMissing = false)
public class HybridCacheConfiguration {
    
    /**
     * Local Caffeine cache manager for frequently accessed metadata
     * Provides sub-millisecond access times with automatic eviction
     */
    @Bean
    @Primary
    public CacheManager localCacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        
        cacheManager.setCaches(Arrays.asList(
            // Metadata caches - larger size, longer TTL
            buildCache("objectMetaByCode", 5000, Duration.ofHours(24)),
            buildCache("objectMetaByDbconn", 1000, Duration.ofHours(24)),
            buildCache("fieldMetaByObject", 5000, Duration.ofHours(24)),
            buildCache("fieldMetaByCode", 10000, Duration.ofHours(24)),
            buildCache("relationInfoByCode", 5000, Duration.ofHours(24)),
            buildCache("relationInfoByFromObject", 2000, Duration.ofHours(24)),
            buildCache("relationInfoByToObject", 2000, Duration.ofHours(24)),
            buildCache("relationInfoByPair", 3000, Duration.ofHours(24)),
            buildCache("relationInfoNavigable", 1000, Duration.ofHours(24)),
            buildCache("relationJoinKeysByRelation", 5000, Duration.ofHours(24)),
            buildCache("operationMetaByCode", 500, Duration.ofHours(24)),
            buildCache("operationMetaAll", 100, Duration.ofHours(24)),
            
            // Query result cache - smaller size, shorter TTL
            buildCache("queryResults", 1000, Duration.ofMinutes(15)),
            
            // Query path cache - medium size, medium TTL
            buildCache("queryPaths", 2000, Duration.ofHours(6))
        ));
        
        return cacheManager;
    }
    
    /**
     * Build a Caffeine cache with specified parameters
     */
    private CaffeineCache buildCache(String name, int maxSize, Duration ttl) {
        return new CaffeineCache(name,
            Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttl.toMillis(), TimeUnit.MILLISECONDS)
                .recordStats() // Enable statistics for monitoring
                .build()
        );
    }
}
