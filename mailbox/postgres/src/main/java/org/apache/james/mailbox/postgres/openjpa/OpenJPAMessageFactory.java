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

package org.apache.james.mailbox.postgres.openjpa;

import java.util.Date;
import java.util.List;

import javax.mail.Flags;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.postgres.mail.model.JPAMailbox;
import org.apache.james.mailbox.postgres.mail.model.openjpa.AbstractJPAMailboxMessage;
import org.apache.james.mailbox.postgres.mail.model.openjpa.JPAMailboxMessage;
import org.apache.james.mailbox.store.MessageFactory;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;

public class OpenJPAMessageFactory implements MessageFactory<AbstractJPAMailboxMessage> {
    private final AdvancedFeature feature;

    public OpenJPAMessageFactory(AdvancedFeature feature) {
        this.feature = feature;
    }

    public enum AdvancedFeature {
        None,
        Streaming,
        Encryption
    }

    @Override
    public AbstractJPAMailboxMessage createMessage(MessageId messageId, ThreadId threadId, Mailbox mailbox, Date internalDate, Date saveDate, int size, int bodyStartOctet, Content content, Flags flags, PropertyBuilder propertyBuilder, List<MessageAttachmentMetadata> attachments) throws MailboxException {
        return new JPAMailboxMessage(JPAMailbox.from(mailbox), internalDate, size, flags, content, bodyStartOctet, propertyBuilder);
    }
}
