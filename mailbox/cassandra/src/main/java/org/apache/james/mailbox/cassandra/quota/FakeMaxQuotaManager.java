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

import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.core.Domain;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

public class FakeMaxQuotaManager implements MaxQuotaManager {
    private static final String MESSAGE = "Use quota compatility mode in cassandra.properties for running the 12 -> 13 migration";

    @Override
    public void setMaxStorage(QuotaRoot quotaRoot, QuotaSizeLimit maxStorageQuota) throws MailboxException {
        throw new NotImplementedException(MESSAGE);
    }

    @Override
    public Publisher<Void> setMaxStorageReactive(QuotaRoot quotaRoot, QuotaSizeLimit maxStorageQuota) {
        return Mono.error(new NotImplementedException(MESSAGE));
    }

    @Override
    public void setMaxMessage(QuotaRoot quotaRoot, QuotaCountLimit maxMessageCount) throws MailboxException {
        throw new NotImplementedException(MESSAGE);
    }

    @Override
    public Publisher<Void> setMaxMessageReactive(QuotaRoot quotaRoot, QuotaCountLimit maxMessageCount) {
        return Mono.error(new NotImplementedException(MESSAGE));
    }

    @Override
    public void removeMaxMessage(QuotaRoot quotaRoot) throws MailboxException {
        throw new NotImplementedException(MESSAGE);
    }

    @Override
    public Publisher<Void> removeMaxMessageReactive(QuotaRoot quotaRoot) {
        return Mono.error(new NotImplementedException(MESSAGE));
    }

    @Override
    public void removeMaxStorage(QuotaRoot quotaRoot) throws MailboxException {
        throw new NotImplementedException(MESSAGE);
    }

    @Override
    public Publisher<Void> removeMaxStorageReactive(QuotaRoot quotaRoot) {
        return Mono.error(new NotImplementedException(MESSAGE));
    }

    @Override
    public void setGlobalMaxStorage(QuotaSizeLimit globalMaxStorage) throws MailboxException {
        throw new NotImplementedException(MESSAGE);
    }

    @Override
    public Publisher<Void> setGlobalMaxStorageReactive(QuotaSizeLimit globalMaxStorage) {
        return Mono.error(new NotImplementedException(MESSAGE));
    }

    @Override
    public void removeGlobalMaxStorage() throws MailboxException {
        throw new NotImplementedException(MESSAGE);
    }

    @Override
    public Publisher<Void> removeGlobalMaxStorageReactive() {
        return Mono.error(new NotImplementedException(MESSAGE));
    }

    @Override
    public void setGlobalMaxMessage(QuotaCountLimit globalMaxMessageCount) throws MailboxException {
        throw new NotImplementedException(MESSAGE);
    }

    @Override
    public Publisher<Void> setGlobalMaxMessageReactive(QuotaCountLimit globalMaxMessageCount) {
        return Mono.error(new NotImplementedException(MESSAGE));
    }

    @Override
    public void removeGlobalMaxMessage() throws MailboxException {
        throw new NotImplementedException(MESSAGE);
    }

    @Override
    public Publisher<Void> removeGlobalMaxMessageReactive() {
        return Mono.error(new NotImplementedException(MESSAGE));
    }

    @Override
    public Optional<QuotaSizeLimit> getGlobalMaxStorage() throws MailboxException {
        return Optional.empty();
    }

    @Override
    public Publisher<QuotaSizeLimit> getGlobalMaxStorageReactive() {
        return Mono.error(new NotImplementedException(MESSAGE));
    }

    @Override
    public Optional<QuotaCountLimit> getGlobalMaxMessage() throws MailboxException {
        return Optional.empty();
    }

    @Override
    public Publisher<QuotaCountLimit> getGlobalMaxMessageReactive() {
        return Mono.error(new NotImplementedException(MESSAGE));
    }

    @Override
    public Map<Quota.Scope, QuotaCountLimit> listMaxMessagesDetails(QuotaRoot quotaRoot) {
        throw new NotImplementedException(MESSAGE);
    }

    @Override
    public Map<Quota.Scope, QuotaSizeLimit> listMaxStorageDetails(QuotaRoot quotaRoot) {
        throw new NotImplementedException(MESSAGE);
    }

    @Override
    public Optional<QuotaCountLimit> getDomainMaxMessage(Domain domain) {
        return Optional.empty();
    }

    @Override
    public Publisher<QuotaCountLimit> getDomainMaxMessageReactive(Domain domain) {
        return Mono.error(new NotImplementedException(MESSAGE));
    }

    @Override
    public void setDomainMaxMessage(Domain domain, QuotaCountLimit count) throws MailboxException {

    }

    @Override
    public Publisher<Void> setDomainMaxMessageReactive(Domain domain, QuotaCountLimit count) {
        return Mono.error(new NotImplementedException(MESSAGE));
    }

    @Override
    public void removeDomainMaxMessage(Domain domain) throws MailboxException {

    }

    @Override
    public Publisher<Void> removeDomainMaxMessageReactive(Domain domain) {
        return Mono.error(new NotImplementedException(MESSAGE));
    }

    @Override
    public void setDomainMaxStorage(Domain domain, QuotaSizeLimit size) throws MailboxException {

    }

    @Override
    public Publisher<Void> setDomainMaxStorageReactive(Domain domain, QuotaSizeLimit size) {
        return Mono.error(new NotImplementedException(MESSAGE));
    }

    @Override
    public Optional<QuotaSizeLimit> getDomainMaxStorage(Domain domain) {
        return Optional.empty();
    }

    @Override
    public Publisher<QuotaSizeLimit> getDomainMaxStorageReactive(Domain domain) {
        return Mono.error(new NotImplementedException(MESSAGE));
    }

    @Override
    public void removeDomainMaxStorage(Domain domain) throws MailboxException {

    }

    @Override
    public Publisher<Void> removeDomainMaxStorageReactive(Domain domain) {
        return Mono.error(new NotImplementedException(MESSAGE));
    }
}
