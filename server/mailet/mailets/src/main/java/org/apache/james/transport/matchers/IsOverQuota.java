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

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.GenericMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.mail.MessagingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class IsOverQuota extends GenericMatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(IsOverQuota.class);
    private static final int SINGLE_EMAIL = 1;

    private final QuotaRootResolver quotaRootResolver;
    private final QuotaManager quotaManager;
    private final MailboxManager mailboxManager;
    private final UsersRepository usersRepository;

    @Inject
    public IsOverQuota(QuotaRootResolver quotaRootResolver, QuotaManager quotaManager, MailboxManager mailboxManager, UsersRepository usersRepository) {
        this.quotaRootResolver = quotaRootResolver;
        this.quotaManager = quotaManager;
        this.mailboxManager = mailboxManager;
        this.usersRepository = usersRepository;
    }

    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        try {
            List<MailAddress> result = new ArrayList<MailAddress>();
            for (MailAddress mailAddress : mail.getRecipients()) {
                String userName = usersRepository.getUser(mailAddress);
                MailboxSession mailboxSession = mailboxManager.createSystemSession(userName, LOGGER);
                MailboxPath mailboxPath = MailboxPath.inbox(mailboxSession);
                QuotaRoot quotaRoot = quotaRootResolver.getQuotaRoot(mailboxPath);

                if (quotaManager.getMessageQuota(quotaRoot).isOverQuotaWithAdditionalValue(SINGLE_EMAIL) ||
                    quotaManager.getStorageQuota(quotaRoot).isOverQuotaWithAdditionalValue(mail.getMessageSize())) {
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
