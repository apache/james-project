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

package org.apache.james.mailetcontainer.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import javax.mail.internet.MimeMessage;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.mailetcontainer.LocalResources;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.server.core.MailImpl;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.Mail;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.google.common.collect.ImmutableList;

public class JamesMailetContextTest {
    public static final Domain DOMAIN_COM = Domain.of("domain.com");
    public static final String USERNAME = "user";
    public static final Username USERMAIL = Username.of(USERNAME + "@" + DOMAIN_COM.name());
    public static final String PASSWORD = "password";
    public static final DNSService DNS_SERVICE = null;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();
    
    private MemoryDomainList domainList;
    private MemoryUsersRepository usersRepository;
    private JamesMailetContext testee;
    private MailAddress mailAddress;
    private MailQueue spoolMailQueue;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        domainList = new MemoryDomainList(DNS_SERVICE);
        domainList.configure(DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false)
            .build());

        usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);
        MemoryRecipientRewriteTable recipientRewriteTable = new MemoryRecipientRewriteTable();
        recipientRewriteTable.configure(new BaseHierarchicalConfiguration());
        MailQueueFactory<MailQueue> mailQueueFactory = mock(MailQueueFactory.class);
        spoolMailQueue = mock(MailQueue.class);
        when(mailQueueFactory.createQueue(MailQueueFactory.SPOOL)).thenReturn(spoolMailQueue);
        DNSService dnsService = null;
        LocalResources localResources = new LocalResources(usersRepository, domainList, recipientRewriteTable);
        testee = new JamesMailetContext(dnsService, domainList, localResources, mailQueueFactory);
        testee.configure(new BaseHierarchicalConfiguration());
        mailAddress = new MailAddress(USERMAIL.asString());
    }

    @Test
    public void isLocalUserShouldBeFalseOnNullUser() {
        assertThat(testee.isLocalUser(null)).isFalse();
    }

    @Test
    public void isLocalServerShouldBeFalseWhenDomainDoNotExist() {
        assertThat(testee.isLocalServer(DOMAIN_COM)).isFalse();
    }

    @Test
    public void isLocalServerShouldBeTrueWhenDomainExist() throws Exception {
        domainList.addDomain(DOMAIN_COM);

        assertThat(testee.isLocalServer(DOMAIN_COM)).isTrue();
    }

    @Test
    public void isLocalUserShouldBeTrueWhenUsernameExist() throws Exception {
        domainList.addDomain(DOMAIN_COM);
        usersRepository.addUser(USERMAIL, PASSWORD);

        assertThat(testee.isLocalUser(USERMAIL.asString())).isTrue();
    }

    @Test
    public void isLocalUserShouldReturnTrueWhenUsedWithLocalPartAndUserExistOnDefaultDomain() throws Exception {
        domainList.configure(DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false)
            .defaultDomain(DOMAIN_COM)
            .build());

        usersRepository.addUser(USERMAIL, PASSWORD);

        assertThat(testee.isLocalUser(USERNAME)).isTrue();
    }

    @Test
    public void isLocalUserShouldReturnFalseWhenUsedWithLocalPartAndUserDoNotExistOnDefaultDomain() throws Exception {
        domainList.configure(DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false)
            .defaultDomain(Domain.of("any"))
            .build());

        domainList.addDomain(DOMAIN_COM);
        usersRepository.addUser(USERMAIL, PASSWORD);

        assertThat(testee.isLocalUser(USERNAME)).isFalse();
    }

    @Test
    public void isLocalUserShouldBeFalseWhenUsernameDoNotExist() throws Exception {
        assertThat(testee.isLocalUser(USERMAIL.asString())).isFalse();
    }

    @Test
    public void isLocalEmailShouldBeFalseWhenUsernameDoNotExist() throws Exception {
        assertThat(testee.isLocalEmail(mailAddress)).isFalse();
    }

    @Test
    public void isLocalEmailShouldBeFalseWhenUsernameDoNotExistButDomainExists() throws Exception {
        domainList.addDomain(DOMAIN_COM);

        assertThat(testee.isLocalEmail(mailAddress)).isFalse();
    }

    @Test
    public void isLocalEmailShouldBeTrueWhenUsernameExists() throws Exception {
        domainList.addDomain(DOMAIN_COM);
        usersRepository.addUser(USERMAIL, PASSWORD);

        assertThat(testee.isLocalEmail(mailAddress)).isTrue();
    }

    @Test
    public void isLocalEmailShouldBeFalseWhenMailIsNull() throws Exception {
        assertThat(testee.isLocalEmail(null)).isFalse();
    }

    @Test
    public void bounceShouldNotFailWhenNonConfiguredPostmaster() throws Exception {
        MailImpl mail = MailImpl.builder()
            .name("mail1")
            .sender(mailAddress)
            .addRecipient(mailAddress)
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .build();
        testee.bounce(mail, "message");
    }

    @Test
    public void bouncingToNullSenderShouldBeANoop() throws Exception {
        MailImpl mail = MailImpl.builder()
            .name("mail1")
            .sender(MailAddress.nullSender())
            .addRecipient(mailAddress)
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .build();

        testee.bounce(mail, "message");

        verifyZeroInteractions(spoolMailQueue);
    }

    @Test
    public void bouncingToNoSenderShouldBeANoop() throws Exception {
        MailImpl mail = MailImpl.builder()
            .name("mail1")
            .addRecipient(mailAddress)
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .build();

        testee.bounce(mail, "message");

        verifyZeroInteractions(spoolMailQueue);
    }

    @Test
    public void bounceShouldEnqueueEmailWithRootState() throws Exception {
        MailImpl mail = MailImpl.builder()
            .name("mail1")
            .sender(mailAddress)
            .addRecipient(mailAddress)
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .build();

        testee.bounce(mail, "message");

        ArgumentCaptor<Mail> mailArgumentCaptor = ArgumentCaptor.forClass(Mail.class);
        verify(spoolMailQueue).enQueue(mailArgumentCaptor.capture());
        verifyNoMoreInteractions(spoolMailQueue);

        assertThat(mailArgumentCaptor.getValue().getState()).isEqualTo(Mail.DEFAULT);
    }

    @Test
    public void sendMailShouldEnqueueEmailWithRootState() throws Exception {
        MailImpl mail = MailImpl.builder()
            .name("mail1")
            .sender(mailAddress)
            .addRecipient(mailAddress)
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .build();
        testee.sendMail(mail);

        ArgumentCaptor<Mail> mailArgumentCaptor = ArgumentCaptor.forClass(Mail.class);
        verify(spoolMailQueue).enQueue(mailArgumentCaptor.capture());
        verifyNoMoreInteractions(spoolMailQueue);

        assertThat(mailArgumentCaptor.getValue().getState()).isEqualTo(Mail.DEFAULT);
    }

    @Test
    public void sendMailShouldEnqueueEmailWithOtherStateWhenSpecified() throws Exception {
        MailImpl mail = MailImpl.builder()
            .name("mail1")
            .sender(mailAddress)
            .addRecipient(mailAddress)
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .build();
        String other = "other";
        testee.sendMail(mail, other);

        ArgumentCaptor<Mail> mailArgumentCaptor = ArgumentCaptor.forClass(Mail.class);
        verify(spoolMailQueue).enQueue(mailArgumentCaptor.capture());
        verifyNoMoreInteractions(spoolMailQueue);

        assertThat(mailArgumentCaptor.getValue().getState()).isEqualTo(other);
    }

    @Test
    public void sendMailShouldEnqueueEmailWithRootStateAndDelayWhenSpecified() throws Exception {
        MailImpl mail = MailImpl.builder()
            .name("mail1")
            .sender(mailAddress)
            .addRecipient(mailAddress)
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .build();
        testee.sendMail(mail, 5, TimeUnit.MINUTES);

        ArgumentCaptor<Mail> mailArgumentCaptor = ArgumentCaptor.forClass(Mail.class);
        ArgumentCaptor<Long> delayArgumentCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<TimeUnit> timeUnitArgumentCaptor = ArgumentCaptor.forClass(TimeUnit.class);
        verify(spoolMailQueue).enQueue(mailArgumentCaptor.capture(), delayArgumentCaptor.capture(), timeUnitArgumentCaptor.capture());
        verifyNoMoreInteractions(spoolMailQueue);

        softly.assertThat(mailArgumentCaptor.getValue().getState()).isEqualTo(Mail.DEFAULT);
        softly.assertThat(delayArgumentCaptor.getValue()).isEqualTo(5L);
        softly.assertThat(timeUnitArgumentCaptor.getValue()).isEqualTo(TimeUnit.MINUTES);
    }

    @Test
    public void sendMailShouldEnqueueEmailWithOtherStateAndDelayWhenSpecified() throws Exception {
        MailImpl mail = MailImpl.builder()
            .name("mail1")
            .sender(mailAddress)
            .addRecipient(mailAddress)
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .build();
        String other = "other";
        testee.sendMail(mail, other, 5, TimeUnit.MINUTES);

        ArgumentCaptor<Mail> mailArgumentCaptor = ArgumentCaptor.forClass(Mail.class);
        ArgumentCaptor<Long> delayArgumentCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<TimeUnit> timeUnitArgumentCaptor = ArgumentCaptor.forClass(TimeUnit.class);
        verify(spoolMailQueue).enQueue(mailArgumentCaptor.capture(), delayArgumentCaptor.capture(), timeUnitArgumentCaptor.capture());
        verifyNoMoreInteractions(spoolMailQueue);

        softly.assertThat(mailArgumentCaptor.getValue().getState()).isEqualTo(other);
        softly.assertThat(delayArgumentCaptor.getValue()).isEqualTo(5L);
        softly.assertThat(timeUnitArgumentCaptor.getValue()).isEqualTo(TimeUnit.MINUTES);
    }

    @Test
    public void sendMailForMessageShouldEnqueueEmailWithRootState() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom(mailAddress.asString())
            .addToRecipient(mailAddress.asString())
            .setText("Simple text")
            .build();

        testee.sendMail(message);

        ArgumentCaptor<Mail> mailArgumentCaptor = ArgumentCaptor.forClass(Mail.class);
        verify(spoolMailQueue).enQueue(mailArgumentCaptor.capture());
        verifyNoMoreInteractions(spoolMailQueue);

        assertThat(mailArgumentCaptor.getValue().getState()).isEqualTo(Mail.DEFAULT);
    }

    @Test
    public void sendMailForMessageAndEnvelopeShouldEnqueueEmailWithRootState() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom(mailAddress.asString())
            .addToRecipient(mailAddress.asString())
            .setText("Simple text")
            .build();

        MailAddress sender = mailAddress;
        ImmutableList<MailAddress> recipients = ImmutableList.of(mailAddress);
        testee.sendMail(sender, recipients, message);

        ArgumentCaptor<Mail> mailArgumentCaptor = ArgumentCaptor.forClass(Mail.class);
        verify(spoolMailQueue).enQueue(mailArgumentCaptor.capture());
        verifyNoMoreInteractions(spoolMailQueue);

        assertThat(mailArgumentCaptor.getValue().getState()).isEqualTo(Mail.DEFAULT);
    }

    @Test
    public void sendMailForMessageAndEnvelopeShouldEnqueueEmailWithOtherStateWhenSpecified() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom(mailAddress.asString())
            .addToRecipient(mailAddress.asString())
            .setText("Simple text")
            .build();

        MailAddress sender = mailAddress;
        ImmutableList<MailAddress> recipients = ImmutableList.of(mailAddress);
        String otherState = "other";
        testee.sendMail(sender, recipients, message, otherState);

        ArgumentCaptor<Mail> mailArgumentCaptor = ArgumentCaptor.forClass(Mail.class);
        verify(spoolMailQueue).enQueue(mailArgumentCaptor.capture());
        verifyNoMoreInteractions(spoolMailQueue);

        assertThat(mailArgumentCaptor.getValue().getState()).isEqualTo(otherState);
    }

    @Test
    public void sendMailForMailShouldEnqueueEmailWithOtherStateWhenSpecified() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom(mailAddress.asString())
            .addToRecipient(mailAddress.asString())
            .setText("Simple text")
            .build();

        String otherState = "other";
        testee.sendMail(FakeMail.builder()
            .name("name")
            .sender(MailAddressFixture.SENDER)
            .recipient(MailAddressFixture.RECIPIENT1)
            .mimeMessage(message)
            .state(otherState)
            .build());

        ArgumentCaptor<Mail> mailArgumentCaptor = ArgumentCaptor.forClass(Mail.class);
        verify(spoolMailQueue).enQueue(mailArgumentCaptor.capture());
        verifyNoMoreInteractions(spoolMailQueue);

        assertThat(mailArgumentCaptor.getValue().getState()).isEqualTo(otherState);
    }

    @Test
    public void sendMailForMailShouldEnqueueEmailWithDefaults() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom(mailAddress.asString())
            .addToRecipient(mailAddress.asString())
            .setText("Simple text")
            .build();

        testee.sendMail(FakeMail.builder()
            .name("name")
            .sender(MailAddressFixture.SENDER)
            .recipient(MailAddressFixture.RECIPIENT1)
            .mimeMessage(message)
            .build());

        ArgumentCaptor<Mail> mailArgumentCaptor = ArgumentCaptor.forClass(Mail.class);
        verify(spoolMailQueue).enQueue(mailArgumentCaptor.capture());
        verifyNoMoreInteractions(spoolMailQueue);

        assertThat(mailArgumentCaptor.getValue().getState()).isEqualTo(Mail.DEFAULT);
    }
}
