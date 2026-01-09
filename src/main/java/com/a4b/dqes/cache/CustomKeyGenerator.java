/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.cache;

import java.lang.reflect.Method;

import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.interceptor.KeyGenerator;

public class CustomKeyGenerator implements KeyGenerator {

	public Object generate(Object target, Method method, Object... params) {
		return target.getClass().getSimpleName() + "_" + method.getName() + "_" + StringUtils.join(params, "_");
	}
}
