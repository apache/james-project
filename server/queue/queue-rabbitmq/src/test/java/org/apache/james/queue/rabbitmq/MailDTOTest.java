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

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.james.util.ClassLoaderUtils.getSystemResourceAsString;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Date;

import jakarta.mail.MessagingException;

import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.server.core.MailImpl;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.PerRecipientHeaders;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class MailDTOTest {
    static final EnqueueId EN_QUEUE_ID = EnqueueId.ofSerialized("110e8400-e29b-11d4-a716-446655440000");
    static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();
    static final Date LAST_UPDATED = Date.from(Instant.parse("2016-09-08T14:25:52.000Z"));

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
            .registerModule(new Jdk8Module())
            .registerModule(new JavaTimeModule())
            .registerModule(new GuavaModule());
    }

    @Test
    void mailDtoShouldBeSerializedToTheRightFormat() throws Exception {
        assertThatJson(objectMapper.writeValueAsString(mailDTO1()))
            .isEqualTo(getSystemResourceAsString("json/mail1.json"));
    }

    @Test
    void mailDtoShouldBeDeserializedFromTheRightFormat() throws Exception {
        assertThat(objectMapper.readValue(getSystemResourceAsString("json/mail1.json"), MailReferenceDTO.class))
            .isEqualTo(mailDTO1());
    }

    @Test
    void mailDtoShouldBeDeserializedFromTheRightFormatWhenLegacy() throws Exception {
        assertThat(objectMapper.readValue(getSystemResourceAsString("json/mail1-legacy.json"), MailReferenceDTO.class))
            .isEqualTo(mailDTO1());
    }

    @Test
    void mailDtoShouldBeSerializedWhenOnlyNameAndBlob() throws Exception {
        assertThatJson(objectMapper.writeValueAsString(mailDTOMin()))
            .isEqualTo(getSystemResourceAsString("json/mail_min.json"));
    }

    @Test
    void mailDtoShouldBeDeserializedWhenOnlyNameAndBlob() throws Exception {
        assertThat(objectMapper.readValue(getSystemResourceAsString("json/mail_min.json"), MailReferenceDTO.class))
            .isEqualTo(mailDTOMin());
    }

    private MailReferenceDTO mailDTO1() throws MessagingException {
        return MailReferenceDTO.fromMailReference(
            new MailReference(
            EN_QUEUE_ID,
            FakeMail.builder()
                .name("mail-name-558")
                .recipients(MailAddressFixture.RECIPIENT1, MailAddressFixture.RECIPIENT2)
                .sender(MailAddressFixture.SENDER)
                .attribute(new Attribute(AttributeName.of("att1"), AttributeValue.of("value")))
                .errorMessage("an error")
                .lastUpdated(LAST_UPDATED)
                .remoteHost("toto.com")
                .remoteAddr("159.221.12.145")
                .addHeaderForRecipient(PerRecipientHeaders.Header.builder()
                    .name("X-custom-header")
                    .value("uedcgukrcg")
                    .build(), MailAddressFixture.RECIPIENT1)
                .addHeaderForRecipient(PerRecipientHeaders.Header.builder()
                    .name("X-custom-header-2")
                    .value("uedcgukrcg")
                    .build(), MailAddressFixture.RECIPIENT2)
                .state("state")
                .build(),
            MimeMessagePartsId.builder()
                .headerBlobId(BLOB_ID_FACTORY.from("210e7136-ede3-44eb-9495-3ed816d6e23b"))
                .bodyBlobId(BLOB_ID_FACTORY.from("ef46c026-7819-4048-b562-3a37469191ed"))
                .build()));
    }

    private MailReferenceDTO mailDTOMin() {
        MailImpl mail = MailImpl.builder()
            .name("mail-name-558")
            .build();
        mail.setState(null);
        mail.setLastUpdated(null);
        return MailReferenceDTO.fromMailReference(
            new MailReference(
                EN_QUEUE_ID,
                mail,
                MimeMessagePartsId.builder()
                    .headerBlobId(BLOB_ID_FACTORY.from("210e7136-ede3-44eb-9495-3ed816d6e23b"))
                    .bodyBlobId(BLOB_ID_FACTORY.from("ef46c026-7819-4048-b562-3a37469191ed"))
                    .build()));
    }
}
