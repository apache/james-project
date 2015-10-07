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
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import org.apache.james.mailbox.cassandra.table.CassandraDefaultMaxQuota;
import org.apache.james.mailbox.cassandra.table.CassandraMaxQuota;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.MaxQuotaManager;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class CassandraPerUserMaxQuotaManager implements MaxQuotaManager {

    private final Session session;
    private final PreparedStatement setMaxStorageStatement;
    private final PreparedStatement setMaxMessageStatement;
    private final PreparedStatement getMaxStorageStatement;
    private final PreparedStatement getMaxMessageStatement;
    private final PreparedStatement setDefaultMaxStorageStatement;
    private final PreparedStatement setDefaultMaxMessageStatement;
    private final Statement getDefaultMaxStorageStatement;
    private final Statement getDefaultMaxMessageStatement;

    @Inject
    public CassandraPerUserMaxQuotaManager(Session session) {
        this.session = session;
        this.setMaxStorageStatement = session.prepare(insertInto(CassandraMaxQuota.TABLE_NAME)
            .value(CassandraMaxQuota.QUOTA_ROOT, bindMarker())
            .value(CassandraMaxQuota.STORAGE, bindMarker()));
        this.setMaxMessageStatement = session.prepare(insertInto(CassandraMaxQuota.TABLE_NAME)
            .value(CassandraMaxQuota.QUOTA_ROOT, bindMarker())
            .value(CassandraMaxQuota.MESSAGE_COUNT, bindMarker()));
        this.getMaxStorageStatement = session.prepare(select(CassandraMaxQuota.STORAGE)
            .from(CassandraMaxQuota.TABLE_NAME)
            .where(eq(CassandraMaxQuota.QUOTA_ROOT, bindMarker())));
        this.getMaxMessageStatement = session.prepare(select(CassandraMaxQuota.MESSAGE_COUNT)
            .from(CassandraMaxQuota.TABLE_NAME)
            .where(eq(CassandraMaxQuota.QUOTA_ROOT, bindMarker())));
        this.getDefaultMaxMessageStatement = select(CassandraDefaultMaxQuota.VALUE)
            .from(CassandraDefaultMaxQuota.TABLE_NAME)
            .where(eq(CassandraDefaultMaxQuota.TYPE, CassandraDefaultMaxQuota.MESSAGE));
        this.getDefaultMaxStorageStatement = select(CassandraDefaultMaxQuota.VALUE)
            .from(CassandraDefaultMaxQuota.TABLE_NAME)
            .where(eq(CassandraDefaultMaxQuota.TYPE, CassandraDefaultMaxQuota.STORAGE));
        this.setDefaultMaxMessageStatement = session.prepare(insertInto(CassandraDefaultMaxQuota.TABLE_NAME)
            .value(CassandraDefaultMaxQuota.TYPE, CassandraDefaultMaxQuota.MESSAGE)
            .value(CassandraDefaultMaxQuota.VALUE, bindMarker()));
        this.setDefaultMaxStorageStatement = session.prepare(insertInto(CassandraDefaultMaxQuota.TABLE_NAME)
            .value(CassandraDefaultMaxQuota.TYPE, CassandraDefaultMaxQuota.STORAGE)
            .value(CassandraDefaultMaxQuota.VALUE, bindMarker()));
    }

    @Override
    public void setMaxStorage(QuotaRoot quotaRoot, long maxStorageQuota) throws MailboxException {
        session.execute(setMaxStorageStatement.bind(quotaRoot.getValue(), maxStorageQuota));
    }

    @Override
    public void setMaxMessage(QuotaRoot quotaRoot, long maxMessageCount) throws MailboxException {
        session.execute(setMaxMessageStatement.bind(quotaRoot.getValue(), maxMessageCount));
    }

    @Override
    public void setDefaultMaxStorage(long defaultMaxStorage) throws MailboxException {
        session.execute(setDefaultMaxStorageStatement.bind(defaultMaxStorage));
    }

    @Override
    public void setDefaultMaxMessage(long defaultMaxMessageCount) throws MailboxException {
        session.execute(setDefaultMaxMessageStatement.bind(defaultMaxMessageCount));
    }

    @Override
    public long getDefaultMaxStorage() throws MailboxException {
        ResultSet resultSet = session.execute(getDefaultMaxStorageStatement);
        if (resultSet.isExhausted()) {
            return Quota.UNLIMITED;
        }
        return resultSet.one().getLong(CassandraDefaultMaxQuota.VALUE);
    }

    @Override
    public long getDefaultMaxMessage() throws MailboxException {
        ResultSet resultSet = session.execute(getDefaultMaxMessageStatement);
        if (resultSet.isExhausted()) {
            return Quota.UNLIMITED;
        }
        return resultSet.one().getLong(CassandraDefaultMaxQuota.VALUE);
    }

    @Override
    public long getMaxStorage(QuotaRoot quotaRoot) throws MailboxException {
        ResultSet resultSet = session.execute(getMaxStorageStatement.bind(quotaRoot.getValue()));
        if (resultSet.isExhausted()) {
            return getDefaultMaxStorage();
        }
        return resultSet.one().getLong(CassandraMaxQuota.STORAGE);
    }

    @Override
    public long getMaxMessage(QuotaRoot quotaRoot) throws MailboxException {
        ResultSet resultSet = session.execute(getMaxMessageStatement.bind(quotaRoot.getValue()));
        if (resultSet.isExhausted()) {
            return getDefaultMaxMessage();
        }
        return resultSet.one().getLong(CassandraMaxQuota.MESSAGE_COUNT);
    }
}
