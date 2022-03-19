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

import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.apache.mailet.base.test.MailUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SetMailAttributeTest {

    private Mailet mailet;

    @BeforeEach
    void setupMailet() {
        mailet = new SetMailAttribute();
    }

    @Test
    void shouldAddConfiguredAttributes() throws MessagingException {
        AttributeName name1 = AttributeName.of("org.apache.james.junit1");
        AttributeName name2 = AttributeName.of("org.apache.james.junit2");
        AttributeValue<String> value1 = AttributeValue.of("true");
        AttributeValue<String> value2 = AttributeValue.of("happy");
        Attribute attribute1 = new Attribute(name1, value1);
        Attribute attribute2 = new Attribute(name2, value2);
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty(name1.asString(), value1.value())
                .setProperty(name2.asString(), value2.value())
                .build();

        mailet.init(mailetConfig);

        Mail mail = MailUtil.createMockMail2Recipients(MimeMessageUtil.defaultMimeMessage());
        
        mailet.service(mail);

        assertThat(mail.getAttribute(name1)).contains(attribute1);
        assertThat(mail.getAttribute(name2)).contains(attribute2);
    }
    
    @Test
    void shouldAddNothingWhenNoConfiguredAttribute() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .build();
     
        mailet.init(mailetConfig);

        Mail mail = MailUtil.createMockMail2Recipients(MimeMessageUtil.defaultMimeMessage());
        
        mailet.service(mail);

        assertThat(mail.attributes()).isEmpty();
    }
    
    @Test
    void shouldOverwriteAttributeWhenAttributeAlreadyPresent() throws MessagingException {
        AttributeName name = AttributeName.of("org.apache.james.junit1");
        Attribute mailAttribute = new Attribute(name, AttributeValue.of("foo"));
        Attribute replacedAttribute = new Attribute(name, AttributeValue.of("bar"));
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty(name.asString(), (String) replacedAttribute.getValue().value())
                .build();
        
        mailet.init(mailetConfig);
        
        Mail mail = MailUtil.createMockMail2Recipients(MimeMessageUtil.defaultMimeMessage());
        mail.setAttribute(mailAttribute);
        
        mailet.service(mail);

        assertThat(mail.getAttribute(name)).contains(replacedAttribute);
    }
}
