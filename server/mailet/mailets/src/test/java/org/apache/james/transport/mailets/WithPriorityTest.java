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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.queue.api.MailPrioritySupport;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetConfig;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WithPriorityTest {

    private static final Attribute PROPERTY_PRIORITY = new Attribute(MailPrioritySupport.MAIL_PRIORITY, AttributeValue.of(7));
    private WithPriority mailet;

    @BeforeEach
    void setup() {
        mailet = new WithPriority();
    }

    @Test
    void getMailetInfoShouldReturnExpectedContent() {
        String expected = "With Priority Mailet";

        String actual = mailet.getMailetInfo();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void initShouldNotThrowWhenValidPriority() {
        MailetConfig mockedMailetConfig = FakeMailetConfig.builder()
            .mailetContext(FakeMailContext.defaultContext())
            .setProperty("priority", "7")
            .build();

        assertThatCode(() -> mailet.init(mockedMailetConfig))
            .doesNotThrowAnyException();
    }

    @Test
    void initShouldThrowWhenInvalidPriority() {
        MailetConfig mockedMailetConfig = FakeMailetConfig.builder()
            .mailetContext(FakeMailContext.defaultContext())
            .setProperty("priority", "-1")
            .build();

        assertThatThrownBy(() -> mailet.init(mockedMailetConfig))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void initShouldThrowWhenPriorityIsNotANumber() {
        MailetConfig mockedMailetConfig = FakeMailetConfig.builder()
                .mailetContext(FakeMailContext.defaultContext())
                .setProperty("priority", "k")
                .build();

        assertThatThrownBy(() -> mailet.init(mockedMailetConfig))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void initShouldThrowWhenPriorityIsEmpty() {
        MailetConfig mockedMailetConfig = FakeMailetConfig.builder()
                .mailetContext(FakeMailContext.defaultContext())
                .setProperty("priority", "")
                .build();

        assertThatThrownBy(() -> mailet.init(mockedMailetConfig))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void initShouldThrowWhenNoPriority() {
        MailetConfig mockedMailetConfig = FakeMailetConfig.builder()
                .mailetContext(FakeMailContext.defaultContext())
                .build();

        assertThatThrownBy(() -> mailet.init(mockedMailetConfig))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void serviceShouldSetMailPriorityWhenNone() throws Exception {
        MailetConfig mockedMailetConfig = FakeMailetConfig.builder()
            .mailetContext(FakeMailContext.defaultContext())
            .setProperty("priority", PROPERTY_PRIORITY.getValue().value().toString())
            .build();

        mailet.init(mockedMailetConfig);
        Mail mail = FakeMail.builder().name("name").build();
        mailet.service(mail);

        assertThat(mail.getAttribute(MailPrioritySupport.MAIL_PRIORITY)).contains(PROPERTY_PRIORITY);
    }

    @Test
    void serviceShouldSetMailPriorityWhenPriorityExists() throws Exception {
        MailetConfig mockedMailetConfig = FakeMailetConfig.builder()
            .mailetContext(FakeMailContext.defaultContext())
            .setProperty("priority", PROPERTY_PRIORITY.getValue().value().toString())
            .build();

        mailet.init(mockedMailetConfig);
        Mail mail = FakeMail.builder()
                .name("name")
                .attribute(new Attribute(MailPrioritySupport.MAIL_PRIORITY, AttributeValue.of(5)))
                .build();
        mailet.service(mail);

        assertThat(mail.getAttribute(MailPrioritySupport.MAIL_PRIORITY)).contains(PROPERTY_PRIORITY);
    }
}
