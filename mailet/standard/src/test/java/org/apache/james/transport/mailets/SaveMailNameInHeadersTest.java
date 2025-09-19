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

import java.util.Properties;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SaveMailNameInHeadersTest {

    private SaveMailNameInHeaders testee;

    @BeforeEach
    public void setUp() throws Exception {
        testee = new SaveMailNameInHeaders();
        testee.init(FakeMailetConfig.builder()
            .mailetName("SanitizeMimeMessageId")
            .build());
    }

    @Test
    public void shouldRecordMailName() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        FakeMail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(mimeMessage)
            .build();

        testee.service(mail);

        assertThat(mail.getMessage().getHeader("X-JAMES-Mail-Name")).contains("mail");
    }

    @Test
    public void shouldResetValueWhenProcessedSeveralTimes() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        mimeMessage.addHeader("X-JAMES-Mail-Name", "oldValue");
        mimeMessage.setHeader("Message-ID", "<existing-id@domain.com>");
        FakeMail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(mimeMessage)
            .build();

        testee.service(mail);

        assertThat(mail.getMessage().getHeader("X-JAMES-Mail-Name")).containsExactly("mail");
    }
}

