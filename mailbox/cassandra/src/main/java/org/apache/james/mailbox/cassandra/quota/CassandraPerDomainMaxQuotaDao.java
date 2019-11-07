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

import org.apache.james.core.Domain;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.cassandra.table.CassandraDomainMaxQuota;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.Select;

public class CassandraPerDomainMaxQuotaDao {

    private final Session session;
    private final PreparedStatement setMaxStorageStatement;
    private final PreparedStatement setMaxMessageStatement;
    private final PreparedStatement getMaxStorageStatement;
    private final PreparedStatement getMaxMessageStatement;
    private final PreparedStatement removeMaxStorageStatement;
    private final PreparedStatement removeMaxMessageStatement;

    @Inject
    public CassandraPerDomainMaxQuotaDao(Session session) {
        this.session = session;
        this.setMaxStorageStatement = session.prepare(setMaxStorageStatement());
        this.setMaxMessageStatement = session.prepare(setMaxMessageStatement());
        this.getMaxStorageStatement = session.prepare(getMaxStorageStatement());
        this.getMaxMessageStatement = session.prepare(getMaxMessageStatement());
        this.removeMaxStorageStatement = session.prepare(removeMaxStorageStatement());
        this.removeMaxMessageStatement = session.prepare(removeMaxMessageStatement());
    }

    private Delete.Where removeMaxMessageStatement() {
        return delete().column(CassandraDomainMaxQuota.MESSAGE_COUNT)
            .from(CassandraDomainMaxQuota.TABLE_NAME)
            .where(eq(CassandraDomainMaxQuota.DOMAIN, bindMarker()));
    }

    private Delete.Where removeMaxStorageStatement() {
        return delete().column(CassandraDomainMaxQuota.STORAGE)
            .from(CassandraDomainMaxQuota.TABLE_NAME)
            .where(eq(CassandraDomainMaxQuota.DOMAIN, bindMarker()));
    }

    private Select.Where getMaxMessageStatement() {
        return select(CassandraDomainMaxQuota.MESSAGE_COUNT)
            .from(CassandraDomainMaxQuota.TABLE_NAME)
            .where(eq(CassandraDomainMaxQuota.DOMAIN, bindMarker()));
    }

    private Select.Where getMaxStorageStatement() {
        return select(CassandraDomainMaxQuota.STORAGE)
            .from(CassandraDomainMaxQuota.TABLE_NAME)
            .where(eq(CassandraDomainMaxQuota.DOMAIN, bindMarker()));
    }

    private Insert setMaxMessageStatement() {
        return insertInto(CassandraDomainMaxQuota.TABLE_NAME)
            .value(CassandraDomainMaxQuota.DOMAIN, bindMarker())
            .value(CassandraDomainMaxQuota.MESSAGE_COUNT, bindMarker());
    }

    private Insert setMaxStorageStatement() {
        return insertInto(CassandraDomainMaxQuota.TABLE_NAME)
            .value(CassandraDomainMaxQuota.DOMAIN, bindMarker())
            .value(CassandraDomainMaxQuota.STORAGE, bindMarker());
    }

    public void setMaxStorage(Domain domain, QuotaSizeLimit maxStorageQuota) {
        session.execute(setMaxStorageStatement.bind(domain.asString(), QuotaCodec.quotaValueToLong(maxStorageQuota)));
    }

    public void setMaxMessage(Domain domain, QuotaCountLimit maxMessageCount) {
        session.execute(setMaxMessageStatement.bind(domain.asString(), QuotaCodec.quotaValueToLong(maxMessageCount)));
    }

    public Optional<QuotaSizeLimit> getMaxStorage(Domain domain) {
        ResultSet resultSet = session.execute(getMaxStorageStatement.bind(domain.asString()));
        if (resultSet.isExhausted()) {
            return Optional.empty();
        }
        Long maxStorage = resultSet.one().get(CassandraDomainMaxQuota.STORAGE, Long.class);
        return QuotaCodec.longToQuotaSize(maxStorage);
    }

    public Optional<QuotaCountLimit> getMaxMessage(Domain domain) {
        ResultSet resultSet = session.execute(getMaxMessageStatement.bind(domain.asString()));
        if (resultSet.isExhausted()) {
            return Optional.empty();
        }
        Long maxMessages = resultSet.one().get(CassandraDomainMaxQuota.MESSAGE_COUNT, Long.class);
        return QuotaCodec.longToQuotaCount(maxMessages);
    }

    public void removeMaxMessage(Domain domain) {
        session.execute(removeMaxMessageStatement.bind(domain.asString()));
    }

    public void removeMaxStorage(Domain domain) {
        session.execute(removeMaxStorageStatement.bind(domain.asString()));
    }

}
