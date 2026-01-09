package com.a4b.dqes.repository;

import org.springframework.stereotype.Repository;

import com.a4b.dqes.domain.CfgtbDbconnInfo;
import com.a4b.dqes.domain.QCfgtbDbconnInfo;
import com.querydsl.core.types.dsl.StringPath;

import lombok.RequiredArgsConstructor;
import com.a4b.dqes.base.dao.ComGenericDAOImpl;

@Repository
@RequiredArgsConstructor
public class CfgtbDbconnInfoRepositoryImpl extends ComGenericDAOImpl<CfgtbDbconnInfo, QCfgtbDbconnInfo, Long> implements CfgtbDbconnInfoRepository {

    private final QCfgtbDbconnInfo qCfgtbDbconnInfo = QCfgtbDbconnInfo.cfgtbDbconnInfo;

    protected StringPath codePath(){
        return qCfgtbDbconnInfo.connCode;
    }

    @Override
    protected QCfgtbDbconnInfo q() {
        return qCfgtbDbconnInfo;
    }
}
