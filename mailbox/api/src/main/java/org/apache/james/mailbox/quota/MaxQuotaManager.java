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

package org.apache.james.mailbox.quota;

import java.util.Map;
import java.util.Optional;

import org.apache.james.core.Domain;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.util.OptionalUtils;

import com.github.fge.lambdas.Throwing;

/**
 * This interface describe how to set the max quotas for users
 * Part of RFC 2087 implementation
 */
public interface MaxQuotaManager {

    /**
     * Method allowing you to set the maximum storage quota for a given user
     *
     * @param quotaRoot Quota root argument from RFC 2087 ( correspond to the user owning this mailbox )
     * @param maxStorageQuota The new storage quota ( in bytes ) for this user
     */
    void setMaxStorage(QuotaRoot quotaRoot, QuotaSize maxStorageQuota) throws MailboxException;

    /**
     * Method allowing you to set the maximum message count allowed for this quotaroot
     *
     * @param quotaRoot Quota root argument from RFC 2087
     * @param maxMessageCount The new message count allowed.
     */
    void setMaxMessage(QuotaRoot quotaRoot, QuotaCount maxMessageCount) throws MailboxException;

    /**
     * Method allowing you to remove the maximum messages count allowed for this quotaroot
     *
     * @param quotaRoot Quota root argument from RFC 2087
     */
    void removeMaxMessage(QuotaRoot quotaRoot) throws MailboxException;

    /**
     * Method allowing you to remove the maximum messages size allowed for this quotaroot
     *
     * @param quotaRoot Quota root argument from RFC 2087
     */
    void removeMaxStorage(QuotaRoot quotaRoot) throws MailboxException;

    /**
     * Method allowing you to set the global maximum storage in bytes.
     *
     * @param globalMaxStorage new global maximum storage
     */
    void setGlobalMaxStorage(QuotaSize globalMaxStorage) throws MailboxException;

    /**
     * Method allowing you to remove the global maximum messages size in bytes.
     */
    void removeGlobalMaxStorage() throws MailboxException;

    /**
     * Method allowing you to set the global maximum message count allowed
     *
     * @param globalMaxMessageCount new global message count
     */
    void setGlobalMaxMessage(QuotaCount globalMaxMessageCount) throws MailboxException;

    /**
     * Method allowing you to remove the global maximum messages count.
     */
    void removeGlobalMaxMessage() throws MailboxException;

    /**
     * Method allowing you to get the global maximum storage in bytes.
     *
     * @return global maximum storage, if defined
     */
    Optional<QuotaSize> getGlobalMaxStorage() throws MailboxException;

    /**
     * Method allowing you to get the global maximum message count allowed
     *
     * @return global maximum message count, if defined
     */
    Optional<QuotaCount> getGlobalMaxMessage() throws MailboxException;

    /**
     * Return the maximum storage which is allowed for the given {@link QuotaRoot} (in fact the user which the session is bound to)
     *
     * The returned valued must be in <strong>bytes</strong>
     *
     * @param quotaRoot Quota root argument from RFC 2087 ( correspond to the user owning this mailbox )
     * @return The maximum storage in bytes if any
     */
    Optional<QuotaSize> getMaxStorage(QuotaRoot quotaRoot) throws MailboxException;

    /**
     * Return the maximum message count which is allowed for the given {@link QuotaRoot} (in fact the user which the session is bound to)
     *
     * @param quotaRoot Quota root argument from RFC 2087 ( correspond to the user owning this mailbox )
     * @return maximum of allowed message count
     */
    Optional<QuotaCount> getMaxMessage(QuotaRoot quotaRoot) throws MailboxException;

    Map<Quota.Scope, QuotaCount> listMaxMessagesDetails(QuotaRoot quotaRoot);

    Map<Quota.Scope, QuotaSize> listMaxStorageDetails(QuotaRoot quotaRoot);

    Optional<QuotaCount> getDomainMaxMessage(Domain domain);

    void setDomainMaxMessage(Domain domain, QuotaCount count) throws MailboxException;

    void removeDomainMaxMessage(Domain domain) throws MailboxException;

    void setDomainMaxStorage(Domain domain, QuotaSize size) throws MailboxException;

    Optional<QuotaSize> getDomainMaxStorage(Domain domain);

    void removeDomainMaxStorage(Domain domain) throws MailboxException;

    default Optional<QuotaCount> getComputedMaxMessage(Domain domain) throws MailboxException {
        return OptionalUtils.orSuppliers(
            Throwing.supplier(() -> getDomainMaxMessage(domain)).sneakyThrow(),
            Throwing.supplier(this::getGlobalMaxMessage).sneakyThrow());
    }

    default Optional<QuotaSize> getComputedMaxStorage(Domain domain) throws MailboxException {
        return OptionalUtils.orSuppliers(
            Throwing.supplier(() -> getDomainMaxStorage(domain)).sneakyThrow(),
            Throwing.supplier(this::getGlobalMaxStorage).sneakyThrow());
    }
}