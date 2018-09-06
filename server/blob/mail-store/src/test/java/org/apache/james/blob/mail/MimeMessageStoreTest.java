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

package org.apache.james.blob.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.mail.internet.MimeMessage;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.Store;
import org.apache.james.blob.memory.MemoryBlobStore;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.util.MimeMessageUtil;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

class MimeMessageStoreTest {
    private static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();

    private MimeMessageStore testee;
    private MemoryBlobStore blobStore;

    @BeforeEach
    void setUp() {
        blobStore = new MemoryBlobStore(BLOB_ID_FACTORY);
        testee = new MimeMessageStore(blobStore);
    }

    @Test
    void saveShouldThrowWhenNull() {
        assertThatThrownBy(() -> testee.save(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void readShouldThrowWhenNull() {
        assertThatThrownBy(() -> testee.read(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void readShouldThrowWhenMissingHeaderBlobs() {
        assertThatThrownBy(() -> testee.read(ImmutableMap.of(
            MimeMessageStore.HEADER_BLOB_TYPE, BLOB_ID_FACTORY.randomId())))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readShouldThrowWhenMissingBodyBlobs() {
        assertThatThrownBy(() -> testee.read(ImmutableMap.of(
            MimeMessageStore.BODY_BLOB_TYPE, BLOB_ID_FACTORY.randomId())))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readShouldThrowWhenExtraBodyBlobs() {
        assertThatThrownBy(() -> testee.read(ImmutableMap.of(
            MimeMessageStore.BODY_BLOB_TYPE, BLOB_ID_FACTORY.randomId(),
            MimeMessageStore.HEADER_BLOB_TYPE, BLOB_ID_FACTORY.randomId(),
            new Store.BlobType("Unknown"), BLOB_ID_FACTORY.randomId())))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mailStoreShouldPreserveContent() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom("any@any.com")
            .addToRecipient("toddy@any.com")
            .setSubject("Important Mail")
            .setText("Important mail content")
            .build();

        Map<Store.BlobType, BlobId> parts = testee.save(message).join();

        MimeMessage retrievedMessage = testee.read(parts).join();

        assertThat(MimeMessageUtil.asString(retrievedMessage))
            .isEqualTo(MimeMessageUtil.asString(message));
    }

    @Test
    void mailStoreShouldPreserveMailWithoutBody() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom("any@any.com")
            .addToRecipient("toddy@any.com")
            .setSubject("Important Mail")
            .build();

        Map<Store.BlobType, BlobId> parts = testee.save(message).join();

        MimeMessage retrievedMessage = testee.read(parts).join();

        assertThat(MimeMessageUtil.asString(retrievedMessage))
            .isEqualTo(MimeMessageUtil.asString(message));
    }

    @Test
    void saveShouldSeparateHeadersAndBodyInDifferentBlobs() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addHeader("Date", "Thu, 6 Sep 2018 13:29:13 +0700 (ICT)")
            .addHeader("Message-ID", "<84739718.0.1536215353507@localhost.localdomain>")
            .addFrom("any@any.com")
            .addToRecipient("toddy@any.com")
            .setSubject("Important Mail")
            .setText("Important mail content")
            .build();

        Map<Store.BlobType, BlobId> parts = testee.save(message).join();

        SoftAssertions.assertSoftly(
            softly -> {
                softly.assertThat(parts).containsKeys(MimeMessageStore.HEADER_BLOB_TYPE, MimeMessageStore.BODY_BLOB_TYPE);

                BlobId headerBlobId = parts.get(MimeMessageStore.HEADER_BLOB_TYPE);
                BlobId bodyBlobId = parts.get(MimeMessageStore.BODY_BLOB_TYPE);

                softly.assertThat(new String(blobStore.readBytes(headerBlobId).join(), StandardCharsets.UTF_8))
                    .isEqualTo("Date: Thu, 6 Sep 2018 13:29:13 +0700 (ICT)\r\n" +
                        "From: any@any.com\r\n" +
                        "To: toddy@any.com\r\n" +
                        "Message-ID: <84739718.0.1536215353507@localhost.localdomain>\r\n" +
                        "Subject: Important Mail\r\n" +
                        "MIME-Version: 1.0\r\n" +
                        "Content-Type: text/plain; charset=UTF-8\r\n" +
                        "Content-Transfer-Encoding: 7bit\r\n\r\n");
                softly.assertThat(new String(blobStore.readBytes(bodyBlobId).join(), StandardCharsets.UTF_8))
                    .isEqualTo("Important mail content");
            });
    }
}
