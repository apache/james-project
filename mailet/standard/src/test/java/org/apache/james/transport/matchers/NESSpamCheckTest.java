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

package org.apache.james.transport.matchers;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.MessagingException;

import org.apache.mailet.Mail;
import org.apache.mailet.base.RFC2822Headers;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.apache.mailet.base.test.MailUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NESSpamCheckTest {

    private NESSpamCheck matcher;

    @BeforeEach
    public void setUp() throws Exception {
        matcher = new NESSpamCheck();
        FakeMatcherConfig mci = FakeMatcherConfig.builder()
            .matcherName("NESSpamCheck")
            .build();

        matcher.init(mci);
    }

    @Test
    public void testNESSpamCheckMatched() throws MessagingException {
        Mail mail = MailUtil.createMockMail2Recipients(
            MailUtil.createMimeMessage(RFC2822Headers.RECEIVED, "xxxxxxxxxxxxxxxxxxxxx"));

        assertThat(matcher.match(mail)).hasSize(2);
    }

    @Test
    public void testNESSpamCheckNotMatched() throws MessagingException {
        Mail mail = MailUtil.createMockMail2Recipients(
            MailUtil.createMimeMessage("defaultHeaderName", "defaultHeaderValue"));

        assertThat(matcher.match(mail)).isNull();
    }
}
