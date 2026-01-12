package com.a4b.dqes.query.planner;

public sealed interface JoinStep permits JoinJoinStep, ExistsStep {
    String debug();
}
