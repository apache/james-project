package org.apache.james.domainlist.jpa;

import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public interface PostgresDomainModule {
    interface PostgresDomainTable {
        Table<Record> TABLE_NAME = DSL.table("domains");

        Field<String> DOMAIN = DSL.field("domain", SQLDataType.VARCHAR.notNull());

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(DOMAIN)
                .constraint(DSL.primaryKey(DOMAIN))))
            .disableRowLevelSecurity();
    }

    PostgresModule MODULE = PostgresModule.builder()
        .addTable(PostgresDomainTable.TABLE)
        .build();
}
