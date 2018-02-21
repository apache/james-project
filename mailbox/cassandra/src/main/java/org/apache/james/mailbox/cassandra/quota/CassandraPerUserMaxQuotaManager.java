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

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaCount;
import org.apache.james.mailbox.quota.QuotaSize;

import com.github.fge.lambdas.Throwing;

public class CassandraPerUserMaxQuotaManager implements MaxQuotaManager {

    private final CassandraPerUserMaxQuotaDao dao;

    @Inject
    public CassandraPerUserMaxQuotaManager(CassandraPerUserMaxQuotaDao dao) {
        this.dao = dao;
    }

    @Override
    public void setMaxStorage(QuotaRoot quotaRoot, QuotaSize maxStorageQuota) {
        dao.setMaxStorage(quotaRoot, maxStorageQuota);
    }

    @Override
    public void setMaxMessage(QuotaRoot quotaRoot, QuotaCount maxMessageCount) {
        dao.setMaxMessage(quotaRoot, maxMessageCount);
    }

    @Override
    public void removeMaxMessage(QuotaRoot quotaRoot) {
        dao.removeMaxMessage(quotaRoot);
    }

    @Override
    public void removeMaxStorage(QuotaRoot quotaRoot) {
        dao.removeMaxStorage(quotaRoot);
    }

    @Override
    public void setDefaultMaxStorage(QuotaSize defaultMaxStorage) {
        dao.setDefaultMaxStorage(defaultMaxStorage);
    }

    @Override
    public void removeDefaultMaxStorage() {
        dao.removeDefaultMaxStorage();
    }

    @Override
    public void setDefaultMaxMessage(QuotaCount defaultMaxMessageCount) {
        dao.setDefaultMaxMessage(defaultMaxMessageCount);
    }

    @Override
    public void removeDefaultMaxMessage() {
        dao.removeDefaultMaxMessage();
    }

    @Override
    public Optional<QuotaSize> getDefaultMaxStorage() {
        return dao.getDefaultMaxStorage();
    }

    @Override
    public Optional<QuotaCount> getDefaultMaxMessage() {
        return dao.getDefaultMaxMessage();
    }

    @Override
    public Optional<QuotaSize> getMaxStorage(QuotaRoot quotaRoot) {
        return dao.getMaxStorage(quotaRoot)
            .map(Optional::of)
            .orElseGet(Throwing.supplier(this::getDefaultMaxStorage).sneakyThrow());
    }

    @Override
    public Optional<QuotaCount> getMaxMessage(QuotaRoot quotaRoot) {
        return dao.getMaxMessage(quotaRoot)
            .map(Optional::of)
            .orElseGet(Throwing.supplier(this::getDefaultMaxMessage).sneakyThrow());
    }
}
