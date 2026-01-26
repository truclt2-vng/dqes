/**
 * Created: Jan 26, 2026 10:16:33 AM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.query.planner;

import java.util.ArrayList;
import java.util.List;

import com.a4b.dqes.domain.ObjectMeta;

import lombok.Data;

@Data
public class Planner {
    private String objectCode;
    private ObjectMeta objectMeta;
    private List<JoinStep> joinSteps;

    public Planner() {
    }

    public void addStep(JoinStep step) {
        if (joinSteps == null) {
            joinSteps = new ArrayList<>();
        }
        addIfAbsent(step);
    }

    private void addIfAbsent(JoinStep step) {
        boolean exists = joinSteps.stream().anyMatch(s -> s.getRelationInfo().getCode().equals(step.getRelationInfo().getCode()));
        if (!exists) {
            joinSteps.add(step);
        }
    }
}
