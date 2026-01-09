/**
 * Created: Jan 09, 2026 11:41:49 AM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.web.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.a4b.dqes.dto.record.MetaRefreshRequest;
import com.a4b.dqes.dto.record.MetaRefreshResponse;
import com.a4b.dqes.service.DqesMetadataRefreshFacade;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.tags.Tags;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/metadata")
@RequiredArgsConstructor
@Tags({
        @Tag(name = "DqesMetadata", description = "API for managing DqesMetadata entities. Provides endpoints for CRUD operations and data retrieval.")
})
public class DqesMetadataApi {

    private final DqesMetadataRefreshFacade facade;

    @PostMapping("/refresh")
    public ResponseEntity<MetaRefreshResponse> refresh(@Valid @RequestBody MetaRefreshRequest req) throws Exception {
        MetaRefreshResponse resp = facade.refresh(req);
        return ResponseEntity.ok(resp);
    }
}
