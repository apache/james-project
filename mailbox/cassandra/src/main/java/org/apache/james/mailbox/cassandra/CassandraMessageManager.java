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

package org.apache.james.mailbox.cassandra;

import java.util.List;

import javax.mail.Flags;

import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.BatchSizes;
import org.apache.james.mailbox.store.ImmutableMailboxMessage;
import org.apache.james.mailbox.store.StoreMessageManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.mailbox.store.search.MessageSearchIndex;

import com.github.steveash.guavate.Guavate;

/**
 * Cassandra implementation of {@link StoreMessageManager}
 * 
 */
public class CassandraMessageManager extends StoreMessageManager {

    private CassandraMailboxSessionMapperFactory mapperFactory;

    public CassandraMessageManager(CassandraMailboxSessionMapperFactory mapperFactory, MessageSearchIndex index,
                                   MailboxEventDispatcher dispatcher, MailboxPathLocker locker, Mailbox mailbox, QuotaManager quotaManager,
                                   QuotaRootResolver quotaRootResolver, MessageParser messageParser, MessageId.Factory messageIdFactory,
                                   BatchSizes batchSizes, ImmutableMailboxMessage.Factory immutableMailboxMessageFactory,
                                   StoreRightManager storeRightManager) throws MailboxException {
        super(mapperFactory, index, dispatcher, locker, mailbox,
            quotaManager, quotaRootResolver, messageParser, messageIdFactory, batchSizes, immutableMailboxMessageFactory, storeRightManager);

        this.mapperFactory = mapperFactory;
    }

    /**
     * Support user flags
     */
    @Override
    protected Flags getPermanentFlags(MailboxSession session) {
        Flags flags = super.getPermanentFlags(session);
        flags.add(Flags.Flag.USER);
        return flags;
    }

    @Override
    protected void storeAttachment(final MailboxMessage message, final List<MessageAttachment> messageAttachments, final MailboxSession session) throws MailboxException {
        mapperFactory.getAttachmentMapper(session)
            .storeAttachmentsForMessage(
                messageAttachments.stream()
                    .map(MessageAttachment::getAttachment)
                    .collect(Guavate.toImmutableList()),
                message.getMessageId());
    }

    @Override
    protected MailboxMessage copyMessage(MailboxMessage message) throws MailboxException {
        SimpleMailboxMessage copy = SimpleMailboxMessage.copy(message.getMailboxId(), message);
        copy.setUid(message.getUid());
        copy.setModSeq(message.getModSeq());
        return copy;
    }
}
