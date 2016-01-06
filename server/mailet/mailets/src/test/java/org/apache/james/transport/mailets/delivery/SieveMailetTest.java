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

import com.google.common.collect.Lists;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.transport.mailets.SieveMailet;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;

import javax.activation.DataHandler;
import javax.mail.Flags;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@Ignore("MAILET-96")
public class SieveMailetTest {

    class Header {
        String name;
        String value;

        public Header(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }

    public static final MailboxPath NOT_SELECTED_MAILBOX = new MailboxPath("#private", "receiver", "INBOX.not.selected");
    public static final MailboxPath SELECTED_MAILBOX = new MailboxPath("#private", "receiver", "INBOX.select");
    public static final MailboxPath INBOX = new MailboxPath("#private", "receiver", "INBOX");

    private UsersRepository usersRepository;
    private MailboxManager mailboxManager;
    private SieveRepository sieveRepository;
    private SieveMailet sieveMailet;
    private FakeMailContext fakeMailContext;
    private FakeMailetConfig fakeMailetConfig;

    @Before
    public void setUp() throws Exception {
        sieveRepository = mock(SieveRepository.class);
        usersRepository = mock(UsersRepository.class);
        mailboxManager = mock(MailboxManager.class);
        fakeMailContext = new FakeMailContext();
        fakeMailetConfig = new FakeMailetConfig("sieveMailet", fakeMailContext);
        sieveMailet = new SieveMailet(usersRepository, mailboxManager, sieveRepository, "INBOX");
    }

    @Test
    public void mailShouldBeWellDeliveredByDefaultToUserWhenvirtualHostingIsTurnedOn() throws Exception {
        when(usersRepository.supportVirtualHosting()).thenAnswer(new Answer<Boolean>() {
            public Boolean answer(InvocationOnMock invocationOnMock) throws Throwable {
                return true;
            }
        });
        when(sieveRepository.getActive("receiver@domain.com")).thenThrow(new ScriptNotFoundException());
        final MessageManager messageManager = prepareMessageManagerOn(new MailboxPath("#private", "receiver@domain.com", "INBOX"));
        sieveMailet.init(fakeMailetConfig);
        sieveMailet.service(createMail());
        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void mailShouldBeWellDeliveredByDefaultToUserWhenvirtualHostingIsTurnedOff() throws Exception {
        when(usersRepository.supportVirtualHosting()).thenAnswer(new Answer<Boolean>() {
            public Boolean answer(InvocationOnMock invocationOnMock) throws Throwable {
                return false;
            }
        });
        when(sieveRepository.getActive("receiver")).thenThrow(new ScriptNotFoundException());
        final MessageManager messageManager = prepareMessageManagerOn(INBOX);
        sieveMailet.init(fakeMailetConfig);
        sieveMailet.service(createMail());
        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void keepScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/keep.script");
        final MessageManager messageManager = prepareMessageManagerOn(INBOX);

        sieveMailet.service(createMail());
        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void discardScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/discard.script");

        sieveMailet.service(createMail());
        verifyNoMoreInteractions(mailboxManager);
    }

    @Test
    public void fileintoScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/fileinto.script");
        final MessageManager messageManager = prepareMessageManagerOn(new MailboxPath("#private", "receiver", "INBOX.any"));

        sieveMailet.service(createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void allOfAllFalseScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/allofAllFalse.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void allOfOneFalseScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/allofOneFalse.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void allOfAllTrueScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/allofAllTrue.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        sieveMailet.service(createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void anyOfAllFalseScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/anyofAllFalse.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void falseScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/false.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void trueScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/true.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        sieveMailet.service(createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void notFalseScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/notFalse.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        sieveMailet.service(createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void notTrueScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/notTrue.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void anyOfOneFalseScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/anyofOneFalse.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        sieveMailet.service(createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void anyOfAllTrueScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/anyofAllTrue.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        sieveMailet.service(createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void stopScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/stop.script");
        final MessageManager messageManager = prepareMessageManagerOn(INBOX);

        sieveMailet.service(createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void headerScriptShouldWorkIfHeaderIsAbsent() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/header.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void headerScriptShouldWorkIfHeaderIsPresent() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/header.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubject("JAMES-1620 revolution"));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void sizeOverScriptShouldWorkIfUnderLimit() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/sizeOver.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        FakeMail mail = createMail();
        mail.setMessageSize(100);
        sieveMailet.service(mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void sizeUnderScriptShouldWorkIfUnderLimit() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/sizeUnder.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        FakeMail mail = createMail();
        mail.setMessageSize(100);
        sieveMailet.service(mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void sizeOverScriptShouldWorkIfOverLimit() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/sizeOver.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        FakeMail mail = createMail();
        mail.setMessageSize(1000);
        sieveMailet.service(mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void sizeUnderScriptShouldWorkIfOverLimit() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/sizeUnder.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        FakeMail mail = createMail();
        mail.setMessageSize(1000);
        sieveMailet.service(mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressLocalPartShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressLocalPart.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("Cc", "source@any.com")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }


    @Test
    public void addressLocalPartShouldOnlyMatchLocalPart() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressLocalPart.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("Cc", "source1@domain.com")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }


    @Test
    public void addressDomainShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressDomain.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("Cc", "source1@domain.com")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }


    @Test
    public void addressDomainShouldOnlyMatchLocalPart() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressDomain.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("Cc", "source@domain.org")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressBccAllShouldNotMatchOtherHeaders() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllBcc.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("Cc", "source@domain.com")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressBccAllShouldMatchSpecifiedAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllBcc.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("Bcc", "source@domain.com")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressBccAllShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllBcc.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("Bcc", "source2@domain.com")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressBccAllShouldNotMatchOtherDomain() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllBcc.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("Bcc", "source@domain.org")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void redirectShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/redirect.script");
        sieveMailet.service(createMail());
        verifyZeroInteractions(mailboxManager);

        List<FakeMailContext.SentMail> sentMails = fakeMailContext.getSentMails();
        assertEquals(sentMails.size(), 1);
        assertEquals(sentMails.get(0), new FakeMailContext.SentMail(new MailAddress("sender@any.com"), Lists.newArrayList(new MailAddress("redirect@apache.org")), null));
    }

    @Test
    public void addressCcAllShouldNotMatchOtherHeaders() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllCc.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("Resend-From", "source@domain.com")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressCcAllShouldMatchSpecifiedAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllCc.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("Cc", "source@domain.com")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressCcAllShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllCc.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("Cc", "source2@domain.com")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressCcAllShouldNotMatchOtherDomain() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllCc.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("Cc", "source@domain.org")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressFromAllShouldNotMatchOtherHeaders() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllFrom.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("Cc", "source@domain.com")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressFromAllShouldMatchSpecifiedAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllFrom.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("From", "source@domain.com")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressFromAllShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllFrom.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("From", "source2@domain.com")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressFromAllShouldNotMatchOtherDomain() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllFrom.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("From", "source@domain.org")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressToAllShouldNotMatchOtherHeaders() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllTo.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("Resent-To", "source@domain.com")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressToAllShouldMatchSpecifiedAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllTo.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("To", "source@domain.com")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressToAllShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllTo.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("To", "source2@domain.com")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressToAllShouldNotMatchOtherDomain() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllTo.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("To", "source@domain.org")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressSenderAllShouldNotMatchOtherHeaders() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllSender.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("To", "source@domain.com")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressSenderAllShouldMatchSpecifiedAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllSender.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("Sender", "source@domain.com")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressSenderAllShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllSender.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("Sender", "source2@domain.com")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressSenderAllShouldNotMatchOtherDomain() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllSender.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("Sender", "source@domain.org")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressResent_FromAllShouldNotMatchOtherHeaders() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-From.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("From", "source@domain.com")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressResent_FromAllShouldMatchSpecifiedAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-From.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("Resend-From", "source@domain.com")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressResent_FromAllShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-From.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("Resend-From", "source2@domain.com")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressResent_FromAllShouldNotMatchOtherDomain() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-From.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("Resend-From", "source@domain.org")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressResent_ToAllShouldNotMatchOtherHeaders() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-To.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("To", "source@domain.com")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressResent_ToAllShouldMatchSpecifiedAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-To.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("Resend-To", "source@domain.com")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressResent_ToAllShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-To.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("Resend-To", "source2@domain.com")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void addressResent_ToAllShouldNotMatchOtherDomain() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-To.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("Resend-To", "source@domain.org")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void envelopeFromShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/envelopeFrom.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        sieveMailet.service(createMailWithSubjectAndHeaders("Default", new Header("From", "source@domain.com")));

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void envelopeFromShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/envelopeFromOtherSender.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void envelopeToShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/envelopeTo.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        sieveMailet.service(createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void envelopeToShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/envelopeToOtherReceiver.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void bodyRawShouldNotMatchNotContainedData() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/bodyRawInvalid.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void bodyRawShouldMatchContent() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/bodyRawMatch.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        sieveMailet.service(createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void bodyContentShouldMatchContent() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/bodyContentMatch.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        sieveMailet.service(createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void bodyContentShouldNotMatchNotContainedData() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/bodyContentInvalid.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void bodyContentShouldNotMatchWhenWrongContentType() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/bodyContentWrongContentType.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void bodyTextShouldNotMatchNotContainedData() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/bodyTextInvalid.script");
        final MessageManager messageManager = prepareMessageManagerOn(NOT_SELECTED_MAILBOX);

        sieveMailet.service(createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void bodyTextShouldMatchContent() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/bodyTextMatch.script");
        final MessageManager messageManager = prepareMessageManagerOn(SELECTED_MAILBOX);

        sieveMailet.service(createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    private void prepareTestUsingScript(final String script) throws Exception {
        when(usersRepository.supportVirtualHosting()).thenReturn(false);
        when(sieveRepository.getActive("receiver")).thenReturn(ClassLoader.getSystemResourceAsStream(script));
        sieveMailet.init(fakeMailetConfig);
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
        message.setRecipient(MimeMessage.RecipientType.TO, new InternetAddress("receiver@domain.com"));
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
        FakeMail mail = new FakeMail(message);
        mail.setState(Mail.DEFAULT);
        mail.setRecipients(Lists.newArrayList(new MailAddress("receiver@domain.com")));
        mail.setSender(new MailAddress("sender@any.com"));
        return mail;
    }

}