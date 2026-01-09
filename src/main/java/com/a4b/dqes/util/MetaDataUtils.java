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
		try {
			String username = SecurityUtils.getCurrentUserLogin().orElseGet(() -> "system");
			BeanUtils.setProperty(object, "makerDate", OffsetDateTime.now());
			BeanUtils.setProperty(object, "makerId", username);
			BeanUtils.setProperty(object, "updateDate", OffsetDateTime.now());
			BeanUtils.setProperty(object, "updateId", username);
			BeanUtils.setProperty(object, "recordStatus", RecordStatus.O.name());
			BeanUtils.setProperty(object, "authStatus", AuthStatus.A.name());
			if(needApproval) {
				BeanUtils.setProperty(object, "authStatus", AuthStatus.U.name());
				BeanUtils.setProperty(object, "currentFlg", false);
			}else {
				BeanUtils.setProperty(object, "authStatus", AuthStatus.A.name());
				BeanUtils.setProperty(object, "currentFlg", true);
			}
			
			BeanUtils.setProperty(object, "createDate", OffsetDateTime.now());
			BeanUtils.setProperty(object, "aggId", UUID.randomUUID());
			BeanUtils.setProperty(object, "appCode", SecurityUtils.getCurrentAppCode());
			BeanUtils.setProperty(object, "tenantCode", SecurityUtils.getCurrentUserTenantCode());
			BeanUtils.setProperty(object, "empCode", SecurityUtils.getCurrentUserEmpCode());
		} catch (IllegalAccessException e) {
		} catch (InvocationTargetException e) {
		}
	}

	public static void forCreateWithUser(Object object, String username) {

		try {
			BeanUtils.setProperty(object, "makerDate", new Date());
			BeanUtils.setProperty(object, "makerId", username);
			BeanUtils.setProperty(object, "updateDate", new Date());
			BeanUtils.setProperty(object, "updateId", username);
			BeanUtils.setProperty(object, "recordStatus", RecordStatus.O.name());
			BeanUtils.setProperty(object, "authStat", AuthStatus.A.name());
		} catch (IllegalAccessException e) {
		} catch (InvocationTargetException e) {
		}

	}

	public static void forCreateWithoutAuthStat(Object object) {

		try {
			String username = SecurityUtils.getCurrentUserLogin().orElseGet(() -> "system");
			BeanUtils.setProperty(object, "makerDate", new Date());
			BeanUtils.setProperty(object, "makerId", username);
			BeanUtils.setProperty(object, "updateDate", new Date());
			BeanUtils.setProperty(object, "updateId", username);
			BeanUtils.setProperty(object, "recordStatus", RecordStatus.O.name());
		} catch (IllegalAccessException e) {
		} catch (InvocationTargetException e) {
		}

	}

	public static void forUpdate(Object object) {
		try {
			String username = SecurityUtils.getCurrentUserLogin().orElseGet(() -> "system");
			BeanUtils.setProperty(object, "updateDate", new Date());
			BeanUtils.setProperty(object, "updateId", username);
		} catch (IllegalAccessException e) {
		} catch (InvocationTargetException e) {
		}
	}

	public static void forUpdateWithUser(Object object, String username) {
		try {
			BeanUtils.setProperty(object, "updateDate", new Date());
			BeanUtils.setProperty(object, "updateId", username);
		} catch (IllegalAccessException e) {
		} catch (InvocationTargetException e) {
		}
	}

    public static void forCreateWithOwner(Object object) {

        try {
            BeanUtils.setProperty(object, "makerDate", new Date());
            BeanUtils.setProperty(object, "makerId", SecurityUtils.getCurrentUserLogin().orElseGet(() -> "system"));
            BeanUtils.setProperty(object, "recordStatus", RecordStatus.O.name());
            BeanUtils.setProperty(object, "authStat", AuthStatus.A.name());
            BeanUtils.setProperty(object, "updateDate", new Date());
            BeanUtils.setProperty(object, "updateId", SecurityUtils.getCurrentUserLogin().orElseGet(() -> "system"));
            BeanUtils.setProperty(object, "owner", SecurityUtils.getCurrentUserLogin().orElseGet(() -> "system"));

        } catch (IllegalAccessException e) {
        } catch (InvocationTargetException e) {
        }

    }

    public static void forCreateWithParamOwner(Object object, String owner) {

        try {
            BeanUtils.setProperty(object, "makerDate", new Date());
            BeanUtils.setProperty(object, "makerId", SecurityUtils.getCurrentUserLogin().orElseGet(() -> "system"));
            BeanUtils.setProperty(object, "recordStatus", RecordStatus.O.name());
            BeanUtils.setProperty(object, "authStat", AuthStatus.A.name());
            BeanUtils.setProperty(object, "updateDate", new Date());
            BeanUtils.setProperty(object, "updateId", SecurityUtils.getCurrentUserLogin().orElseGet(() -> "system"));
            BeanUtils.setProperty(object, "owner", owner);

        } catch (IllegalAccessException e) {
        } catch (InvocationTargetException e) {
        }

    }
}
