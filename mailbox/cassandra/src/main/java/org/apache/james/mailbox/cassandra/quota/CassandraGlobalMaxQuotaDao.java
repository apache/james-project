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

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.cassandra.table.CassandraGlobalMaxQuota;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.Select;

public class CassandraGlobalMaxQuotaDao {

    private final Session session;
    private final PreparedStatement setGlobalMaxStorageStatement;
    private final PreparedStatement setGlobalMaxMessageStatement;
    private final PreparedStatement getGlobalMaxStatement;
    private final PreparedStatement removeGlobalMaxQuotaStatement;

    @Inject
    public CassandraGlobalMaxQuotaDao(Session session) {
        this.session = session;
        this.getGlobalMaxStatement = session.prepare(getGlobalMaxStatement());
        this.setGlobalMaxMessageStatement = session.prepare(setGlobalMaxMessageStatement());
        this.setGlobalMaxStorageStatement = session.prepare(setGlobalMaxStorageStatement());
        this.removeGlobalMaxQuotaStatement = session.prepare(removeGlobalMaxQuotaStatement());
    }

    private Delete.Where removeGlobalMaxQuotaStatement() {
        return delete().all()
            .from(CassandraGlobalMaxQuota.TABLE_NAME)
            .where(eq(CassandraGlobalMaxQuota.TYPE, bindMarker(CassandraGlobalMaxQuota.TYPE)));
    }

    private Insert setGlobalMaxStorageStatement() {
        return insertInto(CassandraGlobalMaxQuota.TABLE_NAME)
            .value(CassandraGlobalMaxQuota.TYPE, CassandraGlobalMaxQuota.STORAGE)
            .value(CassandraGlobalMaxQuota.VALUE, bindMarker());
    }

    private Insert setGlobalMaxMessageStatement() {
        return insertInto(CassandraGlobalMaxQuota.TABLE_NAME)
            .value(CassandraGlobalMaxQuota.TYPE, CassandraGlobalMaxQuota.MESSAGE)
            .value(CassandraGlobalMaxQuota.VALUE, bindMarker());
    }

    private Select.Where getGlobalMaxStatement() {
        return select(CassandraGlobalMaxQuota.VALUE)
            .from(CassandraGlobalMaxQuota.TABLE_NAME)
            .where(eq(CassandraGlobalMaxQuota.TYPE, bindMarker(CassandraGlobalMaxQuota.TYPE)));
    }

    public void setGlobalMaxStorage(QuotaSizeLimit globalMaxStorage) {
        session.execute(setGlobalMaxStorageStatement.bind(QuotaCodec.quotaValueToLong(globalMaxStorage)));
    }

    public void setGlobalMaxMessage(QuotaCountLimit globalMaxMessageCount) {
        session.execute(setGlobalMaxMessageStatement.bind(QuotaCodec.quotaValueToLong(globalMaxMessageCount)));
    }

    public Optional<QuotaSizeLimit> getGlobalMaxStorage() {
        ResultSet resultSet = session.execute(getGlobalMaxStatement.bind()
            .setString(CassandraGlobalMaxQuota.TYPE, CassandraGlobalMaxQuota.STORAGE));
        if (resultSet.isExhausted()) {
            return Optional.empty();
        }
        Long maxStorage = resultSet.one().get(CassandraGlobalMaxQuota.VALUE, Long.class);
        return QuotaCodec.longToQuotaSize(maxStorage);
    }

    public Optional<QuotaCountLimit> getGlobalMaxMessage() {
        ResultSet resultSet = session.execute(getGlobalMaxStatement.bind()
            .setString(CassandraGlobalMaxQuota.TYPE, CassandraGlobalMaxQuota.MESSAGE));
        if (resultSet.isExhausted()) {
            return Optional.empty();
        }
        Long maxMessages = resultSet.one().get(CassandraGlobalMaxQuota.VALUE, Long.class);
        return QuotaCodec.longToQuotaCount(maxMessages);
    }

    public void removeGlobaltMaxStorage() {
        session.execute(removeGlobalMaxQuotaStatement.bind(CassandraGlobalMaxQuota.STORAGE));
    }

    public void removeGlobalMaxMessage() {
        session.execute(removeGlobalMaxQuotaStatement.bind(CassandraGlobalMaxQuota.MESSAGE));
    }
}
