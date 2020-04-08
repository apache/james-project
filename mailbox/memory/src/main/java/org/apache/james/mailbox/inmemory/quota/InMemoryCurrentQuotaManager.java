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
import java.util.concurrent.atomic.AtomicLong;

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

    private final LoadingCache<QuotaRoot, Entry> quotaCache;

    @Inject
    public InMemoryCurrentQuotaManager(CurrentQuotaCalculator quotaCalculator, SessionProvider sessionProvider) {
        this.quotaCache = CacheBuilder.newBuilder().build(new CacheLoader<QuotaRoot, Entry>() {
            @Override
            public Entry load(QuotaRoot quotaRoot) throws Exception {
                return new Entry(quotaCalculator.recalculateCurrentQuotas(quotaRoot, sessionProvider.createSystemSession(Username.of(quotaRoot.getValue()))));
            }
        });
    }

    @Override
    public void increase(QuotaOperation quotaOperation) throws MailboxException {
        updateQuota(quotaOperation.quotaRoot(), quotaOperation.count().asLong(), quotaOperation.size().asLong());
    }

    @Override
    public void decrease(QuotaOperation quotaOperation) throws MailboxException {
        updateQuota(quotaOperation.quotaRoot(), -(quotaOperation.count().asLong()), -(quotaOperation.size().asLong()));
    }

    @Override
    public QuotaCountUsage getCurrentMessageCount(QuotaRoot quotaRoot) throws MailboxException {
        try {
            return QuotaCountUsage.count(quotaCache.get(quotaRoot).getCount().get());
        } catch (ExecutionException e) {
            throw new MailboxException("Exception caught", e);
        }
    }

    @Override
    public QuotaSizeUsage getCurrentStorage(QuotaRoot quotaRoot) throws MailboxException {
        try {
            return QuotaSizeUsage.size(quotaCache.get(quotaRoot).getSize().get());
        } catch (ExecutionException e) {
            throw new MailboxException("Exception caught", e);
        }
    }

    private void updateQuota(QuotaRoot quotaRoot, long count, long size) throws MailboxException {
        try {
            Entry entry = quotaCache.get(quotaRoot);
            entry.getCount().addAndGet(count);
            entry.getSize().addAndGet(size);
        } catch (ExecutionException e) {
            throw new MailboxException("Exception caught", e);
        }
    }

    static class Entry {
        private final AtomicLong count;
        private final AtomicLong size;

        public Entry(CurrentQuotas currentQuotas) {
            this.count = new AtomicLong(currentQuotas.count().asLong());
            this.size = new AtomicLong(currentQuotas.size().asLong());
        }

        public AtomicLong getCount() {
            return count;
        }

        public AtomicLong getSize() {
            return size;
        }
    }
}
