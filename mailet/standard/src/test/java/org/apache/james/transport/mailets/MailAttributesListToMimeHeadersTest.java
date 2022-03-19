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

import java.util.ArrayList;

import jakarta.mail.MessagingException;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mailet;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.apache.mailet.base.test.MailUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class MailAttributesListToMimeHeadersTest {

    private static final String VALUE_1_1 = "test1.1";
    private static final String VALUE_1_2 = "test1.2";
    private static final String VALUE_2_1 = "test2.1";
    private static final String VALUE_2_2 = "test2.2";
    private static final ImmutableList<AttributeValue<String>> MAIL_ATTRIBUTE_VALUE1 = ImmutableList.of(AttributeValue.of(VALUE_1_1), AttributeValue.of(VALUE_1_2));
    private static final ImmutableList<AttributeValue<String>> MAIL_ATTRIBUTE_VALUE2 = ImmutableList.of(AttributeValue.of(VALUE_2_1), AttributeValue.of(VALUE_2_2));

    private static final String MAIL_ATTRIBUTE_NAME1 = "org.apache.james.test";
    private static final String MAIL_ATTRIBUTE_NAME2 = "org.apache.james.test2";
    private static final String HEADER_NAME1 = "JUNIT";
    private static final String HEADER_NAME2 = "JUNIT2";

    private static final Attribute MAIL_ATTRIBUTE1 = Attribute.convertToAttribute(MAIL_ATTRIBUTE_NAME1, MAIL_ATTRIBUTE_VALUE1);
    private static final Attribute MAIL_ATTRIBUTE2 = Attribute.convertToAttribute(MAIL_ATTRIBUTE_NAME2, MAIL_ATTRIBUTE_VALUE2);

    private Mailet mailet;

    @BeforeEach
    void setup() {
        mailet = new MailAttributesListToMimeHeaders();
    }

    @Test
    void shouldThrowMessagingExceptionIfMappingIsNotGiven() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .build();

        assertThatThrownBy(() -> mailet.init(mailetConfig))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void shouldThrowMessagingExceptionIfMappingIsEmpty() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("simplemmapping", "")
            .build();

        assertThatThrownBy(() -> mailet.init(mailetConfig))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void shouldIgnoreAttributeOfMappingThatDoesNotExistOnTheMessage() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("simplemapping",
                MAIL_ATTRIBUTE_NAME1 + "; " + HEADER_NAME1 +
                    "," + MAIL_ATTRIBUTE_NAME2 + "; " + HEADER_NAME2 +
                    "," + "another.attribute" + "; " + "Another-Header")
            .build();

        mailet.init(mailetConfig);

        FakeMail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(MailUtil.createMimeMessage())
            .attribute(MAIL_ATTRIBUTE1)
            .attribute(MAIL_ATTRIBUTE2)
            .build();

        mailet.service(mail);
        assertThat(mail.getMessage().getHeader("another.attribute")).isNull();
    }

    @Test
    void shouldWorkWithMappingWithASingleBinding() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("simplemapping",
                MAIL_ATTRIBUTE_NAME1 + "; " + HEADER_NAME1)
            .build();

        mailet.init(mailetConfig);

        FakeMail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(MailUtil.createMimeMessage())
            .attribute(MAIL_ATTRIBUTE1)
            .build();

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader(HEADER_NAME1))
            .containsExactly(VALUE_1_1, VALUE_1_2);
    }

    @Test
    void shouldPutAttributesIntoHeadersWhenMappingDefined() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("simplemapping",
                        MAIL_ATTRIBUTE_NAME1 + "; " + HEADER_NAME1 +
                        "," + MAIL_ATTRIBUTE_NAME2 + "; " + HEADER_NAME2 +
                        "," + "another.attribute" + "; " + "Another-Header")
                .build();
        mailet.init(mailetConfig);

        FakeMail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(MailUtil.createMimeMessage())
            .attribute(MAIL_ATTRIBUTE1)
            .attribute(MAIL_ATTRIBUTE2)
            .attribute(Attribute.convertToAttribute("unmatched.attribute", "value"))
            .build();

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader(HEADER_NAME1))
            .containsExactlyElementsOf(MAIL_ATTRIBUTE_VALUE1.stream()
                .map(AttributeValue::getValue)
                .collect(ImmutableList.toImmutableList()));

        assertThat(mail.getMessage().getHeader(HEADER_NAME2))
            .containsExactlyElementsOf(MAIL_ATTRIBUTE_VALUE2.stream()
                .map(AttributeValue::getValue)
                .collect(ImmutableList.toImmutableList()));
    }

    @Test
    void shouldNotRemovePreviousAttributeValueWhenAttributeAlreadyPresent() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("simplemapping", MAIL_ATTRIBUTE_NAME1 + "; " + HEADER_NAME1)
                .build();
        mailet.init(mailetConfig);

        String firstValue = "first value";
        FakeMail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addHeader(HEADER_NAME1, firstValue))
            .attribute(MAIL_ATTRIBUTE1)
            .build();

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader(HEADER_NAME1))
            .containsOnly(VALUE_1_1, VALUE_1_2, firstValue);
    }

    @Test
    void shouldFilterAttributeOfWrongClass() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("simplemapping",
                MAIL_ATTRIBUTE_NAME1 + "; " + HEADER_NAME1 +
                    "," + MAIL_ATTRIBUTE_NAME2 + "; " + HEADER_NAME2)
            .build();
        mailet.init(mailetConfig);

        FakeMail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder())
            .attribute(Attribute.convertToAttribute(MAIL_ATTRIBUTE_NAME1, 3L))
            .attribute(MAIL_ATTRIBUTE2)
            .build();

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader(HEADER_NAME1)).isNull();
        assertThat(mail.getMessage().getHeader(HEADER_NAME2))
            .containsExactlyElementsOf(MAIL_ATTRIBUTE_VALUE2.stream()
                .map(AttributeValue::getValue)
                .collect(ImmutableList.toImmutableList()));
    }


    @Test
    void shouldFilterAttributeElementsOfWrongClass() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("simplemapping", MAIL_ATTRIBUTE_NAME1 + "; " + HEADER_NAME1)
            .build();
        mailet.init(mailetConfig);

        String value = "value";
        FakeMail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder())
            .attribute(Attribute.convertToAttribute(MAIL_ATTRIBUTE_NAME1, ImmutableList.of(AttributeValue.of(3L), AttributeValue.of(value))))
            .build();

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader(HEADER_NAME1)).containsOnly(value);
    }


    @Test
    void shouldThrowAtInitWhenNoSemicolumnInConfigurationEntry() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("simplemapping", "invalidConfigEntry")
                .build();

        assertThatThrownBy(() -> mailet.init(mailetConfig))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowAtInitWhenTwoSemicolumnsInConfigurationEntry() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("simplemapping", "first;second;third")
                .build();

        assertThatThrownBy(() -> mailet.init(mailetConfig))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowAtInitWhenNoConfigurationEntry() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .build();

        assertThatThrownBy(() -> mailet.init(mailetConfig))
            .isInstanceOf(MessagingException.class);
    }
}
