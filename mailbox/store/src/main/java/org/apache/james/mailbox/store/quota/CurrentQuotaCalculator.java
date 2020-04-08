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

import static org.apache.james.mailbox.store.mail.AbstractMessageMapper.UNLIMITED;

import java.util.Iterator;
import java.util.List;

import javax.inject.Inject;

import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

public class CurrentQuotaCalculator {

    private final MailboxSessionMapperFactory factory;
    private final QuotaRootResolver quotaRootResolver;

    @Inject
    public CurrentQuotaCalculator(MailboxSessionMapperFactory factory,
                                  QuotaRootResolver quotaRootResolver) {
        this.factory = factory;
        this.quotaRootResolver = quotaRootResolver;
    }

    public CurrentQuotas recalculateCurrentQuotas(QuotaRoot quotaRoot, MailboxSession session) throws MailboxException {
        List<Mailbox> mailboxes = quotaRootResolver.retrieveAssociatedMailboxes(quotaRoot, session);
        MessageMapper mapper = factory.getMessageMapper(session);
        long messagesSizes = 0;
        long messageCount = 0;
        for (Mailbox mailbox : mailboxes) {
            Iterator<MailboxMessage> messages = mapper.findInMailbox(mailbox, MessageRange.all(), MessageMapper.FetchType.Metadata, UNLIMITED);
            messageCount += mapper.countMessagesInMailbox(mailbox);
            while (messages.hasNext()) {
                messagesSizes +=  messages.next().getFullContentOctets();
            }
        }
        return new CurrentQuotas(QuotaCountUsage.count(messageCount), QuotaSizeUsage.size(messagesSizes));
    }

    public static class CurrentQuotas {
        private final QuotaCountUsage count;
        private final QuotaSizeUsage size;

        public CurrentQuotas(QuotaCountUsage count, QuotaSizeUsage size) {
            this.count = count;
            this.size = size;
        }

        public QuotaCountUsage count() {
            return count;
        }

        public QuotaSizeUsage size() {
            return size;
        }
    }

}