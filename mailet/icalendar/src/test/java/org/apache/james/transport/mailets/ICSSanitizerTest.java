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

package org.apache.james.transport.mailets;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import jakarta.mail.Multipart;
import jakarta.mail.util.SharedByteArrayInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ICSSanitizerTest {
    private ICSSanitizer testee;

    @BeforeEach
    void setUp() {
        testee = new ICSSanitizer();
    }

    @Test
    void serviceShouldEnhanceTextCalendarOnlyHeaders() throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(MimeMessageUtil.mimeMessageFromStream(ClassLoaderUtils.getSystemResourceAsSharedStream("ics_in_header.eml")))
            .build();

        testee.service(mail);

        Object content = mail.getMessage().getContent();

        assertThat(content).isInstanceOf(Multipart.class);
        Multipart multipart = (Multipart) content;
        assertThat(multipart.getCount()).isEqualTo(1);

        assertThat(multipart.getBodyPart(0).getContent())
            .isEqualTo("BEGIN: VCALENDAR\r\n" +
                "PRODID: -//Mozilla.org/NONSGML Mozilla Calendar V1.1//EN\r\n" +
                "VERSION: 2.0\r\n" +
                "METHOD: REPLY\r\n" +
                "BEGIN: VEVENT\r\n" +
                "CREATED: 20180911T144134Z\r\n" +
                "LAST-MODIFIED: 20180912T085818Z\r\n" +
                "DTSTAMP: 20180912T085818Z\r\n" +
                "UID: f1514f44bf39311568d64072945fc3b2973debebb0d550e8c841f3f0604b2481e047fe\r\n" +
                " b2aab16e43439a608f28671ab7c10e754cbbe63441a01ba232a553df751eb0931728d67672\r\n" +
                " \r\n" +
                "SUMMARY: Point Produit\r\n" +
                "PRIORITY: 5\r\n" +
                "ORGANIZER;CN=Bob;X-OBM-ID=348: mailto:bob@linagora.com\r\n" +
                "ATTENDEE;CN=Alice;PARTSTAT=ACCEPTED;CUTYPE=INDIVIDUAL;X-OBM-ID=810: mailto:alice@linagora.com\r\n" +
                "DTSTART: 20180919T123000Z\r\n" +
                "DURATION: PT1H\r\n" +
                "TRANSP: OPAQUE\r\n" +
                "SEQUENCE: 0\r\n" +
                "X-LIC-ERROR;X-LIC-ERRORTYPE=VALUE-PARSE-ERROR: No value for X property. Rem\r\n" +
                " oving entire property:\r\n" +
                "CLASS: PUBLIC\r\n" +
                "X-OBM-DOMAIN: linagora.com\r\n" +
                "X-OBM-DOMAIN-UUID: 02874f7c-d10e-102f-acda-0015176f7922\r\n" +
                "LOCATION: TÃ\u0083Â©lÃ\u0083Â©phone\r\n" +
                "END: VEVENT\r\n" +
                "END: VCALENDAR\r\n" +
                "Content-class: urn:content-classes:calendarmessage\r\n" +
                "Content-type: text/calendar; method=REPLY; charset=UTF-8\r\n" +
                "Content-transfer-encoding: 8BIT\r\n" +
                "Content-Disposition: attachment");
        assertThat(multipart.getBodyPart(0).getContentType()).startsWith("text/calendar; method=REPLY; charset=UTF-8");
    }

    @Test
    void validTextCalendarShouldNotBeSanitized() throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(
                MimeMessageBuilder.mimeMessageBuilder()
                    .setMultipartWithBodyParts(
                        MimeMessageBuilder.bodyPartBuilder()
                            .type("text/calendar")
                            .data("Not empty")
                            .addHeader("X-CUSTOM", "Because it is a valid ICS it should not be pushed in body")))
            .build();

        testee.service(mail);

        Object content = mail.getMessage().getContent();

        assertThat(content).isInstanceOf(Multipart.class);
        Multipart multipart = (Multipart) content;
        assertThat(multipart.getCount()).isEqualTo(1);

        SharedByteArrayInputStream inputStream = (SharedByteArrayInputStream) multipart.getBodyPart(0).getContent();
        assertThat(IOUtils.toString(inputStream, StandardCharsets.UTF_8))
            .doesNotContain("X-CUSTOM");
    }
}