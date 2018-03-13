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

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;

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
     * Method allowing you to set the default maximum storage in bytes.
     *
     * @param defaultMaxStorage new default maximum storage
     */
    void setDefaultMaxStorage(QuotaSize defaultMaxStorage) throws MailboxException;

    /**
     * Method allowing you to remove the default maximum messages size in bytes.
     */
    void removeDefaultMaxStorage() throws MailboxException;

    /**
     * Method allowing you to set the default maximum message count allowed
     *
     * @param defaultMaxMessageCount new default message count
     */
    void setDefaultMaxMessage(QuotaCount defaultMaxMessageCount) throws MailboxException;

    /**
     * Method allowing you to remove the default maximum messages count.
     */
    void removeDefaultMaxMessage() throws MailboxException;

    /**
     * Method allowing you to get the default maximum storage in bytes.
     *
     * @return default maximum storage, if defined
     */
    Optional<QuotaSize> getDefaultMaxStorage() throws MailboxException;

    /**
     * Method allowing you to get the default maximum message count allowed
     *
     * @return default maximum message count, if defined
     */
    Optional<QuotaCount> getDefaultMaxMessage() throws MailboxException;

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
}