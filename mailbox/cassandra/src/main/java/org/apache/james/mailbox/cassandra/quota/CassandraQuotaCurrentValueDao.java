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
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.update;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;
import static org.apache.james.mailbox.cassandra.table.CassandraQuotaCurrentValueTable.CURRENT_VALUE;
import static org.apache.james.mailbox.cassandra.table.CassandraQuotaCurrentValueTable.IDENTIFIER;
import static org.apache.james.mailbox.cassandra.table.CassandraQuotaCurrentValueTable.QUOTA_COMPONENT;
import static org.apache.james.mailbox.cassandra.table.CassandraQuotaCurrentValueTable.QUOTA_TYPE;
import static org.apache.james.mailbox.cassandra.table.CassandraQuotaCurrentValueTable.TABLE_NAME;

import java.util.Objects;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.core.quota.QuotaCurrentValue;
import org.apache.james.core.quota.QuotaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.querybuilder.delete.Delete;
import com.datastax.oss.driver.api.querybuilder.select.Select;
import com.datastax.oss.driver.api.querybuilder.update.Update;
import com.google.common.base.MoreObjects;

import reactor.core.publisher.Mono;

public class CassandraQuotaCurrentValueDao {

    public static class QuotaKey {

        public static QuotaKey of(QuotaComponent component, Username identifier, QuotaType quotaType) {
            return new QuotaKey(component, identifier, quotaType);
        }

        private final QuotaComponent quotaComponent;
        private final Username identifier;
        private final QuotaType quotaType;

        public QuotaComponent getQuotaComponent() {
            return quotaComponent;
        }

        public Username getIdentifier() {
            return identifier;
        }

        public QuotaType getQuotaType() {
            return quotaType;
        }

        private QuotaKey(QuotaComponent quotaComponent, Username identifier, QuotaType quotaType) {
            this.quotaComponent = quotaComponent;
            this.identifier = identifier;
            this.quotaType = quotaType;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(quotaComponent, identifier, quotaType);
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof QuotaKey) {
                QuotaKey other = (QuotaKey) o;
                return Objects.equals(quotaComponent, other.quotaComponent)
                    && Objects.equals(identifier, other.identifier)
                    && Objects.equals(quotaType, other.quotaType);
            }
            return false;
        }

        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("quotaComponent", quotaComponent)
                .add("identifier", identifier)
                .add("quotaType", quotaType)
                .toString();
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraQuotaCurrentValueDao.class);

    private final CassandraAsyncExecutor queryExecutor;
    private final PreparedStatement increaseStatement;
    private final PreparedStatement decreaseStatement;
    private final PreparedStatement getQuotaCurrentValueStatement;
    private final PreparedStatement deleteQuotaCurrentValueStatement;

    @Inject
    public CassandraQuotaCurrentValueDao(CqlSession session) {
        this.queryExecutor = new CassandraAsyncExecutor(session);
        this.increaseStatement = session.prepare(increaseStatement().build());
        this.decreaseStatement = session.prepare(decreaseStatement().build());
        this.getQuotaCurrentValueStatement = session.prepare(getQuotaCurrentValueStatement().build());
        this.deleteQuotaCurrentValueStatement = session.prepare(deleteQuotaCurrentValueStatement().build());
    }

    public Mono<Void> increase(QuotaKey quotaKey, long amount) {
        return queryExecutor.executeVoid(increaseStatement.bind()
            .setString(QUOTA_COMPONENT, quotaKey.getQuotaComponent().getValue())
            .setString(IDENTIFIER, quotaKey.getIdentifier().asString())
            .setString(QUOTA_TYPE, quotaKey.getQuotaType().getValue())
            .setLong(CURRENT_VALUE, amount))
            .onErrorResume(ex -> {
                LOGGER.error("Failure when increasing {} {} quota for {}. Quota current value is thus not updated and needs recomputation",
                    quotaKey.getQuotaComponent().getValue(), quotaKey.getQuotaType().getValue(), quotaKey.getIdentifier().asString());
                LOGGER.error(ex.getMessage());
                return Mono.empty();
            });
    }

    public Mono<Void> decrease(QuotaKey quotaKey, long amount) {
        return queryExecutor.executeVoid(decreaseStatement.bind()
            .setString(QUOTA_COMPONENT, quotaKey.getQuotaComponent().getValue())
            .setString(IDENTIFIER, quotaKey.getIdentifier().asString())
            .setString(QUOTA_TYPE, quotaKey.getQuotaType().getValue())
            .setLong(CURRENT_VALUE, amount))
            .onErrorResume(ex -> {
                LOGGER.error("Failure when decreasing {} {} quota for {}. Quota current value is thus not updated and needs recomputation",
                    quotaKey.getQuotaComponent().getValue(), quotaKey.getQuotaType().getValue(), quotaKey.getIdentifier().asString());
                LOGGER.error(ex.getMessage());
                return Mono.empty();
            });
    }

    public Mono<QuotaCurrentValue> getQuotaCurrentValue(QuotaKey quotaKey) {
        return queryExecutor.executeSingleRow(getQuotaCurrentValueStatement.bind()
            .setString(QUOTA_COMPONENT, quotaKey.getQuotaComponent().getValue())
            .setString(IDENTIFIER, quotaKey.getIdentifier().asString())
            .setString(QUOTA_TYPE, quotaKey.getQuotaType().getValue()))
            .map(row -> convertRowToModel(row));
    }

    public Mono<Void> deleteQuotaCurrentValue(QuotaKey quotaKey) {
        return queryExecutor.executeVoid(deleteQuotaCurrentValueStatement.bind()
            .setString(QUOTA_COMPONENT, quotaKey.getQuotaComponent().getValue())
            .setString(IDENTIFIER, quotaKey.getIdentifier().asString())
            .setString(QUOTA_TYPE, quotaKey.getQuotaType().getValue()));
    }

    private Update increaseStatement() {
        return update(TABLE_NAME)
            .increment(CURRENT_VALUE, bindMarker(CURRENT_VALUE))
            .where(column(IDENTIFIER).isEqualTo(bindMarker(IDENTIFIER)),
                column(QUOTA_COMPONENT).isEqualTo(bindMarker(QUOTA_COMPONENT)),
                column(QUOTA_TYPE).isEqualTo(bindMarker(QUOTA_TYPE)));
    }

    private Update decreaseStatement() {
        return update(TABLE_NAME)
            .decrement(CURRENT_VALUE, bindMarker(CURRENT_VALUE))
            .where(column(IDENTIFIER).isEqualTo(bindMarker(IDENTIFIER)),
                column(QUOTA_COMPONENT).isEqualTo(bindMarker(QUOTA_COMPONENT)),
                column(QUOTA_TYPE).isEqualTo(bindMarker(QUOTA_TYPE)));
    }

    private Select getQuotaCurrentValueStatement() {
        return selectFrom(TABLE_NAME)
            .all()
            .where(column(IDENTIFIER).isEqualTo(bindMarker(IDENTIFIER)),
                column(QUOTA_COMPONENT).isEqualTo(bindMarker(QUOTA_COMPONENT)),
                column(QUOTA_TYPE).isEqualTo(bindMarker(QUOTA_TYPE)));
    }

    private Delete deleteQuotaCurrentValueStatement() {
        return deleteFrom(TABLE_NAME)
            .where(column(IDENTIFIER).isEqualTo(bindMarker(IDENTIFIER)),
                column(QUOTA_COMPONENT).isEqualTo(bindMarker(QUOTA_COMPONENT)),
                column(QUOTA_TYPE).isEqualTo(bindMarker(QUOTA_TYPE)));
    }

    private QuotaCurrentValue convertRowToModel(Row row) {
        return QuotaCurrentValue.builder().quotaComponent(QuotaComponent.of(row.get(QUOTA_COMPONENT, String.class)))
            .identifier(Username.of(row.get(IDENTIFIER, String.class)))
            .quotaType(QuotaType.of(row.get(QUOTA_TYPE, String.class)))
            .currentValue(row.get(CURRENT_VALUE, Long.class)).build();
    }

}