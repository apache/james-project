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

package org.apache.mailet.base.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.mailet.ContractMailTest;
import org.apache.mailet.base.MailAddressFixture;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class FakeMailTest extends ContractMailTest {

    @Override
    public FakeMail newMail() {
        try {
            return FakeMail.builder().name("mail").build();
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void beanShouldRespectBeanContract() {
        EqualsVerifier.forClass(FakeMail.class)
            .suppress(Warning.NONFINAL_FIELDS)
            .withPrefabValues(MimeMessage.class, mock(MimeMessage.class), mock(MimeMessage.class))
            .verify();
    }

    @Test
    public void getMaybeSenderShouldHandleNullSender() throws MessagingException {
        assertThat(
            FakeMail.builder()
                .name("mail")
                .sender(MailAddress.nullSender())
                .build()
                .getMaybeSender())
            .isEqualTo(MaybeSender.nullSender());
    }

    @Test
    public void getMaybeSenderShouldHandleNoSender() throws MessagingException {
        assertThat(
            FakeMail.builder()
                .name("mail")
                .build()
                .getMaybeSender())
            .isEqualTo(MaybeSender.nullSender());
    }

    @Test
    public void getMaybeSenderShouldHandleSender() throws MessagingException {
        assertThat(
            FakeMail.builder()
                .name("mail")
                .sender(MailAddressFixture.SENDER)
                .build()
                .getMaybeSender())
            .isEqualTo(MaybeSender.of(MailAddressFixture.SENDER));
    }

    @Test
    public void hasSenderShouldReturnFalseWhenSenderIsNull() throws MessagingException {
        assertThat(
            FakeMail.builder()
                .name("mail")
                .sender(MailAddress.nullSender())
                .build()
                .hasSender())
            .isFalse();
    }

    @Test
    public void hasSenderShouldReturnFalseWhenSenderIsNotSpecified() throws MessagingException {
        assertThat(
            FakeMail.builder()
                .name("mail")
                .build()
                .hasSender())
            .isFalse();
    }

    @Test
    public void hasSenderShouldReturnTrueWhenSenderIsSpecified() throws MessagingException {
        assertThat(
            FakeMail.builder()
                .name("mail")
                .sender(MailAddressFixture.SENDER)
                .build()
                .hasSender())
            .isTrue();
    }
}
