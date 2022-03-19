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

package org.apache.james.core.builder;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Properties;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;

class MimeMessageWrapperTest {

    @Test
    void saveChangesShouldPreserveMessageId() throws Exception {
        String messageId = "<5436@ab.com>";
        String messageText = "Message-ID: " + messageId + "\r\n" +
            "Subject: test\r\n" +
            "\r\n" +
            "Content!";
        InputStream inputStream = new ByteArrayInputStream(messageText.getBytes(StandardCharsets.UTF_8));
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()), inputStream);
        MimeMessageWrapper mimeMessageWrapper = MimeMessageWrapper.wrap(message);

        mimeMessageWrapper.saveChanges();

        assertThat(mimeMessageWrapper.getMessageID())
            .isEqualTo(messageId);
    }

    @Test
    void wrapShouldPreserveBody() throws Exception {
        String messageAsText = "header1: <5436@ab.com>\r\n" +
            "Subject: test\r\n" +
            "\r\n" +
            "Content!";
        InputStream inputStream = new ByteArrayInputStream(messageAsText.getBytes(StandardCharsets.UTF_8));
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()), inputStream);
        MimeMessageWrapper mimeMessageWrapper = MimeMessageWrapper.wrap(message);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        mimeMessageWrapper.writeTo(outputStream);

        assertThat(new String(outputStream.toByteArray(), StandardCharsets.UTF_8))
            .isEqualTo(messageAsText);
    }

    @Test
    void wrapShouldNotThrowWhenNoBody() throws Exception {
        MimeMessage originalMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        originalMessage.addHeader("header1", "value1");
        originalMessage.addHeader("header2", "value2");
        originalMessage.addHeader("header2", "value3");
        MimeMessageWrapper mimeMessageWrapper = MimeMessageWrapper.wrap(originalMessage);

        assertThat(Collections.list(mimeMessageWrapper.getAllHeaders()))
            .extracting(javaxHeader -> new MimeMessageBuilder.Header(javaxHeader.getName(), javaxHeader.getValue()))
            .contains(new MimeMessageBuilder.Header("header1", "value1"),
                new MimeMessageBuilder.Header("header2", "value2"),
                new MimeMessageBuilder.Header("header2", "value3"));
    }

}