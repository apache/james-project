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
import org.apache.james.core.quota.QuotaCurrentValue;
import org.apache.james.core.quota.QuotaType;
import org.apache.james.core.quota.QuotaTypeFactory;
import org.apache.james.mailbox.cassandra.table.CassandraQuotaCurrentValue;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.querybuilder.insert.Insert;
import com.datastax.oss.driver.api.querybuilder.select.Select;

import reactor.core.publisher.Mono;

public class CassandraQuotaCurrentValueDao {

    private final CassandraAsyncExecutor queryExecutor;
    private final PreparedStatement getQuotaCurrentValueStatement;
    private final PreparedStatement setQuotaCurrentValueStatement;
    private final QuotaComponentFactory quotaComponentFactory;
    private final QuotaTypeFactory quotaTypeFactory;

    @Inject
    public CassandraQuotaCurrentValueDao(CqlSession session, QuotaComponentFactory quotaComponentFactory, QuotaTypeFactory quotaTypeFactory) {
        this.queryExecutor = new CassandraAsyncExecutor(session);
        this.getQuotaCurrentValueStatement = session.prepare(getQuotaCurrentValueStatement().build());
        this.setQuotaCurrentValueStatement = session.prepare(setQuotaCurrentValueStatement().build());
        this.quotaComponentFactory = quotaComponentFactory;
        this.quotaTypeFactory = quotaTypeFactory;
    }

    Mono<QuotaCurrentValue> getQuotaCurrentValue(String identifier, QuotaComponent quotaComponent, QuotaType quotaType) {
        return queryExecutor.executeSingleRow(getQuotaCurrentValueStatement.bind(identifier, quotaComponent.asString(), quotaType.asString()))
            .map(row -> QuotaCurrentValue.of(row.get(CassandraQuotaCurrentValue.IDENTIFIER, String.class), quotaComponentFactory.parse(row.get(CassandraQuotaCurrentValue.QUOTA_COMPONENT, String.class)),
                quotaTypeFactory.parse(row.get(CassandraQuotaCurrentValue.QUOTA_TYPE, String.class)), row.get(CassandraQuotaCurrentValue.CURRENT_VALUE, Long.class)));
    }

    Mono<Void> setQuotaCurrentValue(QuotaCurrentValue quotaCurrentValue) {
        return queryExecutor.executeVoid(setQuotaCurrentValueStatement.bind(quotaCurrentValue.getIdentifier(),
            quotaCurrentValue.getQuotaComponent().asString(), quotaCurrentValue.getQuotaType().asString(), quotaCurrentValue.getCurrentValue()));
    }

    private Select getQuotaCurrentValueStatement() {
        return selectFrom(CassandraQuotaCurrentValue.TABLE_NAME)
            .all()
            .where(column(CassandraQuotaCurrentValue.IDENTIFIER).isEqualTo(bindMarker()),
                column(CassandraQuotaCurrentValue.QUOTA_COMPONENT).isEqualTo(bindMarker()),
                column(CassandraQuotaCurrentValue.QUOTA_TYPE).isEqualTo(bindMarker()));
    }

    private Insert setQuotaCurrentValueStatement() {
        return insertInto(CassandraQuotaCurrentValue.TABLE_NAME)
            .value(CassandraQuotaCurrentValue.IDENTIFIER, bindMarker())
            .value(CassandraQuotaCurrentValue.QUOTA_COMPONENT, bindMarker())
            .value(CassandraQuotaCurrentValue.QUOTA_TYPE, bindMarker())
            .value(CassandraQuotaCurrentValue.CURRENT_VALUE, bindMarker());
    }

}
