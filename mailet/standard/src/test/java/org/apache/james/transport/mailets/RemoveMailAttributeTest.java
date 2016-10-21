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

import javax.mail.MessagingException;

import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Test;

public class RemoveMailAttributeTest {

    private static final String ATTRIBUTE_1 = "attribute1";
    private static final String ATTRIBUTE_2 = "attribute2";
    private static final String ATTRIBUTE_3 = "attribute3";
    private static final String VALUE_1 = "value1";
    private static final String VALUE_2 = "value2";
    private static final String VALUE_3 = "value3";
    private static final String ATTRIBUTE1_ATTRIBUTE2 = "attribute1, attribute2";
    private Mailet removeMailet;
    private FakeMailetConfig mailetConfig;

    @Before
    public void setup() throws Exception {
        removeMailet = new RemoveMailAttribute();
        mailetConfig = new FakeMailetConfig("Test", FakeMailContext.defaultContext());
    }

    @Test
    public void getMailetInfoShouldReturnCorrectInformation() throws Exception {
        assertThat(removeMailet.getMailetInfo()).isEqualTo("Remove Mail Attribute Mailet");
    }

    @Test(expected = MailetException.class)
    public void initShouldThrowExceptionIfMailetConfigDoesNotContainAttribute() throws MessagingException {
        removeMailet.init(mailetConfig);
    }

    @Test(expected = NullPointerException.class)
    public void serviceShouldThrowExceptionWithMailNull() throws MessagingException {
        removeMailet.service(null);
    }

    @Test
    public void serviceShouldDoNothingWhenMailHasEmptyAttribute() throws MessagingException {
        mailetConfig.setProperty(RemoveMailAttribute.MAILET_NAME_PARAMETER, ATTRIBUTE1_ATTRIBUTE2);
        removeMailet.init(mailetConfig);

        Mail mail = FakeMail.builder().build();
        removeMailet.service(mail);

        assertThat(mail.getAttributeNames()).isEmpty();
    }

    @Test
    public void serviceShouldDoNothingWhenMailDoNotMatchAttribute() throws MessagingException {
        mailetConfig.setProperty(RemoveMailAttribute.MAILET_NAME_PARAMETER, ATTRIBUTE1_ATTRIBUTE2);
        removeMailet.init(mailetConfig);

        Mail mail = FakeMail.builder()
            .attribute(ATTRIBUTE_3, VALUE_3)
            .build();
        removeMailet.service(mail);

        assertThat(mail.getAttributeNames()).containsExactly(ATTRIBUTE_3);
    }

    @Test
    public void serviceShouldRemoveSpecifiedAttribute() throws MessagingException {
        mailetConfig.setProperty(RemoveMailAttribute.MAILET_NAME_PARAMETER, ATTRIBUTE_1);
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
    public void serviceShouldRemoveSpecifiedAttributes() throws MessagingException {
        mailetConfig.setProperty(RemoveMailAttribute.MAILET_NAME_PARAMETER, ATTRIBUTE1_ATTRIBUTE2);
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
