/**
 * Created: Jan 28, 2026 3:04:54 PM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.web.rest;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.a4b.dqes.service.CacheEvictUtils;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/cacheevict")
@RequiredArgsConstructor
public class CacheEvictApi {
    private final CacheEvictUtils cacheEvictUtils;

    /**
     * Evict a single key from a cache
     * DELETE /api/cacheevict/{cacheName}/{key}
     */
    @DeleteMapping("/{cacheName}/{key}")
    public ResponseEntity<Void> evictKey(
        @PathVariable String cacheName,
        @PathVariable String key
    ) {
        cacheEvictUtils.evict(cacheName, key);
        return ResponseEntity.ok().build();
    }

    /**
     * Evict multiple keys from a cache
     * DELETE /api/cacheevict/{cacheName}
     * Body: ["key1", "key2", "key3"]
     */
    @DeleteMapping("/{cacheName}")
    public ResponseEntity<Void> evictKeys(
        @PathVariable String cacheName,
        @RequestBody List<String> keys
    ) {
        cacheEvictUtils.evict(cacheName, keys);
        return ResponseEntity.ok().build();
    }

    /**
     * Clear all keys in a specific cache
     * DELETE /api/cacheevict/{cacheName}/all
     */
    @DeleteMapping("/{cacheName}/all")
    public ResponseEntity<Void> evictAllKeysInCache(
        @PathVariable String cacheName
    ) {
        cacheEvictUtils.evictAll(cacheName);
        return ResponseEntity.ok().build();
    }

    /**
     * Clear all caches
     * DELETE /api/cacheevict/all
     */
    @DeleteMapping("/all")
    public ResponseEntity<Void> evictAllCaches() {
        cacheEvictUtils.evictAllCaches();
        return ResponseEntity.ok().build();
    }
    
}
