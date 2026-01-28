/**
 * Created: Jan 27, 2026 3:18:57 PM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.a4b.dqes.domain.CfgtbDbconnInfo;

@Repository
public interface CfgtbDbconnInfoJpaRepository  extends JpaRepository<CfgtbDbconnInfo, Long> {
    CfgtbDbconnInfo findByConnCode(String connCode);
}
