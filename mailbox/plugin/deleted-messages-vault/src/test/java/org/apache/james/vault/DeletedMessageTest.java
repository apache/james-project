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

package org.apache.james.vault;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.core.User;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class DeletedMessageTest {
    private static final InMemoryMessageId MESSAGE_ID = InMemoryMessageId.of(42);
    private static final InMemoryId MAILBOX_ID_1 = InMemoryId.of(43);
    private static final InMemoryId MAILBOX_ID_2 = InMemoryId.of(44);
    private static final User USER = User.fromUsername("bob@apache.org");
    private static final ZonedDateTime DELIVERY_DATE = ZonedDateTime.parse("2014-10-30T14:12:00Z");
    private static final ZonedDateTime DELETION_DATE = ZonedDateTime.parse("2015-10-30T14:12:00Z");
    private static final byte[] CONTENT = "header: value\r\n\r\ncontent".getBytes(StandardCharsets.UTF_8);
    private static final String SUBJECT = "subject";

    @Test
    void deletedMessageShouldMatchBeanContract() {
        EqualsVerifier.forClass(DeletedMessage.class)
            .verify();
    }

    @Test
    void buildShouldReturnDeletedMessageWithAllCompulsoryFields() throws Exception {
        MaybeSender sender = MaybeSender.of(new MailAddress("bob@apache.org"));
        MailAddress recipient1 = new MailAddress("alice@apache.org");
        MailAddress recipient2 = new MailAddress("cedric@apache.org");
        DeletedMessage deletedMessage = DeletedMessage.builder()
            .messageId(MESSAGE_ID)
            .originMailboxes(MAILBOX_ID_1, MAILBOX_ID_2)
            .user(USER)
            .deliveryDate(DELIVERY_DATE)
            .deletionDate(DELETION_DATE)
            .content(() -> new ByteArrayInputStream(CONTENT))
            .sender(sender)
            .recipients(recipient1, recipient2)
            .hasAttachment(false)
            .build();

        SoftAssertions.assertSoftly(
            soft -> {
                soft.assertThat(deletedMessage.getMessageId()).isEqualTo(MESSAGE_ID);
                soft.assertThat(deletedMessage.getOriginMailboxes()).containsOnly(MAILBOX_ID_1, MAILBOX_ID_2);
                soft.assertThat(deletedMessage.getOwner()).isEqualTo(USER);
                soft.assertThat(deletedMessage.getDeliveryDate()).isEqualTo(DELIVERY_DATE);
                soft.assertThat(deletedMessage.getDeletionDate()).isEqualTo(DELETION_DATE);
                soft.assertThat(deletedMessage.getContent().get()).hasSameContentAs(new ByteArrayInputStream(CONTENT));
                soft.assertThat(deletedMessage.getSender()).isEqualTo(sender);
                soft.assertThat(deletedMessage.getRecipients()).containsOnly(recipient1, recipient2);
                soft.assertThat(deletedMessage.hasAttachment()).isFalse();
                soft.assertThat(deletedMessage.getSubject()).isEmpty();
            }
        );
    }

    @Test
    void buildShouldReturnDeletedMessageWithSubject() throws Exception {
        MaybeSender sender = MaybeSender.of(new MailAddress("bob@apache.org"));
        MailAddress recipient1 = new MailAddress("alice@apache.org");
        MailAddress recipient2 = new MailAddress("cedric@apache.org");
        DeletedMessage deletedMessage = DeletedMessage.builder()
            .messageId(MESSAGE_ID)
            .originMailboxes(MAILBOX_ID_1, MAILBOX_ID_2)
            .user(USER)
            .deliveryDate(DELIVERY_DATE)
            .deletionDate(DELETION_DATE)
            .content(() -> new ByteArrayInputStream(CONTENT))
            .sender(sender)
            .recipients(recipient1, recipient2)
            .hasAttachment(false)
            .subject(SUBJECT)
            .build();

        assertThat(deletedMessage.getSubject()).contains(SUBJECT);
    }
}