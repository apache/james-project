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
import static com.datastax.driver.core.querybuilder.QueryBuilder.decr;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.incr;
import static com.datastax.driver.core.querybuilder.QueryBuilder.update;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.google.common.base.Preconditions;
import org.apache.james.mailbox.cassandra.table.CassandraCurrentQuota;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.store.quota.StoreCurrentQuotaManager;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class CassandraCurrentQuotaManager implements StoreCurrentQuotaManager {

    private final Session session;
    private final PreparedStatement increaseStatement;
    private final PreparedStatement decreaseStatement;
    private final PreparedStatement getCurrentMessageCountStatement;
    private final PreparedStatement getCurrentStorageStatement;

    @Inject
    public CassandraCurrentQuotaManager(Session session) {
        this.session = session;
        this.increaseStatement = session.prepare(update(CassandraCurrentQuota.TABLE_NAME)
            .with(incr(CassandraCurrentQuota.MESSAGE_COUNT, bindMarker()))
            .and(incr(CassandraCurrentQuota.STORAGE, bindMarker()))
            .where(eq(CassandraCurrentQuota.QUOTA_ROOT, bindMarker())));
        this.decreaseStatement = session.prepare(update(CassandraCurrentQuota.TABLE_NAME)
            .with(decr(CassandraCurrentQuota.MESSAGE_COUNT, bindMarker()))
            .and(decr(CassandraCurrentQuota.STORAGE, bindMarker()))
            .where(eq(CassandraCurrentQuota.QUOTA_ROOT, bindMarker())));
        this.getCurrentMessageCountStatement = session.prepare(select(CassandraCurrentQuota.MESSAGE_COUNT)
            .from(CassandraCurrentQuota.TABLE_NAME)
            .where(eq(CassandraCurrentQuota.QUOTA_ROOT, bindMarker())));
        this.getCurrentStorageStatement = session.prepare(select(CassandraCurrentQuota.STORAGE)
            .from(CassandraCurrentQuota.TABLE_NAME)
            .where(eq(CassandraCurrentQuota.QUOTA_ROOT, bindMarker())));
    }

    @Override
    public void increase(QuotaRoot quotaRoot, long count, long size) throws MailboxException {
        checkArguments(count, size);
        session.execute(increaseStatement.bind(count, size, quotaRoot.getValue()));
    }

    @Override
    public void decrease(QuotaRoot quotaRoot, long count, long size) throws MailboxException {
        checkArguments(count, size);
        session.execute(decreaseStatement.bind(count, size, quotaRoot.getValue()));
    }

    @Override
    public long getCurrentMessageCount(QuotaRoot quotaRoot) throws MailboxException {
        ResultSet resultSet = session.execute(getCurrentMessageCountStatement.bind(quotaRoot.getValue()));
        if (resultSet.isExhausted()) {
            return 0L;
        }
        return resultSet.one().getLong(CassandraCurrentQuota.MESSAGE_COUNT);
    }

    @Override
    public long getCurrentStorage(QuotaRoot quotaRoot) throws MailboxException {
        ResultSet resultSet = session.execute(getCurrentStorageStatement.bind(quotaRoot.getValue()));
        if (resultSet.isExhausted()) {
            return 0L;
        }
        return resultSet.one().getLong(CassandraCurrentQuota.STORAGE);
    }

    private void checkArguments(long count, long size) {
        Preconditions.checkArgument(count > 0, "Count should be positive");
        Preconditions.checkArgument(size > 0, "Size should be positive");
    }
}
