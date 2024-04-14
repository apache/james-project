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

package org.apache.james.transport.matchers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

/**
 * This matcher will check if the incoming email will make recipients exceed their quotas.
 *
 * Quota are managed directly by the mailbox. Performance will depend on your implementation (Cassandra and JPA maintains counts, thus it is a fast operation).
 *
 * Here is a configuration example:
 *
 * <pre><code>
 * &lt;mailet match=&quot;IsOverQuota&quot; class=&quot;&lt;any-class&gt;&quot;/&gt;
 * </code></pre>
 *
 * Read the <a href="http://james.apache.org/server/manage-cli.html">CLI documentation on how to configure quota</a>. Note:
 * managing quotas can also be done through <a href="http://james.apache.org/server/manage-webadmin.html">WebAdmin</a>.
 */
public class IsOverQuota extends GenericMatcher {

    private static final int SINGLE_EMAIL = 1;

    private final QuotaRootResolver quotaRootResolver;
    private final QuotaManager quotaManager;
    private final UsersRepository usersRepository;

    @Inject
    public IsOverQuota(QuotaRootResolver quotaRootResolver, QuotaManager quotaManager, UsersRepository usersRepository) {
        this.quotaRootResolver = quotaRootResolver;
        this.quotaManager = quotaManager;
        this.usersRepository = usersRepository;
    }

    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        try {
            List<MailAddress> result = new ArrayList<>();
            for (MailAddress mailAddress : mail.getRecipients()) {
                Username userName = usersRepository.getUsername(mailAddress);
                MailboxPath mailboxPath = MailboxPath.inbox(userName);
                QuotaRoot quotaRoot = quotaRootResolver.getQuotaRoot(mailboxPath);
                QuotaManager.Quotas quotas = quotaManager.getQuotas(quotaRoot);

                if (quotas.getMessageQuota().isOverQuotaWithAdditionalValue(SINGLE_EMAIL) ||
                    quotas.getStorageQuota().isOverQuotaWithAdditionalValue(mail.getMessageSize())) {
                    result.add(mailAddress);
                }
            }
            return result;
        } catch (MailboxException e) {
            throw new MessagingException("Exception while checking quotas", e);
        } catch (UsersRepositoryException e) {
            throw new MessagingException("Exception while retrieving username", e);
        }
    }
}
