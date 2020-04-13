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

package org.apache.james.mailbox.inmemory.quota;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.QuotaOperation;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.store.quota.CurrentQuotaCalculator;
import org.apache.james.mailbox.store.quota.CurrentQuotaCalculator.CurrentQuotas;
import org.apache.james.mailbox.store.quota.StoreCurrentQuotaManager;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

public class InMemoryCurrentQuotaManager implements StoreCurrentQuotaManager {

    private final LoadingCache<QuotaRoot, AtomicReference<CurrentQuotas>> quotaCache;

    @Inject
    public InMemoryCurrentQuotaManager(CurrentQuotaCalculator quotaCalculator, SessionProvider sessionProvider) {
        this.quotaCache = CacheBuilder.newBuilder().build(new CacheLoader<QuotaRoot, AtomicReference<CurrentQuotas>>() {
            @Override
            public AtomicReference<CurrentQuotas> load(QuotaRoot quotaRoot) throws Exception {
                return new AtomicReference<>(quotaCalculator.recalculateCurrentQuotas(quotaRoot, sessionProvider.createSystemSession(Username.of(quotaRoot.getValue()))));
            }
        });
    }

    @Override
    public void increase(QuotaOperation quotaOperation) throws MailboxException {
        updateQuota(quotaOperation.quotaRoot(), quota -> quota.increase(new CurrentQuotas(quotaOperation.count(), quotaOperation.size())));
    }

    @Override
    public void decrease(QuotaOperation quotaOperation) throws MailboxException {
        updateQuota(quotaOperation.quotaRoot(), quota -> quota.decrease(new CurrentQuotas(quotaOperation.count(), quotaOperation.size())));
    }

    @Override
    public QuotaCountUsage getCurrentMessageCount(QuotaRoot quotaRoot) throws MailboxException {
        try {
            return quotaCache.get(quotaRoot).get().count();
        } catch (ExecutionException e) {
            throw new MailboxException("Exception caught", e);
        }
    }

    @Override
    public QuotaSizeUsage getCurrentStorage(QuotaRoot quotaRoot) throws MailboxException {
        try {
            return quotaCache.get(quotaRoot).get().size();
        } catch (ExecutionException e) {
            throw new MailboxException("Exception caught", e);
        }
    }

    private void updateQuota(QuotaRoot quotaRoot, UnaryOperator<CurrentQuotas> quotaFunction) throws MailboxException {
        try {
            quotaCache.get(quotaRoot).updateAndGet(quotaFunction);
        } catch (ExecutionException e) {
            throw new MailboxException("Exception caught", e);
        }
    }
}
