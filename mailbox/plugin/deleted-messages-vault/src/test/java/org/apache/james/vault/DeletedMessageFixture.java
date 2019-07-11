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

import static org.apache.mailet.base.MailAddressFixture.RECIPIENT1;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT2;
import static org.apache.mailet.base.MailAddressFixture.SENDER;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.james.core.MaybeSender;
import org.apache.james.core.User;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;

public interface DeletedMessageFixture {
    InMemoryMessageId MESSAGE_ID = InMemoryMessageId.of(42);
    InMemoryMessageId OLD_MESSAGE_ID = InMemoryMessageId.of(58);
    InMemoryMessageId MESSAGE_ID_2 = InMemoryMessageId.of(45);
    InMemoryId MAILBOX_ID_1 = InMemoryId.of(43);
    InMemoryId MAILBOX_ID_2 = InMemoryId.of(44);
    InMemoryId MAILBOX_ID_3 = InMemoryId.of(45);
    User USER = User.fromUsername("bob@apache.org");
    User USER_2 = User.fromUsername("dimitri@apache.org");
    ZonedDateTime DELIVERY_DATE = ZonedDateTime.parse("2014-10-30T14:12:00Z");
    ZonedDateTime DELETION_DATE = ZonedDateTime.parse("2015-10-30T14:12:00Z");
    ZonedDateTime NOW = ZonedDateTime.parse("2015-10-30T16:12:00Z");
    ZonedDateTime OLD_DELIVERY_DATE = ZonedDateTime.parse("2010-10-30T14:12:00Z");
    ZonedDateTime OLD_DELETION_DATE = ZonedDateTime.parse("2010-10-30T15:12:00Z");
    Date INTERNAL_DATE = Date.from(DELIVERY_DATE.toInstant());
    byte[] CONTENT = "header: value\r\n\r\ncontent".getBytes(StandardCharsets.UTF_8);
    String SUBJECT = "subject";

    Function<Long, DeletedMessage> DELETED_MESSAGE_GENERATOR = i -> DeletedMessage.builder()
        .messageId(InMemoryMessageId.of(i))
        .originMailboxes(MAILBOX_ID_1, MAILBOX_ID_2)
        .user(USER)
        .deliveryDate(DELIVERY_DATE)
        .deletionDate(DELETION_DATE)
        .sender(MaybeSender.of(SENDER))
        .recipients(RECIPIENT1, RECIPIENT2)
        .hasAttachment(false)
        .size(CONTENT.length)
        .build();
    DeletedMessage.Builder.RequireSize<DeletedMessage.Builder.FinalStage> SIZE_STAGE = DeletedMessage.builder()
        .messageId(MESSAGE_ID)
        .originMailboxes(MAILBOX_ID_1, MAILBOX_ID_2)
        .user(USER)
        .deliveryDate(DELIVERY_DATE)
        .deletionDate(DELETION_DATE)
        .sender(MaybeSender.of(SENDER))
        .recipients(RECIPIENT1, RECIPIENT2)
        .hasAttachment(false);
    Supplier<DeletedMessage.Builder.FinalStage> FINAL_STAGE = () -> SIZE_STAGE
        .size(CONTENT.length);
    DeletedMessage DELETED_MESSAGE_WITH_SUBJECT = FINAL_STAGE.get()
        .subject(SUBJECT)
        .build();
    DeletedMessage DELETED_MESSAGE = FINAL_STAGE.get().build();
    DeletedMessage OLD_DELETED_MESSAGE = DeletedMessage.builder()
        .messageId(OLD_MESSAGE_ID)
        .originMailboxes(MAILBOX_ID_1, MAILBOX_ID_2)
        .user(USER)
        .deliveryDate(OLD_DELIVERY_DATE)
        .deletionDate(OLD_DELETION_DATE)
        .sender(MaybeSender.of(SENDER))
        .recipients(RECIPIENT1, RECIPIENT2)
        .hasAttachment(false)
        .size(CONTENT.length)
        .build();
    DeletedMessage DELETED_MESSAGE_2 = DELETED_MESSAGE_GENERATOR.apply(MESSAGE_ID_2.getRawId());

    DeletedMessage DELETED_MESSAGE_OTHER_USER = DeletedMessage.builder()
        .messageId(InMemoryMessageId.of(48))
        .originMailboxes(MAILBOX_ID_1, MAILBOX_ID_2)
        .user(USER_2)
        .deliveryDate(DELIVERY_DATE)
        .deletionDate(DELETION_DATE)
        .sender(MaybeSender.of(SENDER))
        .recipients(RECIPIENT1, RECIPIENT2)
        .hasAttachment(false)
        .size(CONTENT.length)
        .build();
}
