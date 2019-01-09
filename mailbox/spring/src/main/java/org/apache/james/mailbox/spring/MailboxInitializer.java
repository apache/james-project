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

package org.apache.james.mailbox.spring;

import javax.inject.Inject;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.SessionProvider;
import org.apache.james.mailbox.store.event.DelegatingMailboxListener;
import org.apache.james.mailbox.store.event.MailboxAnnotationListener;
import org.apache.james.mailbox.store.quota.ListeningCurrentQuotaUpdater;
import org.apache.james.mailbox.store.quota.QuotaUpdater;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.mailbox.store.search.MessageSearchIndex;

public class MailboxInitializer {
    private final SessionProvider sessionProvider;
    private final DelegatingMailboxListener delegatingMailboxListener;
    private final MessageSearchIndex messageSearchIndex;
    private final QuotaUpdater quotaUpdater;
    private final MailboxManager mailboxManager;
    private final MailboxSessionMapperFactory mapperFactory;

    @Inject
    public MailboxInitializer(SessionProvider sessionProvider, DelegatingMailboxListener delegatingMailboxListener, MessageSearchIndex messageSearchIndex, QuotaUpdater quotaUpdater, MailboxManager mailboxManager, MailboxSessionMapperFactory mapperFactory) {
        this.sessionProvider = sessionProvider;
        this.delegatingMailboxListener = delegatingMailboxListener;
        this.messageSearchIndex = messageSearchIndex;
        this.quotaUpdater = quotaUpdater;
        this.mailboxManager = mailboxManager;
        this.mapperFactory = mapperFactory;
    }

    public void init() throws MailboxException {
        MailboxSession session = sessionProvider.createSystemSession("admin");

        if (messageSearchIndex instanceof ListeningMessageSearchIndex) {
            ListeningMessageSearchIndex index = (ListeningMessageSearchIndex) messageSearchIndex;
            delegatingMailboxListener.addGlobalListener(index, session);
        }

        if (quotaUpdater instanceof ListeningCurrentQuotaUpdater) {
            ListeningCurrentQuotaUpdater listeningCurrentQuotaUpdater = (ListeningCurrentQuotaUpdater) quotaUpdater;
            delegatingMailboxListener.addGlobalListener(listeningCurrentQuotaUpdater, session);
        }

        if (mailboxManager.getSupportedMailboxCapabilities().contains(MailboxManager.MailboxCapabilities.Annotation)) {
            delegatingMailboxListener.addGlobalListener(new MailboxAnnotationListener(mapperFactory, sessionProvider), session);
        }
    }
}
