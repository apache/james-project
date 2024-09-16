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

package org.apache.james.transport.matcher;

import static org.apache.mailet.base.MailAddressFixture.RECIPIENT1;
import static org.apache.mailet.base.MailAddressFixture.SENDER;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class IsSMIMESignedTest {
    private IsSMIMESigned isSMIMESigned;

    @BeforeEach
    void beforeEach() {
        isSMIMESigned = new IsSMIMESigned();
    }

    @ParameterizedTest
    @ValueSource(strings = {"multipart/signed",
        "application/pkcs7-signature",
        "application/x-pkcs7-signature",
        "application/pkcs7-mime; smime-type=signed-data; name=\"smime.p7m\"",
        "application/x-pkcs7-mime; smime-type=signed-data; name=\"smime.p7m\""})
    void matchShouldReturnNonEmptyListWhenMessageContentTypeIsSMIMERelated(String contentType) throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder().addHeader("Content-Type", contentType))
            .sender(SENDER)
            .recipient(RECIPIENT1)
            .build();
        assertThat(isSMIMESigned.match(mail)).isNotEmpty();
    }

    @Test
    void matchShouldReturnNullWhenMessageContentTypeIsNotSMIMERelated() throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder().addHeader("Content-Type", "text/plain"))
            .sender(SENDER)
            .recipient(RECIPIENT1)
            .build();
        assertThat(isSMIMESigned.match(mail)).isNull();
    }

    @Test
    void matchShouldReturnNullWhenMailIsNull() throws Exception {
        assertThat(isSMIMESigned.match(null)).isNull();
    }

    @Test
    void matchShouldReturnNullWhenMessageIsNull() throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("mail")
            .build();
        assertThat(isSMIMESigned.match(mail)).isNull();
    }
}
