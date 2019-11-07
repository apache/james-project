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
import org.apache.james.mailbox.cassandra.table.CassandraMaxQuota;
import org.apache.james.mailbox.model.QuotaRoot;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.Select;

public class CassandraPerUserMaxQuotaDao {

    private final Session session;
    private final PreparedStatement setMaxStorageStatement;
    private final PreparedStatement setMaxMessageStatement;
    private final PreparedStatement getMaxStorageStatement;
    private final PreparedStatement getMaxMessageStatement;
    private final PreparedStatement removeMaxStorageStatement;
    private final PreparedStatement removeMaxMessageStatement;

    @Inject
    public CassandraPerUserMaxQuotaDao(Session session) {
        this.session = session;
        this.setMaxStorageStatement = session.prepare(setMaxStorageStatement());
        this.setMaxMessageStatement = session.prepare(setMaxMessageStatement());
        this.getMaxStorageStatement = session.prepare(getMaxStorageStatement());
        this.getMaxMessageStatement = session.prepare(getMaxMessageStatement());
        this.removeMaxStorageStatement = session.prepare(removeMaxStorageStatement());
        this.removeMaxMessageStatement = session.prepare(removeMaxMessageStatement());
    }

    private Delete.Where removeMaxMessageStatement() {
        return delete().column(CassandraMaxQuota.MESSAGE_COUNT)
            .from(CassandraMaxQuota.TABLE_NAME)
            .where(eq(CassandraMaxQuota.QUOTA_ROOT, bindMarker()));
    }

    private Delete.Where removeMaxStorageStatement() {
        return delete().column(CassandraMaxQuota.STORAGE)
            .from(CassandraMaxQuota.TABLE_NAME)
            .where(eq(CassandraMaxQuota.QUOTA_ROOT, bindMarker()));
    }

    private Select.Where getMaxMessageStatement() {
        return select(CassandraMaxQuota.MESSAGE_COUNT)
            .from(CassandraMaxQuota.TABLE_NAME)
            .where(eq(CassandraMaxQuota.QUOTA_ROOT, bindMarker()));
    }

    private Select.Where getMaxStorageStatement() {
        return select(CassandraMaxQuota.STORAGE)
            .from(CassandraMaxQuota.TABLE_NAME)
            .where(eq(CassandraMaxQuota.QUOTA_ROOT, bindMarker()));
    }

    private Insert setMaxMessageStatement() {
        return insertInto(CassandraMaxQuota.TABLE_NAME)
            .value(CassandraMaxQuota.QUOTA_ROOT, bindMarker())
            .value(CassandraMaxQuota.MESSAGE_COUNT, bindMarker());
    }

    private Insert setMaxStorageStatement() {
        return insertInto(CassandraMaxQuota.TABLE_NAME)
            .value(CassandraMaxQuota.QUOTA_ROOT, bindMarker())
            .value(CassandraMaxQuota.STORAGE, bindMarker());
    }

    public void setMaxStorage(QuotaRoot quotaRoot, QuotaSizeLimit maxStorageQuota) {
        session.execute(setMaxStorageStatement.bind(quotaRoot.getValue(), QuotaCodec.quotaValueToLong(maxStorageQuota)));
    }

    public void setMaxMessage(QuotaRoot quotaRoot, QuotaCountLimit maxMessageCount) {
        session.execute(setMaxMessageStatement.bind(quotaRoot.getValue(), QuotaCodec.quotaValueToLong(maxMessageCount)));
    }

    public Optional<QuotaSizeLimit> getMaxStorage(QuotaRoot quotaRoot) {
        ResultSet resultSet = session.execute(getMaxStorageStatement.bind(quotaRoot.getValue()));
        if (resultSet.isExhausted()) {
            return Optional.empty();
        }
        Long maxStorage = resultSet.one().get(CassandraMaxQuota.STORAGE, Long.class);
        return QuotaCodec.longToQuotaSize(maxStorage);
    }

    public Optional<QuotaCountLimit> getMaxMessage(QuotaRoot quotaRoot) {
        ResultSet resultSet = session.execute(getMaxMessageStatement.bind(quotaRoot.getValue()));
        if (resultSet.isExhausted()) {
            return Optional.empty();
        }
        Long maxMessages = resultSet.one().get(CassandraMaxQuota.MESSAGE_COUNT, Long.class);
        return QuotaCodec.longToQuotaCount(maxMessages);
    }

    public void removeMaxMessage(QuotaRoot quotaRoot) {
        session.execute(removeMaxMessageStatement.bind(quotaRoot.getValue()));
    }

    public void removeMaxStorage(QuotaRoot quotaRoot) {
        session.execute(removeMaxStorageStatement.bind(quotaRoot.getValue()));
    }

}
