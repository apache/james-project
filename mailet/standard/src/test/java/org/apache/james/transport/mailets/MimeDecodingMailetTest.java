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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import jakarta.mail.MessagingException;

import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.MailetContext;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

class MimeDecodingMailetTest {

    private static final AttributeName MAIL_ATTRIBUTE = AttributeName.of("mime.attachments");
    @SuppressWarnings("unchecked")
    private static final Class<Map<String, AttributeValue<byte[]>>> MAP_STRING_BYTES_CLASS = (Class<Map<String, AttributeValue<byte[]>>>) (Object) Map.class;

    private MailetContext mailetContext;
    private MimeDecodingMailet testee;

    @BeforeEach
    void setUp() {
        testee = new MimeDecodingMailet();
        Logger logger = mock(Logger.class);
        mailetContext = FakeMailContext.builder()
                .logger(logger)
                .build();
    }

    @Test
    void initShouldThrowWhenNoAttributeParameter() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(mailetContext)
                .build();
        assertThatThrownBy(() -> testee.init(mailetConfig))
            .isInstanceOf(MailetException.class);
    }

    @Test
    void initShouldThrowWhenAttributeParameterIsEmpty() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(mailetContext)
                .setProperty(MimeDecodingMailet.ATTRIBUTE_PARAMETER_NAME, "")
                .build();
        assertThatThrownBy(() -> testee.init(mailetConfig))
            .isInstanceOf(MailetException.class);
    }

    @Test
    void serviceShouldNotThrowWhenAttributeContentIsNotAMap() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(mailetContext)
                .setProperty(MimeDecodingMailet.ATTRIBUTE_PARAMETER_NAME, MAIL_ATTRIBUTE.asString())
                .build();
        testee.init(mailetConfig);

        FakeMail mail = FakeMail.defaultFakeMail();
        mail.setAttribute(new Attribute(MAIL_ATTRIBUTE, AttributeValue.of(ImmutableList.of())));

        testee.service(mail);
    }

    @Test
    void serviceShouldNotThrowWhenAttributeContentIsAMapOfWrongTypes() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(mailetContext)
                .setProperty(MimeDecodingMailet.ATTRIBUTE_PARAMETER_NAME, MAIL_ATTRIBUTE.asString())
                .build();
        testee.init(mailetConfig);

        FakeMail mail = FakeMail.defaultFakeMail();
        AttributeValue<Map<String, AttributeValue<?>>> value = AttributeValue.of(ImmutableMap.of("1", AttributeValue.of("2")));
        mail.setAttribute(new Attribute(MAIL_ATTRIBUTE, value));

        testee.service(mail);
    }

    @Test
    void serviceShouldNotSetAttributeWhenNone() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(mailetContext)
                .setProperty(MimeDecodingMailet.ATTRIBUTE_PARAMETER_NAME, MAIL_ATTRIBUTE.asString())
                .build();
        testee.init(mailetConfig);

        FakeMail mail = FakeMail.defaultFakeMail();

        testee.service(mail);
        assertThat(mail.getAttribute(MAIL_ATTRIBUTE)).isEmpty();
    }

    @Test
    void serviceShouldChangeAttributeWhenDefined() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(mailetContext)
                .setProperty(MimeDecodingMailet.ATTRIBUTE_PARAMETER_NAME, MAIL_ATTRIBUTE.asString())
                .build();
        testee.init(mailetConfig);

        FakeMail mail = FakeMail.defaultFakeMail();
        String text = "Attachment content";
        String content = "Content-Transfer-Encoding: 8bit\r\n"
                + "Content-Type: application/octet-stream; charset=utf-8\r\n\r\n"
                + text;
        String expectedKey = "mimePart1";
        AttributeValue<?> value = AttributeValue.of(ImmutableMap.of(expectedKey, AttributeValue.of(content.getBytes(StandardCharsets.UTF_8))));
        mail.setAttribute(new Attribute(MAIL_ATTRIBUTE, value));

        AttributeValue<byte[]> expectedValue = AttributeValue.of(text.getBytes(StandardCharsets.UTF_8));
        testee.service(mail);

        Optional<Map<String, AttributeValue<byte[]>>> processedAttribute = AttributeUtils.getValueAndCastFromMail(mail, MAIL_ATTRIBUTE, MAP_STRING_BYTES_CLASS);
        assertThat(processedAttribute).hasValueSatisfying(map ->
            assertThat(map.values().stream().findFirst().get().getValue())
                .contains(expectedValue.value()));
    }
}
