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

package org.apache.james.transport.mailets.remote.delivery;

import static org.apache.james.transport.mailets.remote.delivery.Bouncer.DELIVERY_ERROR;
import static org.apache.james.transport.mailets.remote.delivery.Bouncer.DELIVERY_ERROR_CODE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Optional;

import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;

import org.apache.james.domainlist.api.DomainList;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BouncerTest {
    private static final String HELLO_NAME = "hello_name";
    private static final FakeMailetConfig DEFAULT_REMOTE_DELIVERY_CONFIG = FakeMailetConfig.builder()
        .setProperty(RemoteDeliveryConfiguration.HELO_NAME, HELLO_NAME)
        .build();
    private static final String BOUNCE_PROCESSOR = "bounce_processor";
    public static final int SMTP_ERROR_CODE_521 = 521;

    private FakeMailContext mailetContext;

    @BeforeEach
    void setUp() {
        mailetContext = FakeMailContext.defaultContext();
    }

    @Test
    void bounceShouldCallMailetContextBounceByDefault() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            DEFAULT_REMOTE_DELIVERY_CONFIG,
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().name("name").state(Mail.DEFAULT)
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .build();
        testee.bounce(mail, new Exception("Exception message"));

        FakeMailContext.BouncedMail expected = new FakeMailContext.BouncedMail(FakeMailContext.fromMail(mail),
            "Hi. This is the James mail server at " + HELLO_NAME + ".\n" +
                "I'm afraid I wasn't able to deliver your message to the following addresses.\n" +
                "This is a permanent error; I've given up. Sorry it didn't work out. Below\n" +
                "I include the list of recipients and the reason why I was unable to deliver\n" +
                "your message.\n\n\n",
            Optional.empty());
        assertThat(mailetContext.getSentMails()).isEmpty();
        assertThat(mailetContext.getBouncedMails()).containsOnly(expected);
    }

    @Test
    void bounceShouldIncludeMessagingExceptionMessageByDefault() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            DEFAULT_REMOTE_DELIVERY_CONFIG,
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().name("name").state(Mail.DEFAULT)
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .build();
        String exceptionMessage = "Exception message";
        testee.bounce(mail, new MessagingException(exceptionMessage));

        FakeMailContext.BouncedMail expected = new FakeMailContext.BouncedMail(FakeMailContext.fromMail(mail),
            "Hi. This is the James mail server at " + HELLO_NAME + ".\n" +
                "I'm afraid I wasn't able to deliver your message to the following addresses.\n" +
                "This is a permanent error; I've given up. Sorry it didn't work out. Below\n" +
                "I include the list of recipients and the reason why I was unable to deliver\n" +
                "your message.\n" +
                "\n" +
                exceptionMessage + "\n\n",
            Optional.empty());
        assertThat(mailetContext.getSentMails()).isEmpty();
        assertThat(mailetContext.getBouncedMails()).containsOnly(expected);
    }

    @Test
    void bounceShouldCustomizeSendFailedExceptionByDefault() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            DEFAULT_REMOTE_DELIVERY_CONFIG,
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().name("name").state(Mail.DEFAULT)
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .build();
        String exceptionMessage = "Error from remote server";
        testee.bounce(mail, new MessagingException("Exception message", new SendFailedException(exceptionMessage)));

        FakeMailContext.BouncedMail expected = new FakeMailContext.BouncedMail(FakeMailContext.fromMail(mail),
            "Hi. This is the James mail server at " + HELLO_NAME + ".\n" +
                "I'm afraid I wasn't able to deliver your message to the following addresses.\n" +
                "This is a permanent error; I've given up. Sorry it didn't work out. Below\n" +
                "I include the list of recipients and the reason why I was unable to deliver\n" +
                "your message.\n" +
                "\n" +
                "Remote mail server told me: " + exceptionMessage + "\n\n",
            Optional.empty());
        assertThat(mailetContext.getSentMails()).isEmpty();
        assertThat(mailetContext.getBouncedMails()).containsOnly(expected);
    }

    @Test
    void bounceShouldCustomizeUnknownHostExceptionByDefault() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            DEFAULT_REMOTE_DELIVERY_CONFIG,
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().name("name").state(Mail.DEFAULT)
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .build();
        String exceptionMessage = "I don't know him";
        testee.bounce(mail, new MessagingException("Exception message", new UnknownHostException(exceptionMessage)));

        FakeMailContext.BouncedMail expected = new FakeMailContext.BouncedMail(FakeMailContext.fromMail(mail),
            "Hi. This is the James mail server at " + HELLO_NAME + ".\n" +
                "I'm afraid I wasn't able to deliver your message to the following addresses.\n" +
                "This is a permanent error; I've given up. Sorry it didn't work out. Below\n" +
                "I include the list of recipients and the reason why I was unable to deliver\n" +
                "your message.\n" +
                "\n" +
                "Unknown host: " + exceptionMessage + "\n" +
                "This could be a DNS server error, a typo, or a problem with the recipient's mail server.\n\n",
            Optional.empty());
        assertThat(mailetContext.getSentMails()).isEmpty();
        assertThat(mailetContext.getBouncedMails()).containsOnly(expected);
    }

    @Test
    void bounceShouldCustomizeConnectionExceptionByDefault() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            DEFAULT_REMOTE_DELIVERY_CONFIG,
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().name("name").state(Mail.DEFAULT)
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .build();
        String exceptionMessage = "Can not connect";
        testee.bounce(mail, new MessagingException("Exception message", new ConnectException(exceptionMessage)));

        FakeMailContext.BouncedMail expected = new FakeMailContext.BouncedMail(FakeMailContext.fromMail(mail),
            "Hi. This is the James mail server at " + HELLO_NAME + ".\n" +
                "I'm afraid I wasn't able to deliver your message to the following addresses.\n" +
                "This is a permanent error; I've given up. Sorry it didn't work out. Below\n" +
                "I include the list of recipients and the reason why I was unable to deliver\n" +
                "your message.\n" +
                "\n" +
                exceptionMessage + "\n\n",
            Optional.empty());
        assertThat(mailetContext.getSentMails()).isEmpty();
        assertThat(mailetContext.getBouncedMails()).containsOnly(expected);
    }

    @Test
    void bounceShouldCustomizeSocketExceptionByDefault() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            DEFAULT_REMOTE_DELIVERY_CONFIG,
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().name("name").state(Mail.DEFAULT)
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .build();
        String exceptionMessage = "Can not connect";
        testee.bounce(mail, new MessagingException("Exception message", new SocketException(exceptionMessage)));

        FakeMailContext.BouncedMail expected = new FakeMailContext.BouncedMail(FakeMailContext.fromMail(mail),
            "Hi. This is the James mail server at " + HELLO_NAME + ".\n" +
                "I'm afraid I wasn't able to deliver your message to the following addresses.\n" +
                "This is a permanent error; I've given up. Sorry it didn't work out. Below\n" +
                "I include the list of recipients and the reason why I was unable to deliver\n" +
                "your message.\n" +
                "\n" +
                "Socket exception: " + exceptionMessage + "\n\n",
            Optional.empty());
        assertThat(mailetContext.getSentMails()).isEmpty();
        assertThat(mailetContext.getBouncedMails()).containsOnly(expected);
    }

    @Test
    void bounceShouldCustomizeNestedMessagingExceptionByDefault() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            DEFAULT_REMOTE_DELIVERY_CONFIG,
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().name("name").state(Mail.DEFAULT)
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .build();
        String exceptionMessage = "Can not connect";
        testee.bounce(mail, new MessagingException("Exception message", new MessagingException(exceptionMessage)));

        FakeMailContext.BouncedMail expected = new FakeMailContext.BouncedMail(FakeMailContext.fromMail(mail),
            "Hi. This is the James mail server at " + HELLO_NAME + ".\n" +
                "I'm afraid I wasn't able to deliver your message to the following addresses.\n" +
                "This is a permanent error; I've given up. Sorry it didn't work out. Below\n" +
                "I include the list of recipients and the reason why I was unable to deliver\n" +
                "your message.\n" +
                "\n" +
                exceptionMessage + "\n\n",
            Optional.empty());
        assertThat(mailetContext.getSentMails()).isEmpty();
        assertThat(mailetContext.getBouncedMails()).containsOnly(expected);
    }

    @Test
    void bounceShouldNotBounceWithNoSenderByDefault() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            DEFAULT_REMOTE_DELIVERY_CONFIG,
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().name("name").state(Mail.DEFAULT)
            .build();
        String exceptionMessage = "Can not connect";
        testee.bounce(mail, new MessagingException("Exception message", new ConnectException(exceptionMessage)));

        assertThat(mailetContext.getSentMails()).isEmpty();
        assertThat(mailetContext.getBouncedMails()).isEmpty();
    }

    @Test
    void bounceShouldSupportExceptionWithoutMessagesByDefault() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            DEFAULT_REMOTE_DELIVERY_CONFIG,
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().name("name").state(Mail.DEFAULT)
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .build();
        testee.bounce(mail, new Exception("Exception message"));

        FakeMailContext.BouncedMail expected = new FakeMailContext.BouncedMail(FakeMailContext.fromMail(mail),
            "Hi. This is the James mail server at " + HELLO_NAME + ".\n" +
                "I'm afraid I wasn't able to deliver your message to the following addresses.\n" +
                "This is a permanent error; I've given up. Sorry it didn't work out. Below\n" +
                "I include the list of recipients and the reason why I was unable to deliver\n" +
                "your message.\n\n\n",
            Optional.empty());
        assertThat(mailetContext.getSentMails()).isEmpty();
        assertThat(mailetContext.getBouncedMails()).containsOnly(expected);
    }

    @Test
    void bounceShouldNotSupportMessagingExceptionWithoutMessagesByDefault() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            DEFAULT_REMOTE_DELIVERY_CONFIG,
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().name("name").state(Mail.DEFAULT)
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .build();
        testee.bounce(mail, new MessagingException());

        FakeMailContext.BouncedMail expected = new FakeMailContext.BouncedMail(FakeMailContext.fromMail(mail),
            "Hi. This is the James mail server at " + HELLO_NAME + ".\n" +
                "I'm afraid I wasn't able to deliver your message to the following addresses.\n" +
                "This is a permanent error; I've given up. Sorry it didn't work out. Below\n" +
                "I include the list of recipients and the reason why I was unable to deliver\n" +
                "your message.\n\nnull\n\n",
            Optional.empty());
        assertThat(mailetContext.getSentMails()).isEmpty();
        assertThat(mailetContext.getBouncedMails()).containsOnly(expected);
    }

    @Test
    void bounceShouldWorkWhenProcessorSpecified() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            FakeMailetConfig.builder()
                .setProperty(RemoteDeliveryConfiguration.HELO_NAME, HELLO_NAME)
                .setProperty(RemoteDeliveryConfiguration.BOUNCE_PROCESSOR, BOUNCE_PROCESSOR)
                .build(),
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().name("name").state(Mail.DEFAULT)
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .build();
        String errorMessage = "message";
        testee.bounce(mail, new MessagingException(errorMessage));

        FakeMailContext.SentMail expected = FakeMailContext.sentMailBuilder()
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .attribute(new Attribute(DELIVERY_ERROR, AttributeValue.of(errorMessage)))
            .state(BOUNCE_PROCESSOR)
            .fromMailet()
            .build();
        assertThat(mailetContext.getSentMails()).containsOnly(expected);
        assertThat(mailetContext.getBouncedMails()).isEmpty();
    }

    @Test
    void bounceShouldWorkWhenProcessorSpecifiedAndNoSender() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            FakeMailetConfig.builder()
                .setProperty(RemoteDeliveryConfiguration.HELO_NAME, HELLO_NAME)
                .setProperty(RemoteDeliveryConfiguration.BOUNCE_PROCESSOR, BOUNCE_PROCESSOR)
                .build(),
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().name("name").state(Mail.DEFAULT)
            .build();
        String errorMessage = "message";
        testee.bounce(mail, new MessagingException(errorMessage));

        FakeMailContext.SentMail expected = FakeMailContext.sentMailBuilder()
            .attribute(new Attribute(DELIVERY_ERROR, AttributeValue.of(errorMessage)))
            .state(BOUNCE_PROCESSOR)
            .fromMailet()
            .build();
        assertThat(mailetContext.getSentMails()).containsOnly(expected);
        assertThat(mailetContext.getBouncedMails()).isEmpty();
    }

    @Test
    void bounceShouldDisplayAddressByDefault() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            DEFAULT_REMOTE_DELIVERY_CONFIG,
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().name("name").state(Mail.DEFAULT)
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .recipient(MailAddressFixture.ANY_AT_JAMES2)
            .build();
        testee.bounce(mail, new Exception("Exception message"));

        FakeMailContext.BouncedMail expected = new FakeMailContext.BouncedMail(FakeMailContext.fromMail(mail),
            "Hi. This is the James mail server at " + HELLO_NAME + ".\n" +
                "I'm afraid I wasn't able to deliver your message to the following addresses.\n" +
                "This is a permanent error; I've given up. Sorry it didn't work out. Below\n" +
                "I include the list of recipients and the reason why I was unable to deliver\n" +
                "your message.\n\n" +
                MailAddressFixture.ANY_AT_JAMES2.asString() + "\n\n",
            Optional.empty());
        assertThat(mailetContext.getSentMails()).isEmpty();
        assertThat(mailetContext.getBouncedMails()).containsOnly(expected);
    }

    @Test
    void bounceShouldDisplayAddressesByDefault() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            DEFAULT_REMOTE_DELIVERY_CONFIG,
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().name("name").state(Mail.DEFAULT)
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .recipients(MailAddressFixture.ANY_AT_JAMES2, MailAddressFixture.OTHER_AT_JAMES2)
            .build();
        testee.bounce(mail, new Exception("Exception message"));

        FakeMailContext.BouncedMail expected = new FakeMailContext.BouncedMail(FakeMailContext.fromMail(mail),
            "Hi. This is the James mail server at " + HELLO_NAME + ".\n" +
                "I'm afraid I wasn't able to deliver your message to the following addresses.\n" +
                "This is a permanent error; I've given up. Sorry it didn't work out. Below\n" +
                "I include the list of recipients and the reason why I was unable to deliver\n" +
                "your message.\n\n" +
                MailAddressFixture.ANY_AT_JAMES2.asString() + "\n" +
                MailAddressFixture.OTHER_AT_JAMES2.asString() + "\n\n",
            Optional.empty());
        assertThat(mailetContext.getSentMails()).isEmpty();
        assertThat(mailetContext.getBouncedMails()).containsOnly(expected);
    }

    @Test
    void bounceShouldWorkWhenProcessorSpecifiedAndNoExceptionMessage() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            FakeMailetConfig.builder()
                .setProperty(RemoteDeliveryConfiguration.HELO_NAME, HELLO_NAME)
                .setProperty(RemoteDeliveryConfiguration.BOUNCE_PROCESSOR, BOUNCE_PROCESSOR)
                .build(),
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().name("name").state(Mail.DEFAULT)
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .build();
        testee.bounce(mail, new MessagingException());

        FakeMailContext.SentMail expected = FakeMailContext.sentMailBuilder()
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .attribute(new Attribute(DELIVERY_ERROR, AttributeValue.of("null")))
            .state(BOUNCE_PROCESSOR)
            .fromMailet()
            .build();
        assertThat(mailetContext.getSentMails()).containsOnly(expected);
        assertThat(mailetContext.getBouncedMails()).isEmpty();
    }

    @Test
    void bounceShouldAttachErrorCodeWhenSmtpError() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            FakeMailetConfig.builder()
                .setProperty(RemoteDeliveryConfiguration.HELO_NAME, HELLO_NAME)
                .setProperty(RemoteDeliveryConfiguration.BOUNCE_PROCESSOR, BOUNCE_PROCESSOR)
                .build(),
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().name("name").state(Mail.DEFAULT)
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .build();

        SMTPSendFailedException ex = mock(SMTPSendFailedException.class);
        when(ex.getReturnCode()).thenReturn(SMTP_ERROR_CODE_521);

        testee.bounce(mail, ex);

        FakeMailContext.SentMail expected = FakeMailContext.sentMailBuilder()
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .attribute(new Attribute(DELIVERY_ERROR, AttributeValue.of("null")))
            .attribute(new Attribute(DELIVERY_ERROR_CODE, AttributeValue.of(SMTP_ERROR_CODE_521)))
            .state(BOUNCE_PROCESSOR)
            .fromMailet()
            .build();
        assertThat(mailetContext.getSentMails()).containsOnly(expected);
        assertThat(mailetContext.getBouncedMails()).isEmpty();
    }

    @Test
    void bounceShouldNotAttachErrorCodeWhenNotMessagingException() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            FakeMailetConfig.builder()
                .setProperty(RemoteDeliveryConfiguration.HELO_NAME, HELLO_NAME)
                .setProperty(RemoteDeliveryConfiguration.BOUNCE_PROCESSOR, BOUNCE_PROCESSOR)
                .build(),
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().name("name").state(Mail.DEFAULT)
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .build();

        testee.bounce(mail, new Exception());

        FakeMailContext.SentMail expected = FakeMailContext.sentMailBuilder()
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .attribute(new Attribute(DELIVERY_ERROR, AttributeValue.of("null")))
            .state(BOUNCE_PROCESSOR)
            .fromMailet()
            .build();
        assertThat(mailetContext.getSentMails()).containsOnly(expected);
        assertThat(mailetContext.getBouncedMails()).isEmpty();
    }

    @Test
    void bounceShouldNotAttachErrorCodeWhenNotSmtpError() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            FakeMailetConfig.builder()
                .setProperty(RemoteDeliveryConfiguration.HELO_NAME, HELLO_NAME)
                .setProperty(RemoteDeliveryConfiguration.BOUNCE_PROCESSOR, BOUNCE_PROCESSOR)
                .build(),
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().name("name").state(Mail.DEFAULT)
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .build();

        testee.bounce(mail, new MessagingException("not smtp related"));

        FakeMailContext.SentMail expected = FakeMailContext.sentMailBuilder()
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .attribute(new Attribute(DELIVERY_ERROR, AttributeValue.of("not smtp related")))
            .state(BOUNCE_PROCESSOR)
            .fromMailet()
            .build();
        assertThat(mailetContext.getSentMails()).containsOnly(expected);
        assertThat(mailetContext.getBouncedMails()).isEmpty();
    }

    @Test
    void bounceShouldAttachNullErrorMessageWhenNoException() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            FakeMailetConfig.builder()
                .setProperty(RemoteDeliveryConfiguration.HELO_NAME, HELLO_NAME)
                .setProperty(RemoteDeliveryConfiguration.BOUNCE_PROCESSOR, BOUNCE_PROCESSOR)
                .build(),
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().name("name").state(Mail.DEFAULT)
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .build();

        testee.bounce(mail, null);

        FakeMailContext.SentMail expected = FakeMailContext.sentMailBuilder()
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .attribute(new Attribute(DELIVERY_ERROR, AttributeValue.of("null")))
            .state(BOUNCE_PROCESSOR)
            .fromMailet()
            .build();
        assertThat(mailetContext.getSentMails()).containsOnly(expected);
        assertThat(mailetContext.getBouncedMails()).isEmpty();
    }
}
