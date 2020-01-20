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
import javax.mail.internet.SharedInputStream;

import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ParsedAttachment;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.BatchSizes;
import org.apache.james.mailbox.store.MessageFactory;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.apache.james.mailbox.store.StoreMessageManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.search.MessageSearchIndex;

/**
 * Cassandra implementation of {@link StoreMessageManager}
 * 
 */
public class CassandraMessageManager extends StoreMessageManager {

    private CassandraMailboxSessionMapperFactory mapperFactory;

    CassandraMessageManager(CassandraMailboxSessionMapperFactory mapperFactory, MessageSearchIndex index,
                            EventBus eventBus, MailboxPathLocker locker, Mailbox mailbox, QuotaManager quotaManager,
                            QuotaRootResolver quotaRootResolver, MessageParser messageParser, MessageId.Factory messageIdFactory,
                            BatchSizes batchSizes,
                            StoreRightManager storeRightManager,
                            PreDeletionHooks preDeletionHooks) {
        super(CassandraMailboxManager.MESSAGE_CAPABILITIES, mapperFactory, index, eventBus, locker, mailbox,
            quotaManager, quotaRootResolver, messageParser, messageIdFactory, batchSizes, storeRightManager,
            preDeletionHooks, new MessageFactory.StoreMessageFactory());

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
    protected List<MessageAttachment> storeAttachments(MessageId messageId, SharedInputStream content, MailboxSession session) throws MailboxException {
        List<ParsedAttachment> attachments = extractAttachments(content);
        return mapperFactory.getAttachmentMapper(session)
            .storeAttachmentsForMessage(attachments, messageId);
    }

}
