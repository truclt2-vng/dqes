/**
 * Created: Dec 02, 2025 4:04:17 PM
 * Copyright © 2025 by A4B. All rights reserved
 */
package com.a4b.dqes.domain.generic;

import java.time.OffsetDateTime;

import com.a4b.core.server.enums.AuthStatus;
import com.a4b.core.server.enums.RecordStatus;

import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity extends ControlFieldEntity {
    
    public void markAsDeleted() {
        this.setRecordStatus(RecordStatus.C.name());
    }

    public void approve() {
        this.setAuthStatus(AuthStatus.A.name());
    }

    public void approveScdType2() {
        this.setAuthStatus(AuthStatus.A.name());
        this.setEffectiveStart(OffsetDateTime.now());
        this.setEffectiveEnd(null);
        this.setCurrentFlg(true);
    }

    public void markAsUnauthorized() {
        this.setAuthStatus(AuthStatus.U.name());
        this.setEffectiveStart(OffsetDateTime.now());
        this.setEffectiveEnd(null);
        this.setCurrentFlg(false);
    }

    public void closeCurrentEntity() {
        this.setEffectiveEnd(OffsetDateTime.now());
        this.setCurrentFlg(false);
    }
}
