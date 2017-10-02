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

package org.apache.james.mailbox.store.mail.model;

import static org.apache.james.mailbox.store.mail.model.ListMessageAssert.assertMessages;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class ListMessageAssertTest {
    private static final String BODY_CONTENT2 = "Subject: Test2 \n\nBody2\n.\n";
    private static final String BODY_CONTENT1 = "Subject: Test1 \n\nBody1\n.\n";
    private static final int BODY_START = 16;
    private static final int UID_VALIDITY = 42;
    private static final MailboxId MAILBOX_ID = TestId.of(1);
    private static final MessageUid MESSAGE_UID = MessageUid.of(2);
    private static final MessageId MESSAGE_ID = new DefaultMessageId();
    private static final Date INTERNAL_DATE = new Date();

    private Mailbox benwaInboxMailbox;

    private MailboxMessage message1;
    private MailboxMessage message2;
    
    @Before
    public void setUp() {
        benwaInboxMailbox = createMailbox(MailboxPath.forUser("user", "INBOX"));

        message1 = createMessage(benwaInboxMailbox, MESSAGE_ID, BODY_CONTENT1, BODY_START, new PropertyBuilder());
        message2 = createMessage(benwaInboxMailbox, MESSAGE_ID, BODY_CONTENT2, BODY_START, new PropertyBuilder());
    }

    @Test
    public void containsOnlyShouldWork() throws IOException {
        List<MailboxMessage> actual = ImmutableList.of(message1, message2);
        assertMessages(actual).containOnly(createMailboxMessage(MAILBOX_ID, MESSAGE_ID, MESSAGE_UID, INTERNAL_DATE, BODY_CONTENT1, BODY_START, new PropertyBuilder()),
                createMailboxMessage(MAILBOX_ID, MESSAGE_ID, MESSAGE_UID, INTERNAL_DATE, BODY_CONTENT2, BODY_START, new PropertyBuilder()));
    }

    @Test(expected = AssertionError.class)
    public void containsOnlyShouldThrowExceptionWhenHavingElementDoesNotBelongToList() throws IOException {
        List<MailboxMessage> actual = ImmutableList.of(message1);
        assertMessages(actual).containOnly(createMailboxMessage(MAILBOX_ID, MESSAGE_ID, MESSAGE_UID, INTERNAL_DATE, BODY_CONTENT2, BODY_START, new PropertyBuilder()));
    }

    private MailboxMessage createMailboxMessage(final MailboxId mailboxId, final MessageId messageId, final MessageUid uid,
            final Date internalDate, final String content, final int bodyStart, final PropertyBuilder propertyBuilder) {
        return new MailboxMessage() {
            @Override
            public ComposedMessageIdWithMetaData getComposedMessageIdWithMetaData() {
                return ComposedMessageIdWithMetaData.builder()
                    .modSeq(getModSeq())
                    .flags(createFlags())
                    .composedMessageId(new ComposedMessageId(mailboxId, getMessageId(), uid))
                    .build();
            }

            @Override
            public MailboxId getMailboxId() {
                return mailboxId;
            }

            @Override
            public MessageUid getUid() {
                return uid;
            }

            @Override
            public void setUid(MessageUid uid) {

            }

            @Override
            public void setModSeq(long modSeq) {

            }

            @Override
            public long getModSeq() {
                return 0;
            }

            @Override
            public boolean isAnswered() {
                return false;
            }

            @Override
            public boolean isDeleted() {
                return false;
            }

            @Override
            public boolean isDraft() {
                return false;
            }

            @Override
            public boolean isFlagged() {
                return false;
            }

            @Override
            public boolean isRecent() {
                return false;
            }

            @Override
            public boolean isSeen() {
                return false;
            }

            @Override
            public void setFlags(Flags flags) {

            }

            @Override
            public Flags createFlags() {
                return null;
            }

            @Override
            public int compareTo(MailboxMessage o) {
                return 0;
            }

            @Override
            public long getHeaderOctets() {
                return bodyStart;
            }

            @Override
            public MessageId getMessageId() {
                return messageId;
            }

            @Override
            public Date getInternalDate() {
                return internalDate;
            }

            @Override
            public InputStream getBodyContent() throws IOException {
                return null;
            }

            @Override
            public String getMediaType() {
                return null;
            }

            @Override
            public String getSubType() {
                return null;
            }

            @Override
            public long getBodyOctets() {
                return content.length() - bodyStart;
            }

            @Override
            public long getFullContentOctets() {
                return content.length();
            }

            @Override
            public Long getTextualLineCount() {
                return null;
            }

            @Override
            public InputStream getHeaderContent() throws IOException {
                return null;
            }

            @Override
            public InputStream getFullContent() throws IOException {
                return new SharedByteArrayInputStream(content.getBytes());
            }

            @Override
            public List<Property> getProperties() {
                return null;
            }

            @Override
            public List<MessageAttachment> getAttachments() {
                return null;
            }
        };
    }

    private SimpleMailbox createMailbox(MailboxPath mailboxPath) {
        SimpleMailbox mailbox = new SimpleMailbox(mailboxPath, UID_VALIDITY);
        mailbox.setMailboxId(MAILBOX_ID);

        return mailbox;
    }

    private MailboxMessage createMessage(Mailbox mailbox, MessageId messageId, String content, int bodyStart, PropertyBuilder propertyBuilder) {
        SimpleMailboxMessage simpleMailboxMessage = new SimpleMailboxMessage(messageId, INTERNAL_DATE, content.length(), bodyStart, new SharedByteArrayInputStream(content.getBytes()), new Flags(), propertyBuilder, mailbox.getMailboxId());
        simpleMailboxMessage.setUid(MESSAGE_UID);
        return simpleMailboxMessage;
    }

}
