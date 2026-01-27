/**
 * Created: Jan 26, 2026 10:13:09 AM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.query.planner;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.a4b.core.server.json.JSON;
import com.a4b.dqes.domain.ObjectMeta;
import com.a4b.dqes.domain.RelationInfo;
import com.a4b.dqes.query.config.PathRowMapper;
import com.a4b.dqes.query.model.PathRow;
import com.a4b.dqes.query.model.QueryContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class JoinPathPlanner {

    private static final int DEFAULT_MAX_PATH_DEPTH = 6;
    private final NamedParameterJdbcTemplate dqesJdbc;
    

    public Planner plan(PlanRequest planReq, QueryContext context) {

        String rootObjectCode = planReq.getRootObject().getObjectCode();
        String rootAlias = planReq.getRootObject().getAliasHint();

        Planner planner = new Planner();
        planner.setObjectCode(planReq.getRootObject().getObjectCode());
        String rootRuntimeAlias = context.getOrGenerateAlias(rootObjectCode, rootAlias);
        context.getObjectMetaPlan().put(rootObjectCode, planReq.getRootObject());
        planner.setObjectMeta(planReq.getRootObject());
        // Objects
        Set<String> requiredObjects = determineRequiredObjects(planReq);
        if(requiredObjects.isEmpty() || (requiredObjects.size() == 1 && requiredObjects.contains(rootObjectCode))){
            return planner;
        }

        int joinOrder = 1;
        for (String objectCode : requiredObjects) {
            if (objectCode.equals(rootObjectCode)) {
                continue;
            }
            List<PathRow> pathRows = planPathsFromRoot(planReq.getConnId(), rootObjectCode);
            PathRow bestPath = bestPathByTargetObject(objectCode, pathRows);
            if (bestPath == null) {
                log.warn("No path found from root object {} to required object {}", rootObjectCode, objectCode);
                continue;
            }

            if(bestPath.hopCount() == 1){
                RelationInfo relationInfo = context.getAllRelationInfos().stream()
                        .filter(ri -> ri.getCode().equals(bestPath.relCode()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("RelationInfo not found for code: " + bestPath.relCode()));
                ObjectMeta joinObject = context.getAllObjectMetaMap().get(relationInfo.getToObjectCode());
                String runtimeJoinAlias = context.getOrGenerateAlias(relationInfo.getJoinAlias(), relationInfo.getJoinAlias());
                context.getObjectMetaPlan().put(relationInfo.getJoinAlias(), joinObject);
                planner.addStep(JoinStep.builder()
                    .fromObjectCode(rootObjectCode)
                    .fromAlias(rootRuntimeAlias)
                    .toObjectCode(relationInfo.getJoinAlias())
                    .relationInfo(relationInfo)
                    .joinTable(joinObject.getDbTable())
                    .runtimeAlias(runtimeJoinAlias)
                    .relationType(relationInfo.getRelationType())
                    .joinOrder(joinOrder++)
                    .build()
                );
            }else{
                String previousObjectCode = rootObjectCode;
                String previousAlias = rootRuntimeAlias;
                Set<String> requiredRelationCodes = requiredRelationCodes(objectCode, bestPath);
                log.debug("Planned path from {} to {} via relations {}", rootObjectCode, objectCode, requiredRelationCodes);
                for (String relationCode : requiredRelationCodes) {
                    RelationInfo relationInfo = context.getAllRelationInfos().stream()
                            .filter(ri -> ri.getCode().equals(relationCode))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("RelationInfo not found for code: " + relationCode));
                    ObjectMeta joinObject = context.getAllObjectMetaMap().get(relationInfo.getToObjectCode());
                    String runtimeJoinAlias = context.getOrGenerateAlias(relationInfo.getJoinAlias(), relationInfo.getJoinAlias());
                    context.getObjectMetaPlan().put(relationInfo.getJoinAlias(), joinObject);
                    planner.addStep(JoinStep.builder()
                        .fromObjectCode(previousObjectCode)
                        .fromAlias(previousAlias)
                        .toObjectCode(relationInfo.getJoinAlias())
                        .relationInfo(relationInfo)
                        .joinTable(joinObject.getDbTable())
                        .runtimeAlias(runtimeJoinAlias)
                        .joinOrder(joinOrder++)
                        .build()
                    );
                    previousObjectCode = relationInfo.getToObjectCode();
                    previousAlias = relationInfo.getJoinAlias();
                }
            }

            
        }
        return planner;
    }

    private Set<String> requiredRelationCodes(String objectCode, PathRow row) {
        TypeReference<List<String>> listStringTypeRef = new TypeReference<>() {};
        Set<String> requiredRelationCodes = new HashSet<>();
        JsonNode pathRelationCodesJson = row.pathRelationCodesJson();
        List<String> pathRelationCodes = JSON.getObjectMapper().convertValue(pathRelationCodesJson, listStringTypeRef);
        requiredRelationCodes.addAll(pathRelationCodes);
        return requiredRelationCodes;
    }

    private Set<String> determineRequiredObjects(PlanRequest planReq) {
        Set<String> requiredObjects = new HashSet<>();
        requiredObjects.addAll(planReq.getSelectFields().stream()
                .map(FieldKey::objectCode)
                .collect(Collectors.toSet()));
        requiredObjects.addAll(planReq.getFilterFields().stream()
                .map(FieldKey::objectCode)
                .collect(Collectors.toSet()));
        return requiredObjects;
    }

    private PathRow bestPathByTargetObject(String targetObjectCode, List<PathRow> pathRows) {
        return pathRows.stream()
                .filter(pr -> pr.joinAlias().equals(targetObjectCode))
                .min((pr1, pr2) -> {
                    int cmp = Integer.compare(pr1.hopCount(), pr2.hopCount());
                    if (cmp == 0) {
                        return Integer.compare(pr1.totalWeight(), pr2.totalWeight());
                    }
                    return cmp;
                })
                .orElse(null);
    }

    public List<PathRow> planPathsFromRoot(long connId, String fromObjectCode) {
        String sql = """
            SELECT
                rel_code,
                from_object_code,
                to_object_code,
                join_alias,
                hop_count,
                total_weight,
                path_relation_codes
            FROM dqes.fn_best_paths_from_object_v2(:connId, :fromObjectCode)
            order by hop_count asc
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("connId", connId)
                .addValue("fromObjectCode", fromObjectCode)
                ;

        return dqesJdbc.query(sql, params, PathRowMapper.INSTANCE);
    }
}
