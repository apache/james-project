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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import jakarta.mail.internet.MimeMessage;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.Store;
import org.apache.james.blob.memory.MemoryBlobStoreFactory;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.util.MimeMessageUtil;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class MimeMessageStoreTest {
    private static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();

    private Store<MimeMessage, MimeMessagePartsId> testee;
    private BlobStore blobStore;

    @BeforeEach
    void setUp() {
        blobStore = MemoryBlobStoreFactory.builder()
            .blobIdFactory(BLOB_ID_FACTORY)
            .defaultBucketName()
            .passthrough();
        testee = MimeMessageStore.factory(blobStore).mimeMessageStore();
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
    void mailStoreShouldPreserveContent() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom("any@any.com")
            .addToRecipient("toddy@any.com")
            .setSubject("Important Mail")
            .setText("Important mail content")
            .build();

        MimeMessagePartsId parts = testee.save(message).block();

        MimeMessage retrievedMessage = testee.read(parts).block();

        assertThat(MimeMessageUtil.asString(retrievedMessage))
            .isEqualTo(MimeMessageUtil.asString(message));
    }

    @Test
    void readShouldNotReturnDeletedMessage() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom("any@any.com")
            .addToRecipient("toddy@any.com")
            .setSubject("Important Mail")
            .setText("Important mail content")
            .build();

        MimeMessagePartsId parts = testee.save(message).block();

        Mono.from(testee.delete(parts)).block();

        assertThatThrownBy(() -> testee.read(parts).block())
            .isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    void deleteShouldNotThrowWhenCalledOnNonExistingData() throws Exception {
        MimeMessagePartsId parts = MimeMessagePartsId.builder()
            .headerBlobId(BLOB_ID_FACTORY.randomId())
            .bodyBlobId(BLOB_ID_FACTORY.randomId())
            .build();

        assertThatCode(() -> Mono.from(testee.delete(parts)).block())
            .doesNotThrowAnyException();
    }

    @Test
    void mailStoreShouldPreserveMailWithoutBody() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom("any@any.com")
            .addToRecipient("toddy@any.com")
            .setSubject("Important Mail")
            .build();

        MimeMessagePartsId parts = testee.save(message).block();

        MimeMessage retrievedMessage = testee.read(parts).block();

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

        MimeMessagePartsId parts = testee.save(message).block();

        SoftAssertions.assertSoftly(
            softly -> {
                BlobId headerBlobId = parts.getHeaderBlobId();
                BlobId bodyBlobId = parts.getBodyBlobId();

                softly.assertThat(new String(Mono.from(blobStore.readBytes(blobStore.getDefaultBucketName(), headerBlobId)).block(), StandardCharsets.UTF_8))
                    .isEqualTo("Date: Thu, 6 Sep 2018 13:29:13 +0700 (ICT)\r\n" +
                        "From: any@any.com\r\n" +
                        "To: toddy@any.com\r\n" +
                        "Message-ID: <84739718.0.1536215353507@localhost.localdomain>\r\n" +
                        "Subject: Important Mail\r\n" +
                        "MIME-Version: 1.0\r\n" +
                        "Content-Type: text/plain; charset=UTF-8\r\n" +
                        "Content-Transfer-Encoding: 7bit\r\n\r\n");
                softly.assertThat(new String(Mono.from(blobStore.readBytes(blobStore.getDefaultBucketName(), bodyBlobId)).block(), StandardCharsets.UTF_8))
                    .isEqualTo("Important mail content");
            });
    }
}
