/**
 * Created: Jan 28, 2026 3:04:26 PM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.service;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEvictUtils {

    private final CacheManager cacheManager;

    /* ========== Evict 1 key ========== */
    public void evict(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            log.warn("Cache not found: {}", cacheName);
            return;
        }
        cache.evict(key);
        log.debug("Evicted cache [{}] key [{}]", cacheName, key);
    }

    /* ========== Evict many keys ========== */
    public void evict(String cacheName, Iterable<?> keys) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            log.warn("Cache not found: {}", cacheName);
            return;
        }
        keys.forEach(cache::evict);
        log.debug("Evicted cache [{}] keys {}", cacheName, keys);
    }

    /* ========== Evict all keys in cache ========== */
    public void evictAll(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            log.warn("Cache not found: {}", cacheName);
            return;
        }
        cache.clear();
        log.debug("Cleared all keys in cache [{}]", cacheName);
    }

    /* ========== Evict ALL caches ========== */
    public void evictAllCaches() {
        cacheManager.getCacheNames()
            .forEach(name -> {
                Cache cache = cacheManager.getCache(name);
                if (cache != null) {
                    cache.clear();
                    log.debug("Cleared cache [{}]", name);
                }
            });
    }
}

