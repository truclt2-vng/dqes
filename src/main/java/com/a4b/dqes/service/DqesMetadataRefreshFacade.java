/**
 * Created: Jan 09, 2026 11:45:39 AM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.service;

import org.springframework.stereotype.Service;

import com.a4b.dqes.dto.record.MetaRefreshRequest;
import com.a4b.dqes.dto.record.MetaRefreshResponse;
import com.a4b.dqes.dto.record.MetaRefreshStats;

@Service
public class DqesMetadataRefreshFacade {

  private final DqesMetadataRefreshService service;

  public DqesMetadataRefreshFacade(DqesMetadataRefreshService service) {
    this.service = service;
  }

  public MetaRefreshResponse refresh(MetaRefreshRequest req) throws Exception {
    long t0 = System.currentTimeMillis();

    int maxDepth = req.maxDepth() == null ? 6 : req.maxDepth();
    boolean includeViews = req.includeViews() == null ? true : req.includeViews();
    boolean includeTables = req.includeTables() == null ? true : req.includeTables();
    boolean includeMatViews = req.includeMaterializedViews() == null ? true : req.includeMaterializedViews();
    boolean genViewRels = req.generateViewRelations() == null ? false : req.generateViewRelations();

    MetaRefreshStats stats = service.refreshByConnCode(
        req.tenantCode(), req.appCode(), req.connCode()
    );

    long elapsed = System.currentTimeMillis() - t0;

    return new MetaRefreshResponse(
        req.tenantCode(),
        req.appCode(),
        req.connCode(),
        elapsed,
        stats.objectsInserted(),
        stats.fieldsInserted(),
        stats.relationsInserted(),
        stats.joinKeysInserted(),
        true
    );
  }
}

