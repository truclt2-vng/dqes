/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.cache;


import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.annotation.AliasFor;

import com.a4b.dqes.config.CacheConfiguration;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@Cacheable(unless = "#result == null || #result.isEmpty()", keyGenerator = CacheConfiguration.KEY_GENERATOR_MCR_CUSTOM)
public @interface CacheableList {

    /**
     * Alias for {@link #cacheNames}.
     */
    @AliasFor(annotation = Cacheable.class)
    String[] value();


}
