/**
 * Created: Nov 28, 2025 1:19:02 PM
 * Copyright © 2025 by A4B. All rights reserved
 */
package com.a4b.dqes.base;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.a4b.core.ag.ag_grid.response.AgGridGetRowsResponse;
import com.a4b.core.rest.data.ResponseData;
import com.a4b.lib.shared.dto.request.AgGridGetRowsRequestExt;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Generic base API for aggregates which expose:
 * - create
 * - update
 * - pivotPaging / pivotPagingEffective / pivotCount
 * - getByAggId
 * - bulkApprove / bulkDelete
 * - export
 *
 * Each concrete entity controller:
 *  - Extends this class with concrete generic types
 *  - Declares @RestController + @RequestMapping on the subclass
 *  - Injects its concrete service and returns it via getService()
 */
public abstract class BaseAggApi<
    DTO,
    CREATE_CMD,
    UPDATE_CMD,
    BULK_APPROVE_CMD,
    BULK_DELETE_CMD
> {

    protected abstract BaseAggService<
        DTO,
        CREATE_CMD,
        UPDATE_CMD,
        BULK_APPROVE_CMD,
        BULK_DELETE_CMD
    > getService();

    // --- CREATE ---
    @Operation(
        summary = "Create",
        description = "Create a new record.",
        extensions = { @Extension(name = "x-sort", properties = @ExtensionProperty(name = "order", value = "1")) }
    )
    @PostMapping("/create")
    public ResponseData<BaseRespDto> create(@RequestBody CREATE_CMD cmd) {
        var resp = getService().create(cmd);
        return ResponseData.getSuccessData(resp);
    }

    // --- PIVOT PAGING ---
    @Operation(
        summary = "Get paginated",
        description = "Retrieve paginated records with filter/sort.",
        extensions = { @Extension(name = "x-sort", properties = @ExtensionProperty(name = "order", value = "2")) }
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = AgGridGetRowsRequestExt.class),
            examples = @ExampleObject(
                value = "{\"startRow\":0,\"endRow\":100,\"filterModel\":{},\"sortModel\":[]}"
            )
        )
    )
    @PostMapping("/pivotPaging")
    public ResponseData<AgGridGetRowsResponse<DTO>> pivotPaging(
        @RequestBody AgGridGetRowsRequestExt request) {

        var rs = getService().pivotPaging(request);
        return ResponseData.getSuccessData(rs);
    }

    // --- PIVOT COUNT ---

    @Operation(
        summary = "Count entries",
        description = "Count records matching filters.",
        extensions = { @Extension(name = "x-sort", properties = @ExtensionProperty(name = "order", value = "3")) }
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = AgGridGetRowsRequestExt.class),
            examples = @ExampleObject(value = "{\"filterModel\":{}}")
        )
    )
    @PostMapping("/pivotCount")
    public ResponseData<Long> pivotCount(@RequestBody AgGridGetRowsRequestExt request) {
        long rs = getService().pivotCount(request);
        return ResponseData.getSuccessData(rs);
    }
    // --- GET DETAIL BY AGG ID ---

    @Operation(
        summary = "Get detail",
        description = "Fetch record by aggId with detail level.",
        extensions = { @Extension(name = "x-sort", properties = @ExtensionProperty(name = "order", value = "5")) }
    )
    @GetMapping("/getByAggId")
    public ResponseData<DTO> getByAggId(@RequestParam UUID aggId) {
        var resp = getService().getDetailByAggId(aggId);
        return ResponseData.getSuccessData(resp);
    }
    // --- UPDATE ---

    @Operation(
        summary = "Update",
        description = "Update an existing record.",
        extensions = { @Extension(name = "x-sort", properties = @ExtensionProperty(name = "order", value = "6")) }
    )
    @PostMapping("/update")
    public ResponseData<BaseRespDto> update(@RequestBody UPDATE_CMD cmd) {
        var resp = getService().update(cmd);
        return ResponseData.getSuccessData(resp);
    }

    // --- BULK APPROVE ---

    @Operation(
        summary = "Bulk approve",
        description = "Approve multiple records at once.",
        extensions = { @Extension(name = "x-sort", properties = @ExtensionProperty(name = "order", value = "7")) }
    )
    @PostMapping("/bulkApprove")
    public ResponseData<Void> bulkApprove(@RequestBody BULK_APPROVE_CMD cmd) {
        getService().bulkApprove(cmd);
        return ResponseData.getSuccessData(null);
    }

    // --- BULK DELETE ---

    @Operation(
        summary = "Bulk delete",
        description = "Permanently remove multiple records.",
        extensions = { @Extension(name = "x-sort", properties = @ExtensionProperty(name = "order", value = "8")) }
    )
    @PostMapping("/bulkDelete")
    public ResponseData<Void> bulkDelete(@RequestBody BULK_DELETE_CMD cmd) {
        getService().bulkDelete(cmd);
        return ResponseData.getSuccessData(null);
    }
}
