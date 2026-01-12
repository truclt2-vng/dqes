package com.a4b.dqes.query.util;

import java.util.Objects;
import java.util.regex.Pattern;

public final class Ident {
    private Ident() {}

    private static final Pattern IDENT = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Pattern SCHEMA_TABLE = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*\\.[A-Za-z_][A-Za-z0-9_]*$");

    public static String quoteIdentifier(String ident) {
        Objects.requireNonNull(ident, "ident");
        if (!IDENT.matcher(ident).matches()) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + ident);
        }
        return "\"" + ident + "\"";
    }

    public static String quoteSchemaTable(String schemaTable) {
        Objects.requireNonNull(schemaTable, "schemaTable");
        if (!SCHEMA_TABLE.matcher(schemaTable).matches()) {
            throw new IllegalArgumentException("Invalid schema.table: " + schemaTable);
        }
        String[] p = schemaTable.split("\\.");
        return quoteIdentifier(p[0]) + "." + quoteIdentifier(p[1]);
    }

    public static String col(String alias, String colName) {
        Objects.requireNonNull(alias, "alias");
        return alias + "." + quoteIdentifier(colName);
    }
}
