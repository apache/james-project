/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.backends.cassandra.components;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;
import static org.apache.james.backends.cassandra.components.CassandraQuotaLimitTable.IDENTIFIER;
import static org.apache.james.backends.cassandra.components.CassandraQuotaLimitTable.QUOTA_COMPONENT;
import static org.apache.james.backends.cassandra.components.CassandraQuotaLimitTable.QUOTA_LIMIT;
import static org.apache.james.backends.cassandra.components.CassandraQuotaLimitTable.QUOTA_SCOPE;
import static org.apache.james.backends.cassandra.components.CassandraQuotaLimitTable.QUOTA_TYPE;
import static org.apache.james.backends.cassandra.components.CassandraQuotaLimitTable.TABLE_NAME;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.core.quota.QuotaLimit;
import org.apache.james.core.quota.QuotaScope;
import org.apache.james.core.quota.QuotaType;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.querybuilder.delete.Delete;
import com.datastax.oss.driver.api.querybuilder.insert.Insert;
import com.datastax.oss.driver.api.querybuilder.select.Select;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraQuotaLimitDao {
    private final CassandraAsyncExecutor queryExecutor;
    private final PreparedStatement getQuotaLimitStatement;
    private final PreparedStatement getQuotaLimitsStatement;
    private final PreparedStatement setQuotaLimitStatement;
    private final PreparedStatement deleteQuotaLimitStatement;

    @Inject
    public CassandraQuotaLimitDao(CqlSession session) {
        this.queryExecutor = new CassandraAsyncExecutor(session);
        this.getQuotaLimitStatement = session.prepare(getQuotaLimitStatement().build());
        this.getQuotaLimitsStatement = session.prepare(getQuotaLimitsStatement().build());
        this.setQuotaLimitStatement = session.prepare(setQuotaLimitStatement().build());
        this.deleteQuotaLimitStatement = session.prepare((deleteQuotaLimitStatement().build()));
    }

    public Mono<QuotaLimit> getQuotaLimit(QuotaLimit.QuotaLimitKey quotaKey) {
        return queryExecutor.executeSingleRow(getQuotaLimitStatement.bind()
            .setString(QUOTA_COMPONENT, quotaKey.getQuotaComponent().getValue())
            .setString(QUOTA_SCOPE, quotaKey.getQuotaScope().getValue())
            .setString(IDENTIFIER, quotaKey.getIdentifier())
            .setString(QUOTA_TYPE, quotaKey.getQuotaType().getValue()))
            .map(this::convertRowToModel);
    }

    public Flux<QuotaLimit> getQuotaLimits(QuotaComponent quotaComponent, QuotaScope quotaScope, String identifier) {
        return queryExecutor.executeRows(getQuotaLimitsStatement.bind()
            .setString(QUOTA_COMPONENT, quotaComponent.getValue())
            .setString(QUOTA_SCOPE, quotaScope.getValue())
            .setString(IDENTIFIER, identifier))
            .map(this::convertRowToModel);
    }

    public Mono<Void> setQuotaLimit(QuotaLimit quotaLimit) {
        return queryExecutor.executeVoid(setQuotaLimitStatement.bind()
            .setString(QUOTA_COMPONENT, quotaLimit.getQuotaComponent().getValue())
            .setString(QUOTA_SCOPE, quotaLimit.getQuotaScope().getValue())
            .setString(IDENTIFIER, quotaLimit.getIdentifier())
            .setString(QUOTA_TYPE, quotaLimit.getQuotaType().getValue())
            .set(QUOTA_LIMIT, quotaLimit.getQuotaLimit().orElse(null), Long.class));
    }

    public Mono<Void> deleteQuotaLimit(QuotaLimit.QuotaLimitKey quotaKey) {
        return queryExecutor.executeVoid(deleteQuotaLimitStatement.bind()
            .setString(QUOTA_COMPONENT, quotaKey.getQuotaComponent().getValue())
            .setString(QUOTA_SCOPE, quotaKey.getQuotaScope().getValue())
            .setString(IDENTIFIER, quotaKey.getIdentifier())
            .setString(QUOTA_TYPE, quotaKey.getQuotaType().getValue()));
    }

    private Select getQuotaLimitStatement() {
        return selectFrom(TABLE_NAME)
            .all()
            .where(column(IDENTIFIER).isEqualTo(bindMarker(IDENTIFIER)),
                column(QUOTA_COMPONENT).isEqualTo(bindMarker(QUOTA_COMPONENT)),
                column(QUOTA_TYPE).isEqualTo(bindMarker(QUOTA_TYPE)),
                column(QUOTA_SCOPE).isEqualTo(bindMarker(QUOTA_SCOPE)));
    }

    private Select getQuotaLimitsStatement() {
        return selectFrom(TABLE_NAME)
            .all()
            .where(column(IDENTIFIER).isEqualTo(bindMarker(IDENTIFIER)),
                column(QUOTA_COMPONENT).isEqualTo(bindMarker(QUOTA_COMPONENT)),
                column(QUOTA_SCOPE).isEqualTo(bindMarker(QUOTA_SCOPE)));
    }

    private Insert setQuotaLimitStatement() {
        return insertInto(TABLE_NAME)
            .value(IDENTIFIER, bindMarker(IDENTIFIER))
            .value(QUOTA_COMPONENT, bindMarker(QUOTA_COMPONENT))
            .value(QUOTA_TYPE, bindMarker(QUOTA_TYPE))
            .value(QUOTA_SCOPE, bindMarker(QUOTA_SCOPE))
            .value(QUOTA_LIMIT, bindMarker(QUOTA_LIMIT));
    }

    private Delete deleteQuotaLimitStatement() {
        return deleteFrom(TABLE_NAME)
            .where(column(IDENTIFIER).isEqualTo(bindMarker(IDENTIFIER)),
                column(QUOTA_COMPONENT).isEqualTo(bindMarker(QUOTA_COMPONENT)),
                column(QUOTA_TYPE).isEqualTo(bindMarker(QUOTA_TYPE)),
                column(QUOTA_SCOPE).isEqualTo(bindMarker(QUOTA_SCOPE)));
    }

    private QuotaLimit convertRowToModel(Row row) {
        return QuotaLimit.builder().quotaComponent(QuotaComponent.of(row.get(QUOTA_COMPONENT, String.class)))
            .quotaScope(QuotaScope.of(row.get(QUOTA_SCOPE, String.class)))
            .identifier(row.get(IDENTIFIER, String.class))
            .quotaType(QuotaType.of(row.get(QUOTA_TYPE, String.class)))
            .quotaLimit(row.get(QUOTA_LIMIT, Long.class))
            .build();
    }

}