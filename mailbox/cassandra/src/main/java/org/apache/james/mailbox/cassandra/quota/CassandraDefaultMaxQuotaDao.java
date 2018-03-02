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

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.mailbox.cassandra.table.CassandraDefaultMaxQuota;
import org.apache.james.mailbox.quota.QuotaCount;
import org.apache.james.mailbox.quota.QuotaSize;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.Select;

public class CassandraDefaultMaxQuotaDao {

    private final Session session;
    private final PreparedStatement setDefaultMaxStorageStatement;
    private final PreparedStatement setDefaultMaxMessageStatement;
    private final PreparedStatement getDefaultMaxStatement;
    private final PreparedStatement removeDefaultMaxQuotaStatement;

    @Inject
    public CassandraDefaultMaxQuotaDao(Session session) {
        this.session = session;
        this.getDefaultMaxStatement = session.prepare(getDefaultMaxStatement());
        this.setDefaultMaxMessageStatement = session.prepare(setDefaultMaxMessageStatement());
        this.setDefaultMaxStorageStatement = session.prepare(setDefaultMaxStorageStatement());
        this.removeDefaultMaxQuotaStatement = session.prepare(removeDefaultMaxQuotaStatement());
    }

    private Delete.Where removeDefaultMaxQuotaStatement() {
        return delete().all()
            .from(CassandraDefaultMaxQuota.TABLE_NAME)
            .where(eq(CassandraDefaultMaxQuota.TYPE, bindMarker(CassandraDefaultMaxQuota.TYPE)));
    }

    private Insert setDefaultMaxStorageStatement() {
        return insertInto(CassandraDefaultMaxQuota.TABLE_NAME)
            .value(CassandraDefaultMaxQuota.TYPE, CassandraDefaultMaxQuota.STORAGE)
            .value(CassandraDefaultMaxQuota.VALUE, bindMarker());
    }

    private Insert setDefaultMaxMessageStatement() {
        return insertInto(CassandraDefaultMaxQuota.TABLE_NAME)
            .value(CassandraDefaultMaxQuota.TYPE, CassandraDefaultMaxQuota.MESSAGE)
            .value(CassandraDefaultMaxQuota.VALUE, bindMarker());
    }

    private Select.Where getDefaultMaxStatement() {
        return select(CassandraDefaultMaxQuota.VALUE)
            .from(CassandraDefaultMaxQuota.TABLE_NAME)
            .where(eq(CassandraDefaultMaxQuota.TYPE, bindMarker(CassandraDefaultMaxQuota.TYPE)));
    }

    public void setDefaultMaxStorage(QuotaSize defaultMaxStorage) {
        session.execute(setDefaultMaxStorageStatement.bind(QuotaCodec.quotaValueToLong(defaultMaxStorage)));
    }

    public void setDefaultMaxMessage(QuotaCount defaultMaxMessageCount) {
        session.execute(setDefaultMaxMessageStatement.bind(QuotaCodec.quotaValueToLong(defaultMaxMessageCount)));
    }

    public Optional<QuotaSize> getDefaultMaxStorage() {
        ResultSet resultSet = session.execute(getDefaultMaxStatement.bind()
            .setString(CassandraDefaultMaxQuota.TYPE, CassandraDefaultMaxQuota.STORAGE));
        if (resultSet.isExhausted()) {
            return Optional.empty();
        }
        Long maxStorage = resultSet.one().get(CassandraDefaultMaxQuota.VALUE, Long.class);
        return QuotaCodec.longToQuotaSize(maxStorage);
    }

    public Optional<QuotaCount> getDefaultMaxMessage() {
        ResultSet resultSet = session.execute(getDefaultMaxStatement.bind()
            .setString(CassandraDefaultMaxQuota.TYPE, CassandraDefaultMaxQuota.MESSAGE));
        if (resultSet.isExhausted()) {
            return Optional.empty();
        }
        Long maxMessages = resultSet.one().get(CassandraDefaultMaxQuota.VALUE, Long.class);
        return QuotaCodec.longToQuotaCount(maxMessages);
    }

    public void removeDefaultMaxStorage() {
        session.execute(removeDefaultMaxQuotaStatement.bind(CassandraDefaultMaxQuota.STORAGE));
    }

    public void removeDefaultMaxMessage() {
        session.execute(removeDefaultMaxQuotaStatement.bind(CassandraDefaultMaxQuota.MESSAGE));
    }
}
