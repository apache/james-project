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

import jakarta.mail.MessagingException;

import org.apache.mailet.Attribute;
import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.apache.mailet.base.test.MailUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RemoveAllMailAttributesTest {

    private Mail mail;
    private Mailet mailet;

    @BeforeEach
    public void setUp() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .build();
        mailet = new RemoveAllMailAttributes();
        mailet.init(mailetConfig);
    }

    @Test
    public void getMailetInfoShouldReturnValue() {
        assertThat(mailet.getMailetInfo()).isEqualTo("Remove All Mail Attributes Mailet");
    }

    @Test
    public void serviceShouldRemoveAllMailAttributes() throws MessagingException {
        mail = MailUtil.createMockMail2Recipients();
        mail.setAttribute(Attribute.convertToAttribute("org.apache.james.test.junit", "true"));

        mailet.service(mail);

        assertThat(mail.attributes()).isEmpty();
    }

    @Test
    public void serviceShouldRemoveAllMailAttributesWhenNone() throws MessagingException {
        mail = MailUtil.createMockMail2Recipients();

        mailet.service(mail);

        assertThat(mail.attributes()).isEmpty();
    }
}
