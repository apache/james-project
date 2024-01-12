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

package org.apache.james.mailbox.inmemory.mail;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MapperProvider;
import org.apache.james.mailbox.store.mail.model.MessageUidProvider;
import org.apache.james.utils.UpdatableTickingClock;

import com.google.common.collect.ImmutableList;

public class InMemoryMapperProvider implements MapperProvider {

    private static final Username USER = Username.of("user");
    private static final MailboxSession MAILBOX_SESSION = MailboxSessionUtil.create(USER);

    private final MessageId.Factory messageIdFactory;
    private final MessageUidProvider messageUidProvider;
    private final InMemoryMailboxSessionMapperFactory inMemoryMailboxSessionMapperFactory;
    private final UpdatableTickingClock clock;


    public InMemoryMapperProvider() {
        messageIdFactory = new InMemoryMessageId.Factory();
        messageUidProvider = new MessageUidProvider();
        clock = new UpdatableTickingClock(Instant.now());
        inMemoryMailboxSessionMapperFactory = new InMemoryMailboxSessionMapperFactory(clock);
    }

    @Override
    public MailboxMapper createMailboxMapper() throws MailboxException {
        return inMemoryMailboxSessionMapperFactory.createMailboxMapper(MAILBOX_SESSION);
    }

    @Override
    public MessageMapper createMessageMapper() throws MailboxException {
        return inMemoryMailboxSessionMapperFactory.createMessageMapper(MailboxSessionUtil.create(USER));
    }

    @Override
    public MessageIdMapper createMessageIdMapper() throws MailboxException {
        return new InMemoryMessageIdMapper(
            inMemoryMailboxSessionMapperFactory.createMailboxMapper(MAILBOX_SESSION),
            inMemoryMailboxSessionMapperFactory.createMessageMapper(MAILBOX_SESSION));
    }

    @Override
    public AttachmentMapper createAttachmentMapper() throws MailboxException {
        return inMemoryMailboxSessionMapperFactory.createAttachmentMapper(MAILBOX_SESSION);
    }

    @Override
    public InMemoryId generateId() {
        return InMemoryId.of(ThreadLocalRandom.current().nextInt());
    }

    @Override
    public MessageUid generateMessageUid(Mailbox mailbox) {
        return messageUidProvider.next();
    }

    @Override
    public boolean supportPartialAttachmentFetch() {
        return false;
    }
    
    @Override
    public MessageId generateMessageId() {
        return messageIdFactory.generate();
    }

    @Override
    public List<Capabilities> getSupportedCapabilities() {
        return ImmutableList.of(
            Capabilities.MESSAGE,
            Capabilities.MAILBOX,
            Capabilities.ATTACHMENT,
            Capabilities.ANNOTATION,
            Capabilities.MOVE,
            Capabilities.ACL_STORAGE,
            Capabilities.UNIQUE_MESSAGE_ID);
    }

    @Override
    public ModSeq generateModSeq(Mailbox mailbox) throws MailboxException {
        return inMemoryMailboxSessionMapperFactory.getModSeqProvider(null)
                .nextModSeq(mailbox);
    }

    @Override
    public ModSeq highestModSeq(Mailbox mailbox) throws MailboxException {
        return inMemoryMailboxSessionMapperFactory.getModSeqProvider(null)
            .highestModSeq(mailbox);
    }

    public UpdatableTickingClock getClock() {
        return clock;
    }
}
