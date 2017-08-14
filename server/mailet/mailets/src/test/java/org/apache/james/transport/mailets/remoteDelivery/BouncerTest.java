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

package org.apache.james.transport.mailets.remoteDelivery;

import static org.apache.james.transport.mailets.remoteDelivery.Bouncer.DELIVERY_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;

import javax.mail.MessagingException;
import javax.mail.SendFailedException;

import org.apache.james.domainlist.api.DomainList;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

public class BouncerTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(BouncerTest.class);
    public static final String HELLO_NAME = "hello_name";
    public static final String BOUNCE_PROCESSOR = "bounce_processor";

    private FakeMailContext mailetContext;

    @Before
    public void setUp() {
        mailetContext = FakeMailContext.defaultContext();
    }

    @Test
    public void bounceShouldCallMailetContextBounceByDefault() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            FakeMailetConfig.builder()
                .setProperty(RemoteDeliveryConfiguration.DELIVERY_THREADS, "1")
                .setProperty(RemoteDeliveryConfiguration.HELO_NAME, HELLO_NAME)
                .build(),
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().state(Mail.DEFAULT)
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .build();
        testee.bounce(mail, new Exception("Exception message"));

        FakeMailContext.BouncedMail expected = new FakeMailContext.BouncedMail(FakeMailContext.fromMail(mail),
            "Hi. This is the James mail server at " + HELLO_NAME + ".\n" +
                "I'm afraid I wasn't able to deliver your message to the following addresses.\n" +
                "This is a permanent error; I've given up. Sorry it didn't work out. Below\n" +
                "I include the list of recipients and the reason why I was unable to deliver\n" +
                "your message.\n\n\n",
            Optional.<MailAddress>absent());
        assertThat(mailetContext.getSentMails()).isEmpty();
        assertThat(mailetContext.getBouncedMails()).containsOnly(expected);
    }

    @Test
    public void bounceShouldIncludeMessagingExceptionMessageByDefault() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            FakeMailetConfig.builder()
                .setProperty(RemoteDeliveryConfiguration.DELIVERY_THREADS, "1")
                .setProperty(RemoteDeliveryConfiguration.HELO_NAME, HELLO_NAME)
                .build(),
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().state(Mail.DEFAULT)
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
            Optional.<MailAddress>absent());
        assertThat(mailetContext.getSentMails()).isEmpty();
        assertThat(mailetContext.getBouncedMails()).containsOnly(expected);
    }

    @Test
    public void bounceShouldCustomizeSendFailedExceptionByDefault() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            FakeMailetConfig.builder()
                .setProperty(RemoteDeliveryConfiguration.DELIVERY_THREADS, "1")
                .setProperty(RemoteDeliveryConfiguration.HELO_NAME, HELLO_NAME)
                .build(),
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().state(Mail.DEFAULT)
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
            Optional.<MailAddress>absent());
        assertThat(mailetContext.getSentMails()).isEmpty();
        assertThat(mailetContext.getBouncedMails()).containsOnly(expected);
    }

    @Test
    public void bounceShouldCustomizeUnknownHostExceptionByDefault() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            FakeMailetConfig.builder()
                .setProperty(RemoteDeliveryConfiguration.DELIVERY_THREADS, "1")
                .setProperty(RemoteDeliveryConfiguration.HELO_NAME, HELLO_NAME)
                .build(),
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().state(Mail.DEFAULT)
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
            Optional.<MailAddress>absent());
        assertThat(mailetContext.getSentMails()).isEmpty();
        assertThat(mailetContext.getBouncedMails()).containsOnly(expected);
    }

    @Test
    public void bounceShouldCustomizeConnectionExceptionByDefault() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            FakeMailetConfig.builder()
                .setProperty(RemoteDeliveryConfiguration.DELIVERY_THREADS, "1")
                .setProperty(RemoteDeliveryConfiguration.HELO_NAME, HELLO_NAME)
                .build(),
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().state(Mail.DEFAULT)
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
            Optional.<MailAddress>absent());
        assertThat(mailetContext.getSentMails()).isEmpty();
        assertThat(mailetContext.getBouncedMails()).containsOnly(expected);
    }

    @Test
    public void bounceShouldCustomizeSocketExceptionByDefault() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            FakeMailetConfig.builder()
                .setProperty(RemoteDeliveryConfiguration.DELIVERY_THREADS, "1")
                .setProperty(RemoteDeliveryConfiguration.HELO_NAME, HELLO_NAME)
                .build(),
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().state(Mail.DEFAULT)
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
            Optional.<MailAddress>absent());
        assertThat(mailetContext.getSentMails()).isEmpty();
        assertThat(mailetContext.getBouncedMails()).containsOnly(expected);
    }

    @Test
    public void bounceShouldCustomizeNestedMessagingExceptionByDefault() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            FakeMailetConfig.builder()
                .setProperty(RemoteDeliveryConfiguration.DELIVERY_THREADS, "1")
                .setProperty(RemoteDeliveryConfiguration.HELO_NAME, HELLO_NAME)
                .build(),
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().state(Mail.DEFAULT)
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
            Optional.<MailAddress>absent());
        assertThat(mailetContext.getSentMails()).isEmpty();
        assertThat(mailetContext.getBouncedMails()).containsOnly(expected);
    }

    @Test
    public void bounceShouldNotBounceWithNoSenderByDefault() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            FakeMailetConfig.builder()
                .setProperty(RemoteDeliveryConfiguration.DELIVERY_THREADS, "1")
                .setProperty(RemoteDeliveryConfiguration.HELO_NAME, HELLO_NAME)
                .build(),
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().state(Mail.DEFAULT)
            .build();
        String exceptionMessage = "Can not connect";
        testee.bounce(mail, new MessagingException("Exception message", new ConnectException(exceptionMessage)));

        assertThat(mailetContext.getSentMails()).isEmpty();
        assertThat(mailetContext.getBouncedMails()).isEmpty();
    }

    @Test
    public void bounceShouldSupportExceptionWithoutMessagesByDefault() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            FakeMailetConfig.builder()
                .setProperty(RemoteDeliveryConfiguration.DELIVERY_THREADS, "1")
                .setProperty(RemoteDeliveryConfiguration.HELO_NAME, HELLO_NAME)
                .build(),
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().state(Mail.DEFAULT)
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .build();
        testee.bounce(mail, new Exception("Exception message"));

        FakeMailContext.BouncedMail expected = new FakeMailContext.BouncedMail(FakeMailContext.fromMail(mail),
            "Hi. This is the James mail server at " + HELLO_NAME + ".\n" +
                "I'm afraid I wasn't able to deliver your message to the following addresses.\n" +
                "This is a permanent error; I've given up. Sorry it didn't work out. Below\n" +
                "I include the list of recipients and the reason why I was unable to deliver\n" +
                "your message.\n\n\n",
            Optional.<MailAddress>absent());
        assertThat(mailetContext.getSentMails()).isEmpty();
        assertThat(mailetContext.getBouncedMails()).containsOnly(expected);
    }

    @Test
    public void bounceShouldNotSupportMessagingExceptionWithoutMessagesByDefault() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            FakeMailetConfig.builder()
                .setProperty(RemoteDeliveryConfiguration.DELIVERY_THREADS, "1")
                .setProperty(RemoteDeliveryConfiguration.HELO_NAME, HELLO_NAME)
                .build(),
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().state(Mail.DEFAULT)
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .build();
        testee.bounce(mail, new MessagingException());

        FakeMailContext.BouncedMail expected = new FakeMailContext.BouncedMail(FakeMailContext.fromMail(mail),
            "Hi. This is the James mail server at " + HELLO_NAME + ".\n" +
                "I'm afraid I wasn't able to deliver your message to the following addresses.\n" +
                "This is a permanent error; I've given up. Sorry it didn't work out. Below\n" +
                "I include the list of recipients and the reason why I was unable to deliver\n" +
                "your message.\n\nnull\n\n",
            Optional.<MailAddress>absent());
        assertThat(mailetContext.getSentMails()).isEmpty();
        assertThat(mailetContext.getBouncedMails()).containsOnly(expected);
    }

    @Test
    public void bounceShouldWorkWhenProcessorSpecified() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            FakeMailetConfig.builder()
                .setProperty(RemoteDeliveryConfiguration.DELIVERY_THREADS, "1")
                .setProperty(RemoteDeliveryConfiguration.HELO_NAME, HELLO_NAME)
                .setProperty(RemoteDeliveryConfiguration.BOUNCE_PROCESSOR, BOUNCE_PROCESSOR)
                .build(),
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().state(Mail.DEFAULT)
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .build();
        String errorMessage = "message";
        testee.bounce(mail, new MessagingException(errorMessage));

        FakeMailContext.SentMail expected = FakeMailContext.sentMailBuilder()
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .attribute(DELIVERY_ERROR, errorMessage)
            .state(BOUNCE_PROCESSOR)
            .fromMailet()
            .build();
        assertThat(mailetContext.getSentMails()).containsOnly(expected);
        assertThat(mailetContext.getBouncedMails()).isEmpty();
    }

    @Test
    public void bounceShouldNotBounceWhenNoSenderWhenProcessorSpecified() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            FakeMailetConfig.builder()
                .setProperty(RemoteDeliveryConfiguration.DELIVERY_THREADS, "1")
                .setProperty(RemoteDeliveryConfiguration.HELO_NAME, HELLO_NAME)
                .setProperty(RemoteDeliveryConfiguration.BOUNCE_PROCESSOR, BOUNCE_PROCESSOR)
                .build(),
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().state(Mail.DEFAULT)
            .build();
        testee.bounce(mail, new MessagingException("message"));

        assertThat(mailetContext.getSentMails()).isEmpty();
        assertThat(mailetContext.getBouncedMails()).isEmpty();
    }

    @Test
    public void bounceShouldDisplayAddressByDefault() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            FakeMailetConfig.builder()
                .setProperty(RemoteDeliveryConfiguration.DELIVERY_THREADS, "1")
                .setProperty(RemoteDeliveryConfiguration.HELO_NAME, HELLO_NAME)
                .build(),
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().state(Mail.DEFAULT)
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
            Optional.<MailAddress>absent());
        assertThat(mailetContext.getSentMails()).isEmpty();
        assertThat(mailetContext.getBouncedMails()).containsOnly(expected);
    }

    @Test
    public void bounceShouldDisplayAddressesByDefault() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            FakeMailetConfig.builder()
                .setProperty(RemoteDeliveryConfiguration.DELIVERY_THREADS, "1")
                .setProperty(RemoteDeliveryConfiguration.HELO_NAME, HELLO_NAME)
                .build(),
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().state(Mail.DEFAULT)
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
            Optional.<MailAddress>absent());
        assertThat(mailetContext.getSentMails()).isEmpty();
        assertThat(mailetContext.getBouncedMails()).containsOnly(expected);
    }

    @Test
    public void bounceShouldWorkWhenProcessorSpecifiedAndNoExceptionMessage() throws Exception {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(
            FakeMailetConfig.builder()
                .setProperty(RemoteDeliveryConfiguration.DELIVERY_THREADS, "1")
                .setProperty(RemoteDeliveryConfiguration.HELO_NAME, HELLO_NAME)
                .setProperty(RemoteDeliveryConfiguration.BOUNCE_PROCESSOR, BOUNCE_PROCESSOR)
                .build(),
            mock(DomainList.class));
        Bouncer testee = new Bouncer(configuration, mailetContext);

        Mail mail = FakeMail.builder().state(Mail.DEFAULT)
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .build();
        testee.bounce(mail, new MessagingException());

        FakeMailContext.SentMail expected = FakeMailContext.sentMailBuilder()
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .attribute(DELIVERY_ERROR, "null")
            .state(BOUNCE_PROCESSOR)
            .fromMailet()
            .build();
        assertThat(mailetContext.getSentMails()).containsOnly(expected);
        assertThat(mailetContext.getBouncedMails()).isEmpty();
    }
}
