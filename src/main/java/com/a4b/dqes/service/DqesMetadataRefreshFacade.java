/**
 * Created: Jan 09, 2026 11:45:39 AM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.service;

import org.springframework.stereotype.Service;

import com.a4b.dqes.dto.record.MetaRefreshRequest;
import com.a4b.dqes.dto.record.MetaRefreshResponse;
import com.a4b.dqes.dto.record.MetaRefreshStats;
import com.a4b.dqes.query.service.DbSchemaCacheService;
import com.a4b.dqes.service.metadata.MetadataService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DqesMetadataRefreshFacade {

  private final MetadataService service;
  private final DbSchemaCacheService dbSchemaCacheService;

  public MetaRefreshResponse refresh(MetaRefreshRequest req) throws Exception {
    long t0 = System.currentTimeMillis();

    MetaRefreshStats stats = service.refreshByConnCode(req.connCode());

    // Reload schema cache after refresh
    dbSchemaCacheService.loadDbSchemaCache(req.connCode());

    long elapsed = System.currentTimeMillis() - t0;

    return new MetaRefreshResponse(
        req.connCode(),
        elapsed,
        stats.objectsInserted(),
        stats.fieldsInserted(),
        stats.relationsInserted(),
        stats.joinKeysInserted()
    );
  }
}

