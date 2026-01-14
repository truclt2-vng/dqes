/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.util;

import java.lang.reflect.InvocationTargetException;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.UUID;

import org.apache.commons.beanutils.BeanUtils;

import com.a4b.core.server.enums.AuthStatus;
import com.a4b.core.server.enums.RecordStatus;
import com.a4b.dqes.security.SecurityUtils;

public final class MetaDataUtils {
	private MetaDataUtils() {

	}

	public static void forCreate(Object object) {
		forCreate(object, false);
	}

	public static void forCreate(Object object, Boolean needApproval){
		String username = SecurityUtils.getCurrentUserLogin().orElseGet(() -> "system");
		selfSetValue(object, "makerDate", OffsetDateTime.now());
		selfSetValue(object, "makerId", username);
		selfSetValue(object, "updateDate", OffsetDateTime.now());
		selfSetValue(object, "updateId", username);
		selfSetValue(object, "recordStatus", RecordStatus.O.name());
		if(needApproval) {
			selfSetValue(object, "authStatus", AuthStatus.U.name());
		}else {
			selfSetValue(object, "authStatus", AuthStatus.A.name());
		}
		selfSetValue(object, "createDate", OffsetDateTime.now());
		selfSetValue(object, "aggId", UUID.randomUUID());
		selfSetValue(object, "appCode", SecurityUtils.getCurrentAppCode());
		selfSetValue(object, "tenantCode", SecurityUtils.getCurrentUserTenantCode());
		selfSetValue(object, "empCode", SecurityUtils.getCurrentUserEmpCode());
	}
	

	public static void forCreateWithUser(Object object, String username) {

		selfSetValue(object, "makerDate", OffsetDateTime.now());
		selfSetValue(object, "makerId", username);
		selfSetValue(object, "updateDate", OffsetDateTime.now());
		selfSetValue(object, "updateId", username);
		selfSetValue(object, "recordStatus", RecordStatus.O.name());
		selfSetValue(object, "authStat", AuthStatus.A.name());
		selfSetValue(object, "createDate", OffsetDateTime.now());
	}

	public static void forCreateWithoutAuthStat(Object object) {
		String username = SecurityUtils.getCurrentUserLogin().orElseGet(() -> "system");
		selfSetValue(object, "makerDate", OffsetDateTime.now());
		selfSetValue(object, "makerId", username);
		selfSetValue(object, "updateDate", OffsetDateTime.now());
		selfSetValue(object, "updateId", username);
		selfSetValue(object, "recordStatus", RecordStatus.O.name());
	}

	public static void forUpdate(Object object) {
		String username = SecurityUtils.getCurrentUserLogin().orElseGet(() -> "system");
		selfSetValue(object, "updateDate", OffsetDateTime.now());
		selfSetValue(object, "updateId", username);
	}

	public static void forUpdateWithUser(Object object, String username) {
		selfSetValue(object, "updateDate", OffsetDateTime.now());
		selfSetValue(object, "updateId", username);
	}

    public static void forCreateWithOwner(Object object) {
		selfSetValue(object, "makerDate", OffsetDateTime.now());
		selfSetValue(object, "makerId", SecurityUtils.getCurrentUserLogin().orElseGet(() -> "system"));
		selfSetValue(object, "recordStatus", RecordStatus.O.name());
		selfSetValue(object, "authStat", AuthStatus.A.name());
		selfSetValue(object, "updateDate", OffsetDateTime.now());
		selfSetValue(object, "updateId", SecurityUtils.getCurrentUserLogin().orElseGet(() -> "system"));
		selfSetValue(object, "owner", SecurityUtils.getCurrentUserLogin().orElseGet(() -> "system"));
    }

    public static void forCreateWithParamOwner(Object object, String owner) {
		selfSetValue(object, "makerDate", OffsetDateTime.now());
		selfSetValue(object, "makerId", SecurityUtils.getCurrentUserLogin().orElseGet(() -> "system"));
		selfSetValue(object, "recordStatus", RecordStatus.O.name());
		selfSetValue(object, "authStat", AuthStatus.A.name());
		selfSetValue(object, "updateDate", OffsetDateTime.now());
		selfSetValue(object, "updateId", SecurityUtils.getCurrentUserLogin().orElseGet(() -> "system"));
		selfSetValue(object, "owner", owner);
    }

	public static void selfSetValue(Object object, String fieldName, Object value) {
		try {
			BeanUtils.setProperty(object, fieldName, value);
		} catch (IllegalAccessException e) {
		} catch (InvocationTargetException e) {
		}
	}
}
