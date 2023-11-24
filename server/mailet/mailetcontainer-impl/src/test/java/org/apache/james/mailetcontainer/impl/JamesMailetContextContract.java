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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import javax.mail.internet.MimeMessage;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.server.core.MailImpl;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.Mail;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.google.common.collect.ImmutableList;

public interface JamesMailetContextContract {
    Domain DOMAIN_COM = Domain.of("domain.com");
    String USERNAME = "user";
    Username USERMAIL = Username.of(USERNAME + "@" + DOMAIN_COM.name());
    String PASSWORD = "password";

    DomainList domainList(DomainListConfiguration configuration);

    default DomainList domainList() {
        return domainList(DomainListConfiguration.DEFAULT);
    }

    UsersRepository usersRepository();

    JamesMailetContext testee();

    MailAddress mailAddress();

    MailQueue spoolMailQueue();

    AbstractRecipientRewriteTable recipientRewriteTable();

    @Test
    default void isLocalUserShouldBeFalseOnNullUser() {
        assertThat(testee().isLocalUser(null)).isFalse();
    }

    @Test
    default void isLocalServerShouldBeFalseWhenDomainDoNotExist() {
        assertThat(testee().isLocalServer(DOMAIN_COM)).isFalse();
    }

    @Test
    default void isLocalServerShouldPropagateDomainExceptions() throws Exception {
        when(domainList().containsDomain(any())).thenThrow(new DomainListException("fail!"));

        assertThatThrownBy(() -> testee().isLocalServer(DOMAIN_COM))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    default void isLocalUserShouldPropagateDomainExceptions() throws Exception {
        when(domainList().getDefaultDomain()).thenThrow(new DomainListException("fail!"));

        assertThatThrownBy(() -> testee().isLocalUser("user"))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    default void isLocalUserShouldPropagateUserExceptions() throws Exception {
        domainList().configure(DomainListConfiguration.builder()
            .defaultDomain(Domain.of("any"))
            .build());
        domainList().addDomain(DOMAIN_COM);

        doThrow(new UsersRepositoryException("fail!")).when(usersRepository()).contains(any());

        assertThatThrownBy(() -> testee().isLocalUser(USERNAME))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    default void isLocalUserShouldPropagateRrtExceptions() throws Exception {
        domainList().configure(DomainListConfiguration.builder()
            .defaultDomain(Domain.of("any"))
            .build());
        domainList().addDomain(DOMAIN_COM);

        doThrow(new RecipientRewriteTableException("fail!")).when(recipientRewriteTable()).getResolvedMappings(any(), any(), any());

        assertThatThrownBy(() -> testee().isLocalUser(USERNAME))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    default void isLocalServerShouldBeTrueWhenDomainExist() throws Exception {
        domainList().addDomain(DOMAIN_COM);

        assertThat(testee().isLocalServer(DOMAIN_COM)).isTrue();
    }

    @Test
    default void isLocalUserShouldBeTrueWhenUsernameExist() throws Exception {
        domainList().addDomain(DOMAIN_COM);
        usersRepository().addUser(USERMAIL, PASSWORD);

        assertThat(testee().isLocalUser(USERMAIL.asString())).isTrue();
    }

    @Test
    default void isLocalUserShouldReturnTrueWhenUsedWithLocalPartAndUserExistOnDefaultDomain() throws Exception {
        domainList().configure(DomainListConfiguration.builder()
            .defaultDomain(DOMAIN_COM)
            .build());

        usersRepository().addUser(USERMAIL, PASSWORD);

        assertThat(testee().isLocalUser(USERNAME)).isTrue();
    }

    @Test
    default void isLocalUserShouldReturnFalseWhenUsedWithLocalPartAndUserDoNotExistOnDefaultDomain() throws Exception {
        domainList().configure(DomainListConfiguration.builder()
            .defaultDomain(Domain.of("any"))
            .build());

        domainList().addDomain(DOMAIN_COM);
        usersRepository().addUser(USERMAIL, PASSWORD);

        assertThat(testee().isLocalUser(USERNAME)).isFalse();
    }

    @Test
    default void isLocalUserShouldBeFalseWhenUsernameDoNotExist() {
        assertThat(testee().isLocalUser(USERMAIL.asString())).isFalse();
    }

    @Test
    default void isLocalEmailShouldBeFalseWhenUsernameDoNotExist() {
        assertThat(testee().isLocalEmail(mailAddress())).isFalse();
    }

    @Test
    default void isLocalEmailShouldBeFalseWhenUsernameDoNotExistButDomainExists() throws Exception {
        domainList().addDomain(DOMAIN_COM);

        assertThat(testee().isLocalEmail(mailAddress())).isFalse();
    }

    @Test
    default void isLocalEmailShouldBeTrueWhenUsernameExists() throws Exception {
        domainList().addDomain(DOMAIN_COM);
        usersRepository().addUser(USERMAIL, PASSWORD);

        assertThat(testee().isLocalEmail(mailAddress())).isTrue();
    }

    @Test
    default void localRecipientsShouldReturnAddressWhenUserExists() throws Exception {
        domainList().addDomain(DOMAIN_COM);
        usersRepository().addUser(USERMAIL, PASSWORD);

        assertThat(testee().localRecipients(ImmutableList.of(mailAddress()))).containsOnly(mailAddress());
    }

    @Test
    default void localRecipientsShouldReturnOnlyExistingUsers() throws Exception {
        domainList().addDomain(DOMAIN_COM);
        usersRepository().addUser(USERMAIL, PASSWORD);

        assertThat(testee().localRecipients(
            ImmutableList.of(mailAddress(),
                MailAddressFixture.RECIPIENT2)))
            .containsOnly(mailAddress());
    }

    @Test
    default void localRecipientsShouldNotReturnAddressWhenUserDoNotExists() throws Exception {
        domainList().addDomain(DOMAIN_COM);

        assertThat(testee().localRecipients(ImmutableList.of(mailAddress()))).isEmpty();
    }

    @Test
    default void localRecipientsShouldNotReturnAddressWhenDomainDoNotExists() throws Exception {
        assertThat(testee().localRecipients(ImmutableList.of(mailAddress()))).isEmpty();
    }

    @Test
    default void isLocalEmailShouldBeFalseWhenMailIsNull() {
        assertThat(testee().isLocalEmail(null)).isFalse();
    }

    @Test
    default void isLocalEmailShouldPropagateDomainExceptions() throws Exception {
        when(domainList().containsDomain(any())).thenThrow(new DomainListException("fail!"));

        assertThatThrownBy(() -> testee().isLocalEmail(mailAddress()))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    default void isLocalEmailShouldPropagateUserExceptions() throws Exception {
        domainList().configure(DomainListConfiguration.builder()
            .defaultDomain(Domain.of("any"))
            .build());
        domainList().addDomain(DOMAIN_COM);

        doThrow(new UsersRepositoryException("fail!")).when(usersRepository()).contains(any());

        assertThatThrownBy(() -> testee().isLocalEmail(mailAddress()))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    default void isLocalEmailShouldPropagateRrtExceptions() throws Exception {
        domainList().configure(DomainListConfiguration.builder()
            .defaultDomain(Domain.of("any"))
            .build());
        domainList().addDomain(DOMAIN_COM);

        doThrow(new RecipientRewriteTableException("fail!")).when(recipientRewriteTable()).getResolvedMappings(any(), any(), any());

        assertThatThrownBy(() -> testee().isLocalEmail(mailAddress()))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    default void bounceShouldNotFailWhenNonConfiguredPostmaster() throws Exception {
        MailImpl mail = MailImpl.builder()
            .name("mail1")
            .sender(mailAddress())
            .addRecipient(mailAddress())
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes("header: value\r\n".getBytes(UTF_8)))
            .build();
        testee().bounce(mail, "message");
    }

    @Test
    default void bouncingToNullSenderShouldBeANoop() throws Exception {
        MailImpl mail = MailImpl.builder()
            .name("mail1")
            .sender(mailAddress().nullSender())
            .addRecipient(mailAddress())
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes("header: value\r\n".getBytes(UTF_8)))
            .build();

        testee().bounce(mail, "message");

        verifyNoMoreInteractions(spoolMailQueue());
    }

    @Test
    default void bouncingToNoSenderShouldBeANoop() throws Exception {
        MailImpl mail = MailImpl.builder()
            .name("mail1")
            .addRecipient(mailAddress())
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes("header: value\r\n".getBytes(UTF_8)))
            .build();

        testee().bounce(mail, "message");

        verifyNoMoreInteractions(spoolMailQueue());
    }

    @Test
    default void bounceShouldEnqueueEmailWithRootState() throws Exception {
        MailImpl mail = MailImpl.builder()
            .name("mail1")
            .sender(mailAddress())
            .addRecipient(mailAddress())
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes("header: value\r\n".getBytes(UTF_8)))
            .build();

        testee().bounce(mail, "message");

        ArgumentCaptor<Mail> mailArgumentCaptor = ArgumentCaptor.forClass(Mail.class);
        verify(spoolMailQueue()).enQueue(mailArgumentCaptor.capture());
        verifyNoMoreInteractions(spoolMailQueue());

        assertThat(mailArgumentCaptor.getValue().getState()).isEqualTo(Mail.DEFAULT);
    }

    @Test
    default void sendMailShouldEnqueueEmailWithRootState() throws Exception {
        MailImpl mail = MailImpl.builder()
            .name("mail1")
            .sender(mailAddress())
            .addRecipient(mailAddress())
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes("header: value\r\n".getBytes(UTF_8)))
            .build();
        testee().sendMail(mail);

        ArgumentCaptor<Mail> mailArgumentCaptor = ArgumentCaptor.forClass(Mail.class);
        verify(spoolMailQueue()).enQueue(mailArgumentCaptor.capture());
        verifyNoMoreInteractions(spoolMailQueue());

        assertThat(mailArgumentCaptor.getValue().getState()).isEqualTo(Mail.DEFAULT);
    }

    @Test
    default void sendMailShouldEnqueueEmailWithOtherStateWhenSpecified() throws Exception {
        MailImpl mail = MailImpl.builder()
            .name("mail1")
            .sender(mailAddress())
            .addRecipient(mailAddress())
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes("header: value\r\n".getBytes(UTF_8)))
            .build();
        String other = "other";
        testee().sendMail(mail, other);

        ArgumentCaptor<Mail> mailArgumentCaptor = ArgumentCaptor.forClass(Mail.class);
        verify(spoolMailQueue()).enQueue(mailArgumentCaptor.capture());
        verifyNoMoreInteractions(spoolMailQueue());

        assertThat(mailArgumentCaptor.getValue().getState()).isEqualTo(other);
    }

    @Test
    default void sendMailShouldEnqueueEmailWithRootStateAndDelayWhenSpecified() throws Exception {
        MailImpl mail = MailImpl.builder()
            .name("mail1")
            .sender(mailAddress())
            .addRecipient(mailAddress())
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes("header: value\r\n".getBytes(UTF_8)))
            .build();
        testee().sendMail(mail, 5, TimeUnit.MINUTES);

        ArgumentCaptor<Mail> mailArgumentCaptor = ArgumentCaptor.forClass(Mail.class);
        ArgumentCaptor<Long> delayArgumentCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<TimeUnit> timeUnitArgumentCaptor = ArgumentCaptor.forClass(TimeUnit.class);
        verify(spoolMailQueue()).enQueue(mailArgumentCaptor.capture(), delayArgumentCaptor.capture(), timeUnitArgumentCaptor.capture());
        verifyNoMoreInteractions(spoolMailQueue());

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(mailArgumentCaptor.getValue().getState()).isEqualTo(Mail.DEFAULT);
            softly.assertThat(delayArgumentCaptor.getValue()).isEqualTo(5L);
            softly.assertThat(timeUnitArgumentCaptor.getValue()).isEqualTo(TimeUnit.MINUTES);
        });
    }

    @Test
    default void sendMailShouldEnqueueEmailWithOtherStateAndDelayWhenSpecified() throws Exception {
        MailImpl mail = MailImpl.builder()
            .name("mail1")
            .sender(mailAddress())
            .addRecipient(mailAddress())
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes("header: value\r\n".getBytes(UTF_8)))
            .build();
        String other = "other";
        testee().sendMail(mail, other, 5, TimeUnit.MINUTES);

        ArgumentCaptor<Mail> mailArgumentCaptor = ArgumentCaptor.forClass(Mail.class);
        ArgumentCaptor<Long> delayArgumentCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<TimeUnit> timeUnitArgumentCaptor = ArgumentCaptor.forClass(TimeUnit.class);
        verify(spoolMailQueue()).enQueue(mailArgumentCaptor.capture(), delayArgumentCaptor.capture(), timeUnitArgumentCaptor.capture());
        verifyNoMoreInteractions(spoolMailQueue());

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(mailArgumentCaptor.getValue().getState()).isEqualTo(other);
            softly.assertThat(delayArgumentCaptor.getValue()).isEqualTo(5L);
            softly.assertThat(timeUnitArgumentCaptor.getValue()).isEqualTo(TimeUnit.MINUTES);
        });
    }

    @Test
    default void sendMailForMessageShouldEnqueueEmailWithRootState() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom(mailAddress().asString())
            .addToRecipient(mailAddress().asString())
            .setText("Simple text")
            .build();

        testee().sendMail(message);

        ArgumentCaptor<Mail> mailArgumentCaptor = ArgumentCaptor.forClass(Mail.class);
        verify(spoolMailQueue()).enQueue(mailArgumentCaptor.capture());
        verifyNoMoreInteractions(spoolMailQueue());

        assertThat(mailArgumentCaptor.getValue().getState()).isEqualTo(Mail.DEFAULT);
    }

    @Test
    default void sendMailForMessageAndEnvelopeShouldEnqueueEmailWithRootState() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom(mailAddress().asString())
            .addToRecipient(mailAddress().asString())
            .setText("Simple text")
            .build();

        MailAddress sender = mailAddress();
        ImmutableList<MailAddress> recipients = ImmutableList.of(mailAddress());
        testee().sendMail(sender, recipients, message);

        ArgumentCaptor<Mail> mailArgumentCaptor = ArgumentCaptor.forClass(Mail.class);
        verify(spoolMailQueue()).enQueue(mailArgumentCaptor.capture());
        verifyNoMoreInteractions(spoolMailQueue());

        assertThat(mailArgumentCaptor.getValue().getState()).isEqualTo(Mail.DEFAULT);
    }

    @Test
    default void sendMailForMessageAndEnvelopeShouldEnqueueEmailWithOtherStateWhenSpecified() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom(mailAddress().asString())
            .addToRecipient(mailAddress().asString())
            .setText("Simple text")
            .build();

        MailAddress sender = mailAddress();
        ImmutableList<MailAddress> recipients = ImmutableList.of(mailAddress());
        String otherState = "other";
        testee().sendMail(sender, recipients, message, otherState);

        ArgumentCaptor<Mail> mailArgumentCaptor = ArgumentCaptor.forClass(Mail.class);
        verify(spoolMailQueue()).enQueue(mailArgumentCaptor.capture());
        verifyNoMoreInteractions(spoolMailQueue());

        assertThat(mailArgumentCaptor.getValue().getState()).isEqualTo(otherState);
    }

    @Test
    default void sendMailForMailShouldEnqueueEmailWithOtherStateWhenSpecified() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom(mailAddress().asString())
            .addToRecipient(mailAddress().asString())
            .setText("Simple text")
            .build();

        String otherState = "other";
        testee().sendMail(FakeMail.builder()
            .name("name")
            .sender(MailAddressFixture.SENDER)
            .recipient(MailAddressFixture.RECIPIENT1)
            .mimeMessage(message)
            .state(otherState)
            .build());

        ArgumentCaptor<Mail> mailArgumentCaptor = ArgumentCaptor.forClass(Mail.class);
        verify(spoolMailQueue()).enQueue(mailArgumentCaptor.capture());
        verifyNoMoreInteractions(spoolMailQueue());

        assertThat(mailArgumentCaptor.getValue().getState()).isEqualTo(otherState);
    }

    @Test
    default void sendMailForMailShouldEnqueueEmailWithDefaults() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom(mailAddress().asString())
            .addToRecipient(mailAddress().asString())
            .setText("Simple text")
            .build();

        testee().sendMail(FakeMail.builder()
            .name("name")
            .sender(MailAddressFixture.SENDER)
            .recipient(MailAddressFixture.RECIPIENT1)
            .mimeMessage(message)
            .build());

        ArgumentCaptor<Mail> mailArgumentCaptor = ArgumentCaptor.forClass(Mail.class);
        verify(spoolMailQueue()).enQueue(mailArgumentCaptor.capture());
        verifyNoMoreInteractions(spoolMailQueue());

        assertThat(mailArgumentCaptor.getValue().getState()).isEqualTo(Mail.DEFAULT);
    }
}
