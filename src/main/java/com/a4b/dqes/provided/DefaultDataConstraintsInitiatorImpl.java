/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.provided;

import com.a4b.core.ezmt.client.dataconstraint.itf.DataConstraintsInitiator;
import com.a4b.dqes.security.SecurityUtils;
import com.dtsc.micservice.secmt.user.CurrentUserInfo;

public class DefaultDataConstraintsInitiatorImpl implements DataConstraintsInitiator {

	public CurrentUserInfo getCurrentUserInfo() {
		return SecurityUtils.getCurrentUserInfo();
	}

}