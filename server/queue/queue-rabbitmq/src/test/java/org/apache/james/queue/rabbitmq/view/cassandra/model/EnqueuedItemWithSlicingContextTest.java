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

package org.apache.james.queue.rabbitmq.view.cassandra.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import jakarta.mail.MessagingException;

import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.queue.rabbitmq.EnqueueId;
import org.apache.james.queue.rabbitmq.EnqueuedItem;
import org.apache.james.queue.rabbitmq.MailQueueName;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class EnqueuedItemWithSlicingContextTest {

    private EnqueuedItem enqueuedItem;
    private EnqueuedItemWithSlicingContext.SlicingContext slicingContext;

    private EnqueuedItemWithSlicingContextTest() throws MessagingException {
        enqueuedItem = EnqueuedItem.builder()
            .enqueueId(EnqueueId.generate())
            .mailQueueName(MailQueueName.fromString("mailQueueName"))
                .mail(FakeMail.builder()
                        .name("name")
                        .build())
                .enqueuedTime(Instant.now())
                .mimeMessagePartsId(MimeMessagePartsId.builder()
                        .headerBlobId(new HashBlobId.Factory().from("headerBlodId"))
                        .bodyBlobId(new HashBlobId.Factory().from("bodyBlodId"))
                        .build())
                .build();
        slicingContext = EnqueuedItemWithSlicingContext.SlicingContext.of(BucketedSlices.BucketId.of(1), Instant.now());
    }

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(EnqueuedItemWithSlicingContext.class)
            .verify();
    }

    @Test
    void slicingContextShouldMatchBeanContract() {
        EqualsVerifier.forClass(EnqueuedItemWithSlicingContext.SlicingContext.class)
            .verify();
    }

    @Test
    void buildShouldThrowWhenEnqueuedItemIsNull() {
        EnqueuedItem enqueuedItem = null;
        assertThatThrownBy(() -> EnqueuedItemWithSlicingContext.builder()
                .enqueuedItem(enqueuedItem)
                .slicingContext(slicingContext)
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildShouldThrowWhenSlicingContextIsNull() {
        EnqueuedItemWithSlicingContext.SlicingContext slicingContext = null;
        assertThatThrownBy(() -> EnqueuedItemWithSlicingContext.builder()
                .enqueuedItem(enqueuedItem)
                .slicingContext(slicingContext)
                .build())
            .isInstanceOf(NullPointerException.class);
    }
}