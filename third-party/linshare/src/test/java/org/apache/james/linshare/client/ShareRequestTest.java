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

package org.apache.james.linshare.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.apache.james.core.MailAddress;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class ShareRequestTest {

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(ShareRequest.class)
            .verify();
    }

    @Test
    void builderShouldThrowWhenPassingNullRecipient() {
        assertThatThrownBy(() -> ShareRequest.builder()
                .addDocumentId(new Document.DocumentId(UUID.fromString("89bc2e3b-e07e-405f-9520-2de33a0a836c")))
                .addRecipient(null)
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void builderShouldThrowWhenPassingNullDocumentId() {
        assertThatThrownBy(() -> ShareRequest.builder()
                .addDocumentId(null)
                .addRecipient(new MailAddress("user@james.org"))
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void builderShouldThrowWhenNoRecipient() {
        assertThatThrownBy(() -> ShareRequest.builder()
                .addRecipient(new MailAddress("user@james.org"))
                .build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void builderShouldThrowWhenNoDocumentId() {
        assertThatThrownBy(() -> ShareRequest.builder()
                .addDocumentId(new Document.DocumentId(UUID.fromString("89bc2e3b-e07e-405f-9520-2de33a0a836c")))
                .build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Nested
    class RecipientTest {

        @Test
        void shouldMatchBeanContract() {
            EqualsVerifier.forClass(ShareRequest.Recipient.class)
                .verify();
        }

        @Test
        void constructorShouldThrowWhenPassingNullMailAddress() {
            assertThatThrownBy(() -> new ShareRequest.Recipient(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void constructorShouldThrowWhenPassingNullSenderMailAddress() {
            assertThatThrownBy(() -> new ShareRequest.Recipient(MailAddress.nullSender()))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
