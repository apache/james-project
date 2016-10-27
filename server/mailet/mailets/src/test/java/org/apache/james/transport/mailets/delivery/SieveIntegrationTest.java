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

package org.apache.james.transport.mailets.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.mail.Flags;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import org.apache.commons.logging.Log;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.transport.mailets.jsieve.ResourceLocator;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

import com.google.common.collect.Lists;

public class SieveIntegrationTest {

    public static final String RECEIVER_DOMAIN_COM = "receiver@domain.com";

    class Header {
        String name;
        String value;

        public Header(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }

    public static DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-mm-dd HH:mm:ss");
    public static final DateTime DATE_CLOSE = formatter.parseDateTime("2016-01-16 00:00:00");
    public static final DateTime DATE_DEFAULT = formatter.parseDateTime("2016-01-14 00:00:00");
    public static final DateTime DATE_NEW = formatter.parseDateTime("2016-01-18 00:00:00");
    public static final DateTime DATE_OLD = formatter.parseDateTime("2011-01-18 00:00:00");
    public static final MailboxPath NOT_SELECTED_MAILBOX = new MailboxPath("#private", "receiver", "INBOX.not.selected");
    public static final MailboxPath SELECTED_MAILBOX = new MailboxPath("#private", "receiver", "INBOX.select");
    public static final MailboxPath INBOX = new MailboxPath("#private", "receiver", "INBOX");

    private MailStorer testee;
    private UsersRepository usersRepository;
    private MailboxManager mailboxManager;
    private ResourceLocator resourceLocator;
    private FakeMailContext fakeMailContext;
    private MailAddress sender;

    @Before
    public void setUp() throws Exception {
        resourceLocator = mock(ResourceLocator.class);
        usersRepository = mock(UsersRepository.class);
        mailboxManager = mock(MailboxManager.class);
        fakeMailContext = FakeMailContext.defaultContext();
        sender = new MailAddress("sender@any");

        testee = SieveMailStorer.builder()
            .resourceLocator(resourceLocator)
            .usersRepository(usersRepository)
            .folder("INBOX")
            .sievePoster(new SievePoster(mailboxManager, "INBOX", usersRepository, fakeMailContext))
            .log(mock(Log.class))
            .mailetContext(fakeMailContext)
            .build();
    }

    @Test
    public void mailShouldBeWellDeliveredByDefaultToUserWhenVirtualHostingIsTurnedOn() throws Exception {
        when(usersRepository.supportVirtualHosting()).thenReturn(true);
        when(resourceLocator.get(RECEIVER_DOMAIN_COM)).thenThrow(new ScriptNotFoundException());
        final MessageManager messageManager = prepareMessageManagerOn(new MailboxPath("#private", RECEIVER_DOMAIN_COM, "INBOX"));

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void mailShouldBeWellDeliveredByDefaultToUserWhenvirtualHostingIsTurnedOff() throws Exception {
        when(usersRepository.supportVirtualHosting()).thenReturn(false);
        when(resourceLocator.get("receiver")).thenThrow(new ScriptNotFoundException());
        final MessageManager messageManager = prepareMessageManagerOn(INBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void keepScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/keep.script");
        final MessageManager messageManager = prepareMessageManagerOn(INBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void discardScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/discard.script");

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verifyNoMoreInteractions(mailboxManager);
    }

    @Test
    public void fileintoScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/fileinto.script");
        final MessageManager messageManager = prepareMessageManagerOn(new MailboxPath("#private", "receiver", "INBOX.any"));

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void allOfAllFalseScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/allofAllFalse.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void allOfOneFalseScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/allofOneFalse.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void allOfAllTrueScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/allofAllTrue.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void anyOfAllFalseScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/anyofAllFalse.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void falseScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/false.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void trueScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/true.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void notFalseScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/notFalse.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void notTrueScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/notTrue.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void anyOfOneFalseScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/anyofOneFalse.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void anyOfAllTrueScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/anyofAllTrue.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void stopScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/stop.script");
        final MessageManager messageManager = prepareMessageManagerOn(INBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void headerScriptShouldWorkIfHeaderIsAbsent() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/header.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void headerScriptShouldWorkIfHeaderIsPresent() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/header.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubject("JAMES-1620 revolution");
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void sizeOverScriptShouldWorkIfUnderLimit() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/sizeOver.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        FakeMail mail = createMail();
        mail.setMessageSize(100);
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void sizeUnderScriptShouldWorkIfUnderLimit() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/sizeUnder.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        FakeMail mail = createMail();
        mail.setMessageSize(100);
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void sizeOverScriptShouldWorkIfOverLimit() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/sizeOver.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        FakeMail mail = createMail();
        mail.setMessageSize(1000);
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void sizeUnderScriptShouldWorkIfOverLimit() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/sizeUnder.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        FakeMail mail = createMail();
        mail.setMessageSize(1000);
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressLocalPartShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressLocalPart.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Cc", "source@any.com"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }


    @Test
    public void addressLocalPartShouldOnlyMatchLocalPart() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressLocalPart.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Cc", "source1@domain.com"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }


    @Test
    public void addressDomainShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressDomain.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Cc", "source1@domain.com"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }


    @Test
    public void addressDomainShouldOnlyMatchLocalPart() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressDomain.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Cc", "source@domain.org"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressBccAllShouldNotMatchOtherHeaders() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllBcc.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Cc", "source@domain.com"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressBccAllShouldMatchSpecifiedAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllBcc.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Bcc", "source@domain.com"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressBccAllShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllBcc.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Bcc", "source2@domain.com"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressBccAllShouldNotMatchOtherDomain() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllBcc.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Bcc", "source@domain.org"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void redirectShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/redirect.script");

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verifyZeroInteractions(mailboxManager);
        assertThat(fakeMailContext.getSentMails())
            .containsExactly(new FakeMailContext.SentMail(new MailAddress("sender@any.com"), Lists.newArrayList(new MailAddress("redirect@apache.org")), null));
    }

    @Test
    public void addressCcAllShouldNotMatchOtherHeaders() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllCc.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Resend-From", "source@domain.com"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressCcAllShouldMatchSpecifiedAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllCc.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Cc", "source@domain.com"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressCcAllShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllCc.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Cc", "source2@domain.com"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressCcAllShouldNotMatchOtherDomain() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllCc.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Cc", "source@domain.org"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressFromAllShouldNotMatchOtherHeaders() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllFrom.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Cc", "source@domain.com"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressFromAllShouldMatchSpecifiedAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllFrom.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("From", "source@domain.com"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressFromAllShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllFrom.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("From", "source2@domain.com"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressFromAllShouldNotMatchOtherDomain() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllFrom.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("From", "source@domain.org"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressToAllShouldNotMatchOtherHeaders() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllTo.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Resent-To", "source@domain.com"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressToAllShouldMatchSpecifiedAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllTo.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("To", "source@domain.com"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressToAllShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllTo.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("To", "source2@domain.com"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressToAllShouldNotMatchOtherDomain() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllTo.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("To", "source@domain.org"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressSenderAllShouldNotMatchOtherHeaders() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllSender.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("To", "source@domain.com"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressSenderAllShouldMatchSpecifiedAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllSender.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Sender", "source@domain.com"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressSenderAllShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllSender.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Sender", "source2@domain.com"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressSenderAllShouldNotMatchOtherDomain() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllSender.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Sender", "source@domain.org"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressResent_FromAllShouldNotMatchOtherHeaders() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-From.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("From", "source@domain.com"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressResent_FromAllShouldMatchSpecifiedAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-From.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Resend-From", "source@domain.com"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressResent_FromAllShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-From.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Resend-From", "source2@domain.com"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressResent_FromAllShouldNotMatchOtherDomain() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-From.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Resend-From", "source@domain.org"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressResent_ToAllShouldNotMatchOtherHeaders() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-To.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("To", "source@domain.com"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressResent_ToAllShouldMatchSpecifiedAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-To.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Resend-To", "source@domain.com"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressResent_ToAllShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-To.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Resend-To", "source2@domain.com"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressResent_ToAllShouldNotMatchOtherDomain() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-To.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Resend-To", "source@domain.org"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void envelopeFromShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/envelopeFrom.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("From", "source@domain.com"));
        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void envelopeFromShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/envelopeFromOtherSender.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void envelopeToShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/envelopeTo.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void envelopeToShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/envelopeToOtherReceiver.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void bodyRawShouldNotMatchNotContainedData() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/bodyRawInvalid.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void bodyRawShouldMatchContent() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/bodyRawMatch.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void bodyContentShouldMatchContent() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/bodyContentMatch.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void bodyContentShouldNotMatchNotContainedData() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/bodyContentInvalid.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void bodyContentShouldNotMatchWhenWrongContentType() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/bodyContentWrongContentType.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void bodyTextShouldNotMatchNotContainedData() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/bodyTextInvalid.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void bodyTextShouldMatchContent() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/bodyTextMatch.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void doubleVacationShouldNotBeExecuted() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/doubleVacation.script");
        MessageManager messageManager = prepareMessageManagerOn(INBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
        assertThat(fakeMailContext.getSentMails()).isEmpty();
    }

    @Test
    public void vacationShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/vacationReason.script");
        MessageManager messageManager = prepareMessageManagerOn(INBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
        assertThat(fakeMailContext.getSentMails()).containsExactly(new FakeMailContext.SentMail(new MailAddress(RECEIVER_DOMAIN_COM), Lists.newArrayList(new MailAddress("sender@any.com")), null));
    }

    @Test
    public void vacationShouldNotSendNotificationToMailingLists() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/vacationReason.script");
        MessageManager messageManager = prepareMessageManagerOn(INBOX);
        Mail mail = createMail();
        mail.getMessage().addHeader("List-Id", "0123456789");

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
        assertThat(fakeMailContext.getSentMails()).isEmpty();
    }

    @Test
    public void vacationShouldNotGenerateNotificationIfTooOld() throws Exception {
        prepareTestUsingScriptAndDates("org/apache/james/transport/mailets/delivery/vacationReason.script", DATE_OLD, DATE_NEW);
        MessageManager messageManager = prepareMessageManagerOn(INBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
        assertThat(fakeMailContext.getSentMails()).isEmpty();
    }

    @Test
    public void vacationShouldNotCancelFileIntoActionIfNotExecuted() throws Exception {
        prepareTestUsingScriptAndDates("org/apache/james/transport/mailets/delivery/vacationReasonAndFileInto.script", DATE_OLD, DATE_NEW);
        MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
        assertThat(fakeMailContext.getSentMails()).isEmpty();
    }

    @Test
    public void vacationDaysParameterShouldFilterTooOldDates() throws Exception {
        prepareTestUsingScriptAndDates("org/apache/james/transport/mailets/delivery/vacationDaysReason.script", DATE_DEFAULT, DATE_NEW);
        MessageManager messageManager = prepareMessageManagerOn(INBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
        assertThat(fakeMailContext.getSentMails()).isEmpty();
    }

    @Test
    public void vacationDaysParameterShouldKeepDatesInRange() throws Exception {
        prepareTestUsingScriptAndDates("org/apache/james/transport/mailets/delivery/vacationDaysReason.script", DATE_CLOSE, DATE_NEW);
        MessageManager messageManager = prepareMessageManagerOn(INBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
        assertThat(fakeMailContext.getSentMails()).containsExactly(new FakeMailContext.SentMail(new MailAddress(RECEIVER_DOMAIN_COM), Lists.newArrayList(new MailAddress("sender@any.com")), null));
    }

    @Test
    public void vacationShouldNotCancelFileIntoActionIfExecuted() throws Exception {
        prepareTestUsingScriptAndDates("org/apache/james/transport/mailets/delivery/vacationReasonAndFileInto.script", DATE_DEFAULT, DATE_NEW);
        MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
        assertThat(fakeMailContext.getSentMails()).containsExactly(new FakeMailContext.SentMail(new MailAddress(RECEIVER_DOMAIN_COM), Lists.newArrayList(new MailAddress("sender@any.com")), null));
    }

    @Test
    public void vacationFromSubjectShouldWork() throws Exception {
        prepareTestUsingScriptAndDates("org/apache/james/transport/mailets/delivery/vacationSubjectFromReason.script", DATE_DEFAULT, DATE_NEW);
        MessageManager messageManager = prepareMessageManagerOn(INBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
        assertThat(fakeMailContext.getSentMails()).containsExactly(new FakeMailContext.SentMail(new MailAddress("benwa@apache.org"), Lists.newArrayList(new MailAddress("sender@any.com")), null));
    }

    @Test
    public void vacationDaysAddressesShouldWork() throws Exception {
        prepareTestUsingScriptAndDates("org/apache/james/transport/mailets/delivery/vacationDaysAddressesReason.script", DATE_CLOSE, DATE_NEW);
        MessageManager messageManager = prepareMessageManagerOn(INBOX);

        testee.storeMail(sender, new MailAddress(RECEIVER_DOMAIN_COM), createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
        assertThat(fakeMailContext.getSentMails()).containsExactly(new FakeMailContext.SentMail(new MailAddress(RECEIVER_DOMAIN_COM), Lists.newArrayList(new MailAddress("sender@any.com")), null));
    }

    private void prepareTestUsingScript(final String script) throws Exception {
        prepareTestUsingScriptAndDates(script, DATE_DEFAULT, DATE_DEFAULT);
    }

    private void prepareTestUsingScriptAndDates(String script, DateTime scriptCreationDate, DateTime scriptExecutionDate) throws Exception {
        when(usersRepository.supportVirtualHosting()).thenReturn(false);
        when(resourceLocator.get("//receiver@localhost/sieve")).thenReturn(new ResourceLocator.UserSieveInformation(scriptCreationDate,
            scriptExecutionDate,
            ClassLoader.getSystemResourceAsStream(script)));
    }

    private MessageManager prepareMessageManagerOn(MailboxPath inbox) throws MailboxException {
        final MessageManager messageManager = mock(MessageManager.class);
        when(mailboxManager.getMailbox(eq(inbox), any(MailboxSession.class))).thenReturn(messageManager);
        final MailboxSession session = mock(MailboxSession.class);
        when(session.getPathDelimiter()).thenReturn('.');
        when(mailboxManager.createSystemSession(any(String.class), any(Logger.class))).thenReturn(session);
        return messageManager;
    }

    private FakeMail createMail() throws MessagingException, IOException {
        return createMailWithSubject("Subject");
    }

    private FakeMail createMailWithSubject(String subject) throws MessagingException, IOException {
        return createMailWithSubjectAndHeaders(subject);
    }

    private FakeMail createMailWithSubjectAndHeaders(String subject, Header... headers) throws MessagingException, IOException {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject(subject);
        message.setSender(new InternetAddress("sender@any.com"));
        message.setRecipient(MimeMessage.RecipientType.TO, new InternetAddress(RECEIVER_DOMAIN_COM));
        MimeMultipart multipart = new MimeMultipart();
        MimeBodyPart scriptPart = new MimeBodyPart();
        scriptPart.setDataHandler(
            new DataHandler(
                new ByteArrayDataSource(
                    "A text to match",
                    "text/plain; charset=UTF-8")
            ));
        scriptPart.setDisposition(MimeBodyPart.ATTACHMENT);
        scriptPart.setHeader("Content-Type", "text/plain; charset=UTF-8");
        scriptPart.setFileName("file.txt");
        multipart.addBodyPart(scriptPart);
        message.setContent(multipart);
        for (Header header : headers) {
            message.addHeader(header.name, header.value);
        }
        message.saveChanges();
        return FakeMail.builder()
                .mimeMessage(message)
                .state(Mail.DEFAULT)
                .recipient(new MailAddress(RECEIVER_DOMAIN_COM))
                .sender(new MailAddress("sender@any.com"))
                .build();
    }
}
