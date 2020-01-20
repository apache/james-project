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

import java.util.List;

import javax.mail.internet.SharedInputStream;

import org.apache.james.mailbox.AttachmentStorer;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ParsedAttachment;
import org.apache.james.mailbox.store.mail.AttachmentMapperFactory;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

public class StoreAttachmentStorer implements AttachmentStorer {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoreAttachmentStorer.class);

    private final AttachmentMapperFactory mapperFactory;
    private final MessageParser messageParser;

    public StoreAttachmentStorer(AttachmentMapperFactory mapperFactory, MessageParser messageParser) {
        this.mapperFactory = mapperFactory;
        this.messageParser = messageParser;
    }

    @Override
    public List<MessageAttachment> storeAttachments(MessageId messageId, SharedInputStream messageContent, MailboxSession session) throws MailboxException {
        List<ParsedAttachment> attachments = extractAttachments(messageContent);
        return mapperFactory.getAttachmentMapper(session)
            .storeAttachmentsForMessage(attachments, messageId);
    }

    private List<ParsedAttachment> extractAttachments(SharedInputStream contentIn) {
        try {
            return messageParser.retrieveAttachments(contentIn.newStream(0, -1));
        } catch (Exception e) {
            LOGGER.warn("Error while parsing mail's attachments: {}", e.getMessage(), e);
            return ImmutableList.of();
        }
    }
}
