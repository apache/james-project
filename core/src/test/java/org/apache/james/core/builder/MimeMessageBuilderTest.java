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

import jakarta.mail.internet.MimeMessage;

import org.apache.james.util.MimeMessageUtil;
import org.junit.jupiter.api.Test;

class MimeMessageBuilderTest {

    @Test
    void buildShouldPreserveMessageID() throws Exception {
        String messageID = "<abc@123>";
        MimeMessage mimeMessage = MimeMessageBuilder.mimeMessageBuilder()
            .addHeader("Message-ID", messageID)
            .build();

        assertThat(mimeMessage.getMessageID())
            .isEqualTo(messageID);
    }

    @Test
    void buildShouldAllowMultiValuedHeader() throws Exception {
        String headerName = "header";
        MimeMessage mimeMessage = MimeMessageBuilder.mimeMessageBuilder()
            .addHeader(headerName, "value1")
            .addHeader(headerName, "value2")
            .build();

        assertThat(mimeMessage.getHeader(headerName))
            .hasSize(2);
    }

    @Test
    void buildShouldPreserveDate() throws Exception {
        String value = "Wed, 28 Mar 2018 17:02:25 +0200";
        MimeMessage mimeMessage = MimeMessageBuilder.mimeMessageBuilder()
            .addHeader("Date", value)
            .build();

        assertThat(mimeMessage.getHeader("Date"))
            .containsExactly(value);
    }

    @Test
    void embeddedMessagesShouldBeSupported() throws Exception {
        MimeMessage embeddedMimeMessage = MimeMessageBuilder.mimeMessageBuilder()
            .setSubject("A unicorn eat popcorn")
            .setText("As studies demonstrated unicorns eats cereals.")
            .build();
        MimeMessage mimeMessage = MimeMessageBuilder.mimeMessageBuilder()
            .setSubject("Internet is a strange place")
            .setContent(MimeMessageBuilder.multipartBuilder()
                .addBody(MimeMessageBuilder.bodyPartBuilder()
                    .data("The following embedded message is sooo funny!"))
                .addBody(embeddedMimeMessage))
            .build();

        assertThat(MimeMessageUtil.asString(mimeMessage))
            .contains(MimeMessageUtil.asString(embeddedMimeMessage));
    }

    @Test
    void buildShouldAllowToSpecifyMultipartSubtype() throws Exception {
        MimeMessage mimeMessage = MimeMessageBuilder.mimeMessageBuilder()
            .setContent(MimeMessageBuilder.multipartBuilder()
                .subType("alternative")
                .addBody(MimeMessageBuilder.bodyPartBuilder().data("Body 1"))
                .addBody(MimeMessageBuilder.bodyPartBuilder().data("Body 2")))
            .build();

        assertThat(mimeMessage.getContentType())
            .startsWith("multipart/alternative");
    }

}