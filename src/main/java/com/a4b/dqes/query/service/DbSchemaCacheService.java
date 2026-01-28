/**
 * Created: Jan 28, 2026 10:26:59 AM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.query.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.a4b.dqes.constant.CacheNames;
import com.a4b.dqes.domain.QrytbFieldMeta;
import com.a4b.dqes.domain.QrytbObjectMeta;
import com.a4b.dqes.domain.QrytbRelationInfo;
import com.a4b.dqes.domain.QrytbRelationJoinCondition;
import com.a4b.dqes.domain.QrytbRelationJoinKey;
import com.a4b.dqes.dto.schemacache.DbConnInfoDto;
import com.a4b.dqes.dto.schemacache.DbSchemaCacheRc;
import com.a4b.dqes.dto.schemacache.FieldMetaRC;
import com.a4b.dqes.dto.schemacache.ObjectMetaRC;
import com.a4b.dqes.dto.schemacache.RelationInfoRC;
import com.a4b.dqes.dto.schemacache.RelationJoinConditionRC;
import com.a4b.dqes.dto.schemacache.RelationJoinKeyRC;
import com.a4b.dqes.repository.jpa.FieldMetaJpaRepository;
import com.a4b.dqes.repository.jpa.ObjectMetaJpaRepository;
import com.a4b.dqes.repository.jpa.QrytbRelationJoinConditionJpaRepository;
import com.a4b.dqes.repository.jpa.RelationInfoRepository;
import com.a4b.dqes.repository.jpa.RelationJoinKeyRepository;
import com.a4b.dqes.service.CfgtbDbconnInfoService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DbSchemaCacheService {

    private final ObjectMetaJpaRepository objectMetaRepository;
    private final FieldMetaJpaRepository fieldMetaRepository;
    private final RelationInfoRepository relationInfoRepository;
    private final RelationJoinKeyRepository relationJoinKeyRepository;
    private final QrytbRelationJoinConditionJpaRepository relationJoinConditionRepository;
    private final CfgtbDbconnInfoService dbconnInfoService;

    // @CacheableNonNull(value = CacheNames.DB_SCHEMA_CACHE)
    @Cacheable(value = CacheNames.DB_SCHEMA_CACHE, key = "#dbConnCode", unless = "#result == null")
    public DbSchemaCacheRc loadDbSchemaCache(String dbConnCode) {
        DbConnInfoDto dbconnInfo = dbconnInfoService.getDbConnInfoByCode(dbConnCode);
        Integer dbconnId = dbconnInfo.getId().intValue();
        List<QrytbObjectMeta> objectMetas = objectMetaRepository.findByDbconnId(dbconnId);
        List<QrytbFieldMeta> allFieldMetas = fieldMetaRepository.findByDbconnId(dbconnId);
        List<QrytbRelationInfo> allRelationInfos = relationInfoRepository.findByDbconnId(dbconnId);
        List<QrytbRelationJoinKey> allRelationJoinKeys = relationJoinKeyRepository.findByDbconnIdOrderBySeq(dbconnId);
        List<QrytbRelationJoinCondition> allRelationJoinConditions = relationJoinConditionRepository.findByDbconnIdOrderBySeq(dbconnId);
        
        List<FieldMetaRC> fieldMetaRCs = allFieldMetas.stream().map(fm -> new FieldMetaRC(
            fm.getId(),
            fm.getObjectCode(),
            fm.getFieldCode(),
            fm.getFieldLabel(),
            fm.getAliasHint(),
            fm.getMappingType(),
            fm.getColumnName(),
            fm.getDataType(),
            fm.getNotNull(),
            fm.getDefaultSelect(),
            fm.getAllowSelect(),
            fm.getAllowFilter(),
            fm.getAllowSort(),
            fm.getDescription(),
            fm.getDbconnId(),
            fm.getIsPrimary()
        )).collect(Collectors.toList());

        Map<String, List<FieldMetaRC>> fieldMetaByObjectMap = fieldMetaRCs.stream()
            .collect(Collectors.groupingBy(FieldMetaRC::getObjectCode));

        List<ObjectMetaRC> objectMetaRCs = objectMetas.stream().map(om -> new ObjectMetaRC(
            om.getId(),
            om.getObjectCode(),
            om.getObjectName(),
            om.getDbTable(),
            om.getAliasHint(),
            om.getDbconnId(),
            fieldMetaByObjectMap.getOrDefault(om.getObjectCode(), new ArrayList<>())
        )).collect(Collectors.toList());


        List<RelationJoinKeyRC> relationJoinKeyRCs = allRelationJoinKeys.stream().map(rjk -> new RelationJoinKeyRC(
            rjk.getId(),
            rjk.getRelationId(),
            rjk.getSeq(),
            rjk.getFromColumnName(),
            rjk.getOperator(),
            rjk.getToColumnName(),
            rjk.getNullSafe(),
            rjk.getDbconnId()
        )).collect(Collectors.toList());

        List<RelationJoinConditionRC> relationJoinConditionRCs = allRelationJoinConditions.stream().map(rjc -> new RelationJoinConditionRC(
            rjc.getId(),
            rjc.getRelationCode(),
            rjc.getSeq(),
            rjc.getColumnName(),
            rjc.getOperator(),
            rjc.getValueType(),
            rjc.getValueLiteral(),
            rjc.getParamName(),
            rjc.getDbconnId()
        )).collect(Collectors.toList());

        List<RelationInfoRC> relationInfoRCs = allRelationInfos.stream().map(ri -> {
            List<RelationJoinKeyRC> joinKeys = relationJoinKeyRCs.stream()
                .filter(rjk -> rjk.getRelationId().equals(ri.getId()))
                .collect(Collectors.toList());
            List<RelationJoinConditionRC> joinConditions = relationJoinConditionRCs.stream()
                .filter(rjc -> rjc.getRelationCode().equals(ri.getCode()))
                .collect(Collectors.toList());
            return new RelationInfoRC(
                ri.getId(),
                ri.getCode(),
                ri.getFromObjectCode(),
                ri.getToObjectCode(),
                ri.getRelationType(),
                ri.getJoinType(),
                ri.getJoinAlias(),
                ri.getDbconnId(),
                joinKeys,
                joinConditions
            );
        }).collect(Collectors.toList());
        DbSchemaCacheRc dbSchemaCacheRc = new DbSchemaCacheRc(objectMetaRCs, relationInfoRCs);
        return dbSchemaCacheRc;
    }
    
}
