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

package org.apache.james.queue.rabbitmq;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import jakarta.mail.MessagingException;

import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class EnqueuedItemTest {

    private MailQueueName mailQueueName;
    private Mail mail;
    private Instant enqueuedTime;
    private MimeMessagePartsId partsId;

    EnqueuedItemTest() throws MessagingException {
        mailQueueName = MailQueueName.fromString("mailQueueName");
        mail = FakeMail.defaultFakeMail();
        enqueuedTime = Instant.now();
        partsId = MimeMessagePartsId.builder()
                .headerBlobId(new HashBlobId.Factory().from("headerBlobId"))
                .bodyBlobId(new HashBlobId.Factory().from("bodyBlobId"))
                .build();
    }

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(EnqueuedItem.class)
            .verify();
    }

    @Test
    void buildShouldThrowWhenEnqueueIdIsNull() {
        assertThatThrownBy(() -> EnqueuedItem.builder()
                .enqueueId(null)
                .mailQueueName(mailQueueName)
                .mail(mail)
                .enqueuedTime(enqueuedTime)
                .mimeMessagePartsId(partsId)
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildShouldThrowWhenMailQueueNameIsNull() {
        assertThatThrownBy(() -> EnqueuedItem.builder()
                .enqueueId(EnqueueId.generate())
                .mailQueueName(null)
                .mail(mail)
                .enqueuedTime(enqueuedTime)
                .mimeMessagePartsId(partsId)
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildShouldThrowWhenMailIsNull() {
        assertThatThrownBy(() -> EnqueuedItem.builder()
                .enqueueId(EnqueueId.generate())
                .mailQueueName(mailQueueName)
                .mail(null)
                .enqueuedTime(enqueuedTime)
                .mimeMessagePartsId(partsId)
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildShouldThrowWhenEnqueuedTimeIsNull() {
        assertThatThrownBy(() -> EnqueuedItem.builder()
                .enqueueId(EnqueueId.generate())
                .mailQueueName(mailQueueName)
                .mail(mail)
                .enqueuedTime(null)
                .mimeMessagePartsId(partsId)
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildShouldThrowWhenMimeMessagePartsIdIsNull() {
        assertThatThrownBy(() -> EnqueuedItem.builder()
                .enqueueId(EnqueueId.generate())
                .mailQueueName(mailQueueName)
                .mail(mail)
                .enqueuedTime(enqueuedTime)
                .mimeMessagePartsId(null)
                .build())
            .isInstanceOf(NullPointerException.class);
    }
}