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

package org.apache.james.mailbox.store;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import jakarta.mail.Flags;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;

public interface MessageFactory<T extends MailboxMessage> {
    T createMessage(MessageId messageId, ThreadId threadId, Mailbox mailbox, Date internalDate, Date saveDate, int size, int bodyStartOctet,
                    Content content, Flags flags, PropertyBuilder propertyBuilder,
                    List<MessageAttachmentMetadata> attachments) throws MailboxException;

    class StoreMessageFactory implements MessageFactory<SimpleMailboxMessage> {
        @Override
        public SimpleMailboxMessage createMessage(MessageId messageId, ThreadId threadId, Mailbox mailbox, Date internalDate, Date saveDate, int size,
                                                  int bodyStartOctet, Content content, Flags flags,
                                                  PropertyBuilder propertyBuilder, List<MessageAttachmentMetadata> attachments) {
            return new SimpleMailboxMessage(messageId, threadId, internalDate, size, bodyStartOctet, content, flags, propertyBuilder.build(),
                mailbox.getMailboxId(), attachments, Optional.of(saveDate));
        }
    }
}
