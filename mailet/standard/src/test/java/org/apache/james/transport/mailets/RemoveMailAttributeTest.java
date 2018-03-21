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

import javax.mail.MessagingException;

import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RemoveMailAttributeTest {

    private static final String ATTRIBUTE_1 = "attribute1";
    private static final String ATTRIBUTE_2 = "attribute2";
    private static final String ATTRIBUTE_3 = "attribute3";
    private static final String VALUE_1 = "value1";
    private static final String VALUE_2 = "value2";
    private static final String VALUE_3 = "value3";
    private static final String ATTRIBUTE1_ATTRIBUTE2 = "attribute1, attribute2";
    private Mailet removeMailet;

    @BeforeEach
    void setup() {
        removeMailet = new RemoveMailAttribute();
    }

    @Test
    void getMailetInfoShouldReturnCorrectInformation() {
        assertThat(removeMailet.getMailetInfo()).isEqualTo("Remove Mail Attribute Mailet");
    }

    @Test
    void initShouldThrowExceptionIfMailetConfigDoesNotContainAttribute() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .build();
        assertThatThrownBy(() -> removeMailet.init(mailetConfig)).isInstanceOf(MailetException.class);
    }

    @Test
    void serviceShouldThrowExceptionWithMailNull() {
        assertThatThrownBy(() -> removeMailet.service(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void serviceShouldDoNothingWhenMailHasEmptyAttribute() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty(RemoveMailAttribute.MAILET_NAME_PARAMETER, ATTRIBUTE1_ATTRIBUTE2)
                .build();
        removeMailet.init(mailetConfig);

        Mail mail = FakeMail.builder().build();
        removeMailet.service(mail);

        assertThat(mail.getAttributeNames()).isEmpty();
    }

    @Test
    void serviceShouldDoNothingWhenMailDoNotMatchAttribute() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty(RemoveMailAttribute.MAILET_NAME_PARAMETER, ATTRIBUTE1_ATTRIBUTE2)
                .build();
        removeMailet.init(mailetConfig);

        Mail mail = FakeMail.builder()
            .attribute(ATTRIBUTE_3, VALUE_3)
            .build();
        removeMailet.service(mail);

        assertThat(mail.getAttributeNames()).containsExactly(ATTRIBUTE_3);
    }

    @Test
    void serviceShouldRemoveSpecifiedAttribute() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty(RemoveMailAttribute.MAILET_NAME_PARAMETER, ATTRIBUTE_1)
                .build();
        removeMailet.init(mailetConfig);

        Mail mail = FakeMail.builder()
            .attribute(ATTRIBUTE_1, VALUE_1)
            .attribute(ATTRIBUTE_2, VALUE_2)
            .attribute(ATTRIBUTE_3, VALUE_3)
            .build();
        removeMailet.service(mail);

        assertThat(mail.getAttributeNames()).containsOnly(ATTRIBUTE_2, ATTRIBUTE_3);
    }

    @Test
    void serviceShouldRemoveSpecifiedAttributes() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty(RemoveMailAttribute.MAILET_NAME_PARAMETER, ATTRIBUTE1_ATTRIBUTE2)
                .build();
        removeMailet.init(mailetConfig);

        Mail mail = FakeMail.builder()
            .attribute(ATTRIBUTE_1, VALUE_1)
            .attribute(ATTRIBUTE_2, VALUE_2)
            .attribute(ATTRIBUTE_3, VALUE_3)
            .build();
        removeMailet.service(mail);

        assertThat(mail.getAttributeNames()).containsExactly(ATTRIBUTE_3);
    }
}
