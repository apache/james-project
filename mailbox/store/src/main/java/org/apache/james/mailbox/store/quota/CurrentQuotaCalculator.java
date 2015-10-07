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

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.Message;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Iterator;
import java.util.List;

@Singleton
public class CurrentQuotaCalculator {

    private final MailboxSessionMapperFactory factory;
    private final QuotaRootResolver quotaRootResolver;

    @Inject
    public CurrentQuotaCalculator(MailboxSessionMapperFactory factory,
                                  QuotaRootResolver quotaRootResolver) {
        this.factory = factory;
        this.quotaRootResolver = quotaRootResolver;
    }

    @SuppressWarnings("unchecked")
    public CurrentQuotas recalculateCurrentQuotas(QuotaRoot quotaRoot, MailboxSession session) throws MailboxException {
        List<Mailbox> mailboxes = retrieveMailboxes(quotaRoot, session);
        MessageMapper mapper = factory.getMessageMapper(session);
        long messagesSizes = 0;
        long messageCount = 0;
        for (Mailbox mailbox : mailboxes) {
            Iterator<Message> messages = mapper.findInMailbox(mailbox, MessageRange.all(), MessageMapper.FetchType.Metadata, -1);
            messageCount += mapper.countMessagesInMailbox(mailbox);
            while(messages.hasNext()) {
                messagesSizes +=  messages.next().getFullContentOctets();
            }
        }
        return new CurrentQuotas(messageCount, messagesSizes);
    }

    private List<Mailbox> retrieveMailboxes(QuotaRoot quotaRoot, MailboxSession session) throws MailboxException {
        List<MailboxPath> paths = quotaRootResolver.retrieveAssociatedMailboxes(quotaRoot, session);
        final MailboxMapper mapper = factory.getMailboxMapper(session);
        return Lists.transform(paths, new Function<MailboxPath, Mailbox>() {
            @Override
            public Mailbox apply(MailboxPath mailboxPath) {
                try {
                    return mapper.findMailboxByPath(mailboxPath);
                } catch (MailboxException e) {
                    throw Throwables.propagate(e);
                }
            }
        });
    }

    public static class CurrentQuotas {
        private final long count;
        private final long size;

        public CurrentQuotas(long count, long size) {
            this.count = count;
            this.size = size;
        }

        public long getCount() {
            return count;
        }

        public long getSize() {
            return size;
        }
    }

}