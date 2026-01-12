package com.a4b.dqes.query.meta;

import java.util.Objects;

public final class FieldMeta {
    private final String objectCode;
    private final String fieldCode;
    private final String aliasHint;
    private final FieldMappingType mappingType;

    private final String columnName;
    private final String dataTypeCode;

    private final boolean allowSelect;
    private final boolean allowFilter;
    private final boolean allowSort;

    public FieldMeta(
            String objectCode,
            String fieldCode,
            String aliasHint,
            FieldMappingType mappingType,
            String columnName,
            String dataTypeCode,
            boolean allowSelect,
            boolean allowFilter,
            boolean allowSort
    ) {
        this.objectCode = Objects.requireNonNull(objectCode, "objectCode");
        this.fieldCode = Objects.requireNonNull(fieldCode, "fieldCode");
        this.aliasHint = Objects.requireNonNull(fieldCode, "aliasHint");
        this.mappingType = Objects.requireNonNull(mappingType, "mappingType");
        this.columnName = columnName;
        this.dataTypeCode = Objects.requireNonNull(dataTypeCode, "dataTypeCode");
        this.allowSelect = allowSelect;
        this.allowFilter = allowFilter;
        this.allowSort = allowSort;

        if (mappingType == FieldMappingType.COLUMN && (columnName == null || columnName.isBlank())) {
            throw new IllegalArgumentException("COLUMN mapping requires columnName");
        }
    }

    public String objectCode() { return objectCode; }
    public String fieldCode() { return fieldCode; }
    public String aliasHint() { return aliasHint; }
    public FieldMappingType mappingType() { return mappingType; }
    public String columnName() { return columnName; }
    public String dataTypeCode() { return dataTypeCode; }
    public boolean allowSelect() { return allowSelect; }
    public boolean allowFilter() { return allowFilter; }
    public boolean allowSort() { return allowSort; }
}
