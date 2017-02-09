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

package org.apache.james.mailbox.elasticsearch.json;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.elasticsearch.IndexAttachments;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.Property;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Multimap;

public class IndexableMessageWithMessageId extends IndexableMessage {

    public static IndexableMessage from(MailboxMessage message, List<MailboxSession.User> users, TextExtractor textExtractor,
                                        ZoneId zoneId, IndexAttachments indexAttachments) {
        IndexableMessage indexableMessage = IndexableMessage.from(message, users, textExtractor, zoneId, indexAttachments);
        return new IndexableMessageWithMessageId(indexableMessage.getUid(), indexableMessage.getMailboxId(), indexableMessage.getUsers(),
            indexableMessage.getModSeq(), indexableMessage.getSize(), indexableMessage.getDate(), indexableMessage.getMediaType(),
            indexableMessage.getSubType(), indexableMessage.isUnRead(), indexableMessage.isRecent(), indexableMessage.isFlagged(),
            indexableMessage.isDeleted(), indexableMessage.isDraft(), indexableMessage.isAnswered(), indexableMessage.getUserFlags(),
            indexableMessage.getHeaders(), indexableMessage.getFrom(), indexableMessage.getTo(), indexableMessage.getCc(), indexableMessage.getBcc(),
            indexableMessage.getReplyTo(), indexableMessage.getSubjects(), indexableMessage.getSentDate(), indexableMessage.getProperties(),
            indexableMessage.getAttachments(), indexableMessage.getHasAttachment(), indexableMessage.getBodyText(), indexableMessage.getBodyHtml(), indexableMessage.getText(),
            message.getMessageId().serialize());
    }

    private String messageId;

    public IndexableMessageWithMessageId(long uid, String mailboxId, List<String> users, long modSeq, long size, String date,
                                         String mediaType, String subType, boolean isUnRead, boolean isRecent, boolean isFlagged,
                                         boolean isDeleted, boolean isDraft, boolean isAnswered, String[] userFlags, Multimap<String, String> headers,
                                         EMailers from, EMailers to, EMailers cc, EMailers bcc, EMailers replyTo, Subjects subjects,
                                         String sentDate, List<Property> properties, List<MimePart> attachments, boolean hasAttachments, Optional<String> bodyText,
                                         Optional<String> bodyHtml, String text, String messageId) {
        super(uid, mailboxId, users, modSeq, size, date, mediaType, subType, isUnRead, isRecent, isFlagged, isDeleted,
            isDraft, isAnswered, userFlags, headers, from, to, cc, bcc, replyTo, subjects, sentDate, properties, attachments, hasAttachments,
            bodyText, bodyHtml, text);
        this.messageId = messageId;
    }

    public IndexableMessageWithMessageId(String messageId) {
        this.messageId = messageId;
    }

    @JsonProperty(JsonMessageConstants.MESSAGE_ID)
    public String getMessageId() {
        return messageId;
    }
}
