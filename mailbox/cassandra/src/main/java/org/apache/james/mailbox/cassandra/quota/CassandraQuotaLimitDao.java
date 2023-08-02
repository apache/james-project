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

package org.apache.james.mailbox.cassandra.quota;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.core.quota.QuotaComponentFactory;
import org.apache.james.core.quota.QuotaLimit;
import org.apache.james.core.quota.QuotaScope;
import org.apache.james.core.quota.QuotaScopeFactory;
import org.apache.james.core.quota.QuotaType;
import org.apache.james.core.quota.QuotaTypeFactory;
import org.apache.james.mailbox.cassandra.table.CassandraQuotaLimit;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.querybuilder.insert.Insert;
import com.datastax.oss.driver.api.querybuilder.select.Select;

import reactor.core.publisher.Mono;

public class CassandraQuotaLimitDao {

    private final CassandraAsyncExecutor queryExecutor;
    private final PreparedStatement getQuotaLimitStatement;
    private final PreparedStatement setQuotaLimitStatement;
    private final QuotaComponentFactory quotaComponentFactory;
    private final QuotaTypeFactory quotaTypeFactory;
    private final QuotaScopeFactory quotaScopeFactory;

    @Inject
    public CassandraQuotaLimitDao(CqlSession session, QuotaComponentFactory quotaComponentFactory, QuotaTypeFactory quotaTypeFactory, QuotaScopeFactory quotaScopeFactory) {
        this.queryExecutor = new CassandraAsyncExecutor(session);
        this.getQuotaLimitStatement = session.prepare(getQuotaLimitStatement().build());
        this.setQuotaLimitStatement = session.prepare(setQuotaLimitStatement().build());
        this.quotaComponentFactory = quotaComponentFactory;
        this.quotaTypeFactory = quotaTypeFactory;
        this.quotaScopeFactory = quotaScopeFactory;
    }

    Mono<QuotaLimit> getQuotaLimit(String identifier, QuotaComponent quotaComponent, QuotaType quotaType, QuotaScope quotaScope) {
        return queryExecutor.executeSingleRow(getQuotaLimitStatement.bind(identifier, quotaComponent.asString(), quotaType.asString(), quotaScope.asString()))
            .map(row -> QuotaLimit.of(row.get(CassandraQuotaLimit.IDENTIFIER, String.class), quotaComponentFactory.parse(row.get(CassandraQuotaLimit.QUOTA_COMPONENT, String.class)),
                quotaTypeFactory.parse(row.get(CassandraQuotaLimit.QUOTA_TYPE, String.class)), quotaScopeFactory.parse(row.get(CassandraQuotaLimit.QUOTA_SCOPE, String.class)),
                    row.get(CassandraQuotaLimit.MAX_VALUE, Long.class)));
    }

    Mono<Void> setQuotaLimit(QuotaLimit quotaLimit) {
        return queryExecutor.executeVoid(setQuotaLimitStatement.bind(quotaLimit.getIdentifier(),
            quotaLimit.getQuotaComponent().asString(), quotaLimit.getQuotaType().asString(), quotaLimit.getQuotaScope().asString(), quotaLimit.getMaxValue()));
    }

    private Select getQuotaLimitStatement() {
        return selectFrom(CassandraQuotaLimit.TABLE_NAME)
            .all()
            .where(column(CassandraQuotaLimit.IDENTIFIER).isEqualTo(bindMarker()),
                column(CassandraQuotaLimit.QUOTA_COMPONENT).isEqualTo(bindMarker()),
                column(CassandraQuotaLimit.QUOTA_TYPE).isEqualTo(bindMarker()),
                column(CassandraQuotaLimit.QUOTA_SCOPE).isEqualTo(bindMarker()));
    }

    private Insert setQuotaLimitStatement() {
        return insertInto(CassandraQuotaLimit.TABLE_NAME)
            .value(CassandraQuotaLimit.IDENTIFIER, bindMarker())
            .value(CassandraQuotaLimit.QUOTA_COMPONENT, bindMarker())
            .value(CassandraQuotaLimit.QUOTA_TYPE, bindMarker())
            .value(CassandraQuotaLimit.QUOTA_SCOPE, bindMarker())
            .value(CassandraQuotaLimit.MAX_VALUE, bindMarker());
    }

}
