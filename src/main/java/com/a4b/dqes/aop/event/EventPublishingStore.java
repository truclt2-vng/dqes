/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.aop.event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class EventPublishingStore {

	private static final ThreadLocal<Object> tlEvent = new ThreadLocal<>();
	private static final boolean supportMultiEvent = true;

	public static final void putEvent(Object event) {
		if(supportMultiEvent) {
			multiEvent(event);
		}else {
			tlEvent.set(event);
		}
	}

	public static final Object getEvent() {
		return tlEvent.get();
	}
	
	public static final void clear() {
		tlEvent.remove();
	}

	private static void multiEvent(Object newEvent) {
		Object object = tlEvent.get();
		if(object != null){
			List<Object> list = new ArrayList<>();
			if(object instanceof Collection) {
				list.addAll((Collection<?>) object);
				list.add(newEvent);
			}else {
				list.add(object);
				list.add(newEvent);
			}
			tlEvent.set(list);
		}else{
			tlEvent.set(newEvent);
		}
	}
}

