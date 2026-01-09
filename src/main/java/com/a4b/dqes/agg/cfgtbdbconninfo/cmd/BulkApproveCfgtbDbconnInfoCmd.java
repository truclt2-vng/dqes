package com.a4b.dqes.agg.cfgtbdbconninfo.cmd;

import java.io.Serializable;
import java.util.List;

import com.a4b.core.validation.constraints.StepByStep;
import com.a4b.core.validation.constraints.Validator;
import com.a4b.core.validation.constraints.group.GroupOrder;
import com.a4b.core.validation.constraints.group.Level1;
import com.a4b.core.validation.constraints.group.Level9;
import com.a4b.dqes.agg.cfgtbdbconninfo.CfgtbDbconnInfoValidator;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@GroupOrder
@StepByStep(value = { @Validator(value = CfgtbDbconnInfoValidator.class)}, groups = Level9.class)
public class BulkApproveCfgtbDbconnInfoCmd implements Serializable {
    @NotNull(groups = Level1.class)
    private List<Long> ids;
}
