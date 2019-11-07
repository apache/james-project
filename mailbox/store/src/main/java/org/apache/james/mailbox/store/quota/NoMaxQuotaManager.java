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

package org.apache.james.mailbox.store.quota;

import java.util.Map;
import java.util.Optional;

import org.apache.james.core.Domain;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.MaxQuotaManager;

import com.google.common.collect.ImmutableMap;

/**
 * A Max Quota Manager that simply throws exceptions
 *
 * Intended to be used to disactivate Max Quota admin support
 */
public class NoMaxQuotaManager implements MaxQuotaManager {

    @Override
    public void setMaxStorage(QuotaRoot quotaRoot, QuotaSizeLimit maxStorageQuota) throws MailboxException {
        throw new MailboxException("Operation is not supported");
    }

    @Override
    public void setMaxMessage(QuotaRoot quotaRoot, QuotaCountLimit maxMessageCount) throws MailboxException {
        throw new MailboxException("Operation is not supported");
    }

    @Override
    public void removeMaxMessage(QuotaRoot quotaRoot) throws MailboxException {
        throw new MailboxException("Operation is not supported");
    }

    @Override
    public void removeMaxStorage(QuotaRoot quotaRoot) throws MailboxException {
        throw new MailboxException("Operation is not supported");
    }

    @Override
    public void setDomainMaxMessage(Domain domain, QuotaCountLimit count) throws MailboxException {
        throw new MailboxException("Operation is not supported");
    }

    @Override
    public void setDomainMaxStorage(Domain domain, QuotaSizeLimit size) throws MailboxException {
        throw new MailboxException("Operation is not supported");
    }

    @Override
    public void removeDomainMaxMessage(Domain domain) throws MailboxException {
        throw new MailboxException("Operation is not supported");
    }

    @Override
    public void removeDomainMaxStorage(Domain domain) throws MailboxException {
        throw new MailboxException("Operation is not supported");
    }

    @Override
    public void setGlobalMaxStorage(QuotaSizeLimit globalMaxStorage) throws MailboxException {
        throw new MailboxException("Operation is not supported");
    }

    @Override
    public void removeGlobalMaxStorage() throws MailboxException {
        throw new MailboxException("Operation is not supported");
    }

    @Override
    public void removeGlobalMaxMessage() throws MailboxException {
        throw new MailboxException("Operation is not supported");
    }

    @Override
    public void setGlobalMaxMessage(QuotaCountLimit globalMaxMessageCount) throws MailboxException {
        throw new MailboxException("Operation is not supported");
    }

    @Override
    public Optional<QuotaSizeLimit> getMaxStorage(QuotaRoot quotaRoot) {
        return Optional.empty();
    }

    @Override
    public Optional<QuotaCountLimit> getMaxMessage(QuotaRoot quotaRoot) {
        return Optional.empty();
    }

    @Override
    public Map<Quota.Scope, QuotaCountLimit> listMaxMessagesDetails(QuotaRoot quotaRoot) {
        return ImmutableMap.of();
    }

    @Override
    public Map<Quota.Scope, QuotaSizeLimit> listMaxStorageDetails(QuotaRoot quotaRoot) {
        return ImmutableMap.of();
    }

    @Override
    public Optional<QuotaCountLimit> getDomainMaxMessage(Domain domain) {
        return Optional.empty();
    }

    @Override
    public Optional<QuotaSizeLimit> getDomainMaxStorage(Domain domain) {
        return Optional.empty();
    }

    @Override
    public Optional<QuotaSizeLimit> getGlobalMaxStorage() {
        return Optional.empty();
    }

    @Override
    public Optional<QuotaCountLimit> getGlobalMaxMessage() {
        return Optional.empty();
    }
}
