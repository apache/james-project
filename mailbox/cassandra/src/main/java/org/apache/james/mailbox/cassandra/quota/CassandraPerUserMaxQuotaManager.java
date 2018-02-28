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

import javax.inject.Inject;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.MaxQuotaManager;

import com.github.fge.lambdas.Throwing;

public class CassandraPerUserMaxQuotaManager implements MaxQuotaManager {

    private final CassandraPerUserMaxQuotaDao dao;

    @Inject
    public CassandraPerUserMaxQuotaManager(CassandraPerUserMaxQuotaDao dao) {
        this.dao = dao;
    }

    @Override
    public void setMaxStorage(QuotaRoot quotaRoot, long maxStorageQuota) throws MailboxException {
        dao.setMaxStorage(quotaRoot, maxStorageQuota);
    }

    @Override
    public void setMaxMessage(QuotaRoot quotaRoot, long maxMessageCount) throws MailboxException {
        dao.setMaxMessage(quotaRoot, maxMessageCount);
    }

    @Override
    public void setDefaultMaxStorage(long defaultMaxStorage) throws MailboxException {
        dao.setDefaultMaxStorage(defaultMaxStorage);
    }

    @Override
    public void setDefaultMaxMessage(long defaultMaxMessageCount) throws MailboxException {
        dao.setDefaultMaxMessage(defaultMaxMessageCount);
    }

    @Override
    public long getDefaultMaxStorage() throws MailboxException {
        return dao.getDefaultMaxStorage().orElse(Quota.UNLIMITED);
    }

    @Override
    public long getDefaultMaxMessage() throws MailboxException {
        return dao.getDefaultMaxMessage().orElse(Quota.UNLIMITED);
    }

    @Override
    public long getMaxStorage(QuotaRoot quotaRoot) throws MailboxException {
        return dao.getMaxStorage(quotaRoot).orElseGet(Throwing.supplier(this::getDefaultMaxStorage).sneakyThrow());
    }

    @Override
    public long getMaxMessage(QuotaRoot quotaRoot) throws MailboxException {
        return dao.getMaxMessage(quotaRoot).orElseGet(Throwing.supplier(this::getDefaultMaxMessage).sneakyThrow());
    }
}
