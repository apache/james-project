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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaCount;
import org.apache.james.mailbox.quota.QuotaSize;

import com.github.steveash.guavate.Guavate;

public class InMemoryPerUserMaxQuotaManager implements MaxQuotaManager {

    private Optional<QuotaCount> maxMessage = Optional.empty();
    private Optional<QuotaSize> maxStorage = Optional.empty();

    private final Map<String, QuotaCount> domainMaxMessage = new ConcurrentHashMap<>();
    private final Map<String, QuotaSize> domainMaxStorage = new ConcurrentHashMap<>();

    private final Map<String, QuotaSize> userMaxStorage = new ConcurrentHashMap<>();
    private final Map<String, QuotaCount> userMaxMessage = new ConcurrentHashMap<>();

    @Override
    public void setDefaultMaxStorage(QuotaSize maxStorage) throws MailboxException {
        this.maxStorage = Optional.of(maxStorage);
    }

    @Override
    public void setDomainMaxMessage(String domain, QuotaCount count) {
        domainMaxMessage.put(domain, count);
    }

    @Override
    public void setDomainMaxStorage(String domain, QuotaSize size) {
        domainMaxStorage.put(domain, size);
    }

    @Override
    public void removeDomainMaxMessage(String domain) throws MailboxException {
        domainMaxMessage.remove(domain);
    }

    @Override
    public void removeDomainMaxStorage(String domain) {
        domainMaxStorage.remove(domain);
    }

    @Override
    public Optional<QuotaSize> getMaxStorage(QuotaRoot quotaRoot) throws MailboxException {
        QuotaSize max = userMaxStorage.get(quotaRoot.getValue());
        if (max == null) {
            return maxStorage;
        }
        return Optional.of(max);
    }

    @Override
    public Optional<QuotaCount> getMaxMessage(QuotaRoot quotaRoot) throws MailboxException {
        QuotaCount max = userMaxMessage.get(quotaRoot.getValue());
        if (max == null) {
            return maxMessage;
        }
        return Optional.of(max);
    }

    @Override
    public Map<Quota.Scope, QuotaCount> listMaxMessagesDetails(QuotaRoot quotaRoot) {
        return Stream.of(
                Pair.of(Quota.Scope.User, Optional.ofNullable(userMaxMessage.get(quotaRoot.getValue()))),
                Pair.of(Quota.Scope.Global, maxMessage))
            .filter(pair -> pair.getValue().isPresent())
            .collect(Guavate.toImmutableMap(Pair::getKey, value -> value.getValue().get()));
    }

    @Override
    public Map<Quota.Scope, QuotaSize> listMaxStorageDetails(QuotaRoot quotaRoot) {
        return Stream.of(
            Pair.of(Quota.Scope.User, Optional.ofNullable(userMaxStorage.get(quotaRoot.getValue()))),
            Pair.of(Quota.Scope.Global, maxStorage))
            .filter(pair -> pair.getValue().isPresent())
            .collect(Guavate.toImmutableMap(Pair::getKey, value -> value.getValue().get()));
    }

    @Override
    public void setDefaultMaxMessage(QuotaCount maxMessage) throws MailboxException {
        this.maxMessage = Optional.of(maxMessage);
    }

    @Override
    public Optional<QuotaCount> getDomainMaxMessage(String domain) {
        return Optional.ofNullable(domainMaxMessage.get(domain));
    }

    @Override
    public Optional<QuotaSize> getDomainMaxStorage(String domain) {
        return Optional.ofNullable(domainMaxStorage.get(domain));
    }

    @Override
    public void setMaxStorage(QuotaRoot user, QuotaSize maxStorageQuota) {
        userMaxStorage.put(user.getValue(), maxStorageQuota);
    }

    @Override
    public void setMaxMessage(QuotaRoot quotaRoot, QuotaCount maxMessageCount) {
        userMaxMessage.put(quotaRoot.getValue(), maxMessageCount);
    }

    @Override
    public Optional<QuotaSize> getDefaultMaxStorage() throws MailboxException {
        return maxStorage;
    }

    @Override
    public Optional<QuotaCount> getDefaultMaxMessage() throws MailboxException {
        return maxMessage;
    }

    @Override
    public void removeMaxMessage(QuotaRoot quotaRoot) throws MailboxException {
        userMaxMessage.remove(quotaRoot.getValue());
    }

    @Override
    public void removeMaxStorage(QuotaRoot quotaRoot) throws MailboxException {
        userMaxStorage.remove(quotaRoot.getValue());
    }

    @Override
    public void removeDefaultMaxStorage() throws MailboxException {
        maxStorage = Optional.empty();
    }

    @Override
    public void removeDefaultMaxMessage() throws MailboxException {
        maxMessage = Optional.empty();
    }
}
