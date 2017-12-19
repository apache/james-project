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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import org.apache.james.core.MailAddress;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.transport.mailets.Sieve;
import org.apache.james.transport.mailets.jsieve.ResourceLocator;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.apache.mailet.base.test.MimeMessageBuilder;
import org.apache.mailet.base.test.MimeMessageBuilder.Header;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

public class SieveIntegrationTest {

    public static final String LOCAL_PART = "receiver";
    public static final String RECEIVER_DOMAIN_COM = LOCAL_PART + "@domain.com";

    public static DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-mm-dd HH:mm:ss");
    public static final DateTime DATE_CLOSE = formatter.parseDateTime("2016-01-16 00:00:00");
    public static final DateTime DATE_DEFAULT = formatter.parseDateTime("2016-01-14 00:00:00");
    public static final DateTime DATE_NEW = formatter.parseDateTime("2016-01-18 00:00:00");
    public static final DateTime DATE_OLD = formatter.parseDateTime("2011-01-18 00:00:00");
    public static final MailboxPath NOT_SELECTED_MAILBOX = MailboxPath.forUser(LOCAL_PART, "INBOX.not.selected");
    public static final MailboxPath SELECTED_MAILBOX = MailboxPath.forUser(LOCAL_PART, "INBOX.select");
    public static final MailboxPath INBOX = MailboxPath.forUser(LOCAL_PART, "INBOX");

    private Sieve testee;
    private UsersRepository usersRepository;
    private ResourceLocator resourceLocator;
    private FakeMailContext fakeMailContext;

    @Before
    public void setUp() throws Exception {
        resourceLocator = mock(ResourceLocator.class);
        usersRepository = mock(UsersRepository.class);
        fakeMailContext = FakeMailContext.builder().logger(mock(Logger.class)).build();

        testee = new Sieve(usersRepository, resourceLocator);
        testee.init(FakeMailetConfig.builder().mailetName("Sieve").mailetContext(fakeMailContext).build());
    }

    @Test
    public void serviceShouldNotModifyEmailWhenErrorRetrievingScript() throws Exception {
        when(usersRepository.supportVirtualHosting()).thenReturn(true);
        when(usersRepository.getUser(new MailAddress(RECEIVER_DOMAIN_COM))).thenReturn(RECEIVER_DOMAIN_COM);
        when(resourceLocator.get(new MailAddress(RECEIVER_DOMAIN_COM))).thenThrow(new ScriptNotFoundException());

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + RECEIVER_DOMAIN_COM)).isNull();
        assertThat(mail.getState()).isEqualTo(Mail.DEFAULT);
    }

    @Test
    public void mailShouldBeWellDeliveredByDefaultToUserWhenVirtualHostingIsTurnedOn() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/keep.script");
        when(usersRepository.supportVirtualHosting()).thenReturn(true);
        when(usersRepository.getUser(new MailAddress(RECEIVER_DOMAIN_COM))).thenReturn(RECEIVER_DOMAIN_COM);

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + RECEIVER_DOMAIN_COM)).isEqualTo("/INBOX");
    }

    @Test
    public void mailShouldBeWellDeliveredByDefaultToUserWhenvirtualHostingIsTurnedOff() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/keep.script");
        when(usersRepository.supportVirtualHosting()).thenReturn(false);
        when(usersRepository.getUser(new MailAddress("receiver@localhost"))).thenReturn("receiver");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo("/INBOX");
    }

    @Test
    public void keepScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/keep.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo("/INBOX");
    }

    @Test
    public void discardScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/discard.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getRecipients()).isEmpty();
    }

    @Test
    public void fileintoScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/fileinto.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo("/INBOX/any");
    }

    @Test
    public void allOfAllFalseScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/allofAllFalse.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void allOfOneFalseScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/allofOneFalse.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void allOfAllTrueScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/allofAllTrue.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(SELECTED_MAILBOX.getName()));
    }

    @Test
    public void anyOfAllFalseScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/anyofAllFalse.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void falseScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/false.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void trueScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/true.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(SELECTED_MAILBOX.getName()));
    }

    @Test
    public void notFalseScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/notFalse.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(SELECTED_MAILBOX.getName()));
    }

    @Test
    public void notTrueScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/notTrue.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void anyOfOneFalseScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/anyofOneFalse.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(SELECTED_MAILBOX.getName()));
    }

    @Test
    public void anyOfAllTrueScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/anyofAllTrue.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(SELECTED_MAILBOX.getName()));
    }

    @Test
    public void stopScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/stop.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(INBOX.getName()));
    }

    @Test
    public void headerScriptShouldWorkIfHeaderIsAbsent() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/header.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void headerInstructionShouldSupportFoldedEncodedHeaders() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/headerEncodedFolded.script");

        FakeMail mail = FakeMail.builder()
            .mimeMessage(MimeMessageBuilder.mimeMessageFromStream(
                ClassLoader.getSystemResourceAsStream("eml/gmail.eml")))
            .state(Mail.DEFAULT)
            .recipient(new MailAddress(RECEIVER_DOMAIN_COM))
            .sender(new MailAddress("sender@any.com"))
            .build();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART))
            .isEqualTo(expressMailboxNameWithSlash(SELECTED_MAILBOX.getName()));
    }


    @Test
    public void headerScriptShouldWorkIfHeaderIsPresent() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/header.script");

        FakeMail mail = createMailWithSubject("JAMES-1620 revolution");
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(SELECTED_MAILBOX.getName()));
    }

    @Test
    public void sizeOverScriptShouldWorkIfUnderLimit() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/sizeOver.script");

        FakeMail mail = createMail();
        mail.setMessageSize(100);
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void sizeUnderScriptShouldWorkIfUnderLimit() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/sizeUnder.script");

        FakeMail mail = createMail();
        mail.setMessageSize(100);
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(SELECTED_MAILBOX.getName()));
    }

    @Test
    public void sizeOverScriptShouldWorkIfOverLimit() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/sizeOver.script");

        FakeMail mail = createMail();
        mail.setMessageSize(1000);
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(SELECTED_MAILBOX.getName()));
    }

    @Test
    public void sizeUnderScriptShouldWorkIfOverLimit() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/sizeUnder.script");

        FakeMail mail = createMail();
        mail.setMessageSize(1000);
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void addressLocalPartShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressLocalPart.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Cc", "source@any.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(SELECTED_MAILBOX.getName()));
    }


    @Test
    public void addressLocalPartShouldOnlyMatchLocalPart() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressLocalPart.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Cc", "source1@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }


    @Test
    public void addressDomainShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressDomain.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Cc", "source1@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(SELECTED_MAILBOX.getName()));
    }

    @Test
    public void addressDomainShouldOnlyMatchLocalPart() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressDomain.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Cc", "source@domain.org"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void addressBccAllShouldNotMatchOtherHeaders() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllBcc.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Cc", "source@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void addressBccAllShouldMatchSpecifiedAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllBcc.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Bcc", "source@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(SELECTED_MAILBOX.getName()));
    }

    @Test
    public void addressBccAllShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllBcc.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Bcc", "source2@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void addressBccAllShouldNotMatchOtherDomain() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllBcc.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Bcc", "source@domain.org"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void redirectShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/redirect.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getRecipients()).isEmpty();
        FakeMailContext.SentMail expectedSentMail = FakeMailContext.sentMailBuilder()
            .sender(new MailAddress("sender@any.com"))
            .recipient(new MailAddress("redirect@apache.org"))
            .fromMailet()
            .build();
        assertThat(fakeMailContext.getSentMails())
            .containsExactly(expectedSentMail);
    }

    @Test
    public void addressCcAllShouldNotMatchOtherHeaders() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllCc.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Resend-From", "source@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void addressCcAllShouldMatchSpecifiedAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllCc.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Cc", "source@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(SELECTED_MAILBOX.getName()));
    }

    @Test
    public void addressCcAllShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllCc.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Cc", "source2@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void addressCcAllShouldNotMatchOtherDomain() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllCc.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Cc", "source@domain.org"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void addressFromAllShouldNotMatchOtherHeaders() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllFrom.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Cc", "source@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void addressFromAllShouldMatchSpecifiedAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllFrom.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("From", "source@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(SELECTED_MAILBOX.getName()));
    }

    @Test
    public void addressFromAllShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllFrom.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("From", "source2@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void addressFromAllShouldNotMatchOtherDomain() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllFrom.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("From", "source@domain.org"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void addressToAllShouldNotMatchOtherHeaders() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllTo.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Resent-To", "source@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void addressToAllShouldMatchSpecifiedAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllTo.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("To", "source@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(SELECTED_MAILBOX.getName()));
    }

    @Test
    public void addressToAllShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllTo.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("To", "source2@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void addressToAllShouldNotMatchOtherDomain() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllTo.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("To", "source@domain.org"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void addressSenderAllShouldNotMatchOtherHeaders() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllSender.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("To", "source@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void addressSenderAllShouldMatchSpecifiedAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllSender.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Sender", "source@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(SELECTED_MAILBOX.getName()));
    }

    @Test
    public void addressSenderAllShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllSender.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Sender", "source2@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void addressSenderAllShouldNotMatchOtherDomain() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllSender.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Sender", "source@domain.org"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void addressResent_FromAllShouldNotMatchOtherHeaders() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-From.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("From", "source@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void addressResent_FromAllShouldMatchSpecifiedAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-From.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Resend-From", "source@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(SELECTED_MAILBOX.getName()));
    }

    @Test
    public void addressResent_FromAllShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-From.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Resend-From", "source2@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void addressResent_FromAllShouldNotMatchOtherDomain() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-From.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Resend-From", "source@domain.org"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void addressResent_ToAllShouldNotMatchOtherHeaders() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-To.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("To", "source@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void addressResent_ToAllShouldMatchSpecifiedAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-To.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Resend-To", "source@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(SELECTED_MAILBOX.getName()));
    }

    @Test
    public void addressResent_ToAllShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-To.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Resend-To", "source2@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void addressResent_ToAllShouldNotMatchOtherDomain() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-To.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Resend-To", "source@domain.org"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void envelopeFromShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/envelopeFrom.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("From", "source@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(SELECTED_MAILBOX.getName()));
    }

    @Test
    public void envelopeFromShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/envelopeFromOtherSender.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void envelopeToShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/envelopeTo.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(SELECTED_MAILBOX.getName()));
    }

    @Test
    public void envelopeToShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/envelopeToOtherReceiver.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void bodyRawShouldNotMatchNotContainedData() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/bodyRawInvalid.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void bodyRawShouldMatchContent() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/bodyRawMatch.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(SELECTED_MAILBOX.getName()));
    }

    @Test
    public void bodyContentShouldMatchContent() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/bodyContentMatch.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(SELECTED_MAILBOX.getName()));
    }

    @Test
    public void bodyContentShouldNotMatchNotContainedData() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/bodyContentInvalid.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void bodyContentShouldNotMatchWhenWrongContentType() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/bodyContentWrongContentType.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void bodyTextShouldNotMatchNotContainedData() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/bodyTextInvalid.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName()));
    }

    @Test
    public void bodyTextShouldMatchContent() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/bodyTextMatch.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(SELECTED_MAILBOX.getName()));
    }

    @Test
    public void doubleVacationShouldNotBeExecutedAndReceiverShouldHaveANotificationAboutSieveError() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/doubleVacation.script");

        FakeMail mail = createMail();
        testee.service(mail);

        // Notification of script interpretation failure
        assertThat(fakeMailContext.getSentMails()).containsExactly(FakeMailContext.sentMailBuilder()
            .recipient(new MailAddress(RECEIVER_DOMAIN_COM))
            .sender(new MailAddress(RECEIVER_DOMAIN_COM))
            .fromMailet()
            .build());
        // No action taken
        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isNull();
    }

    @Test
    public void vacationShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/vacationReason.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(INBOX.getName()));

        FakeMailContext.SentMail expectedSentMail = FakeMailContext.sentMailBuilder()
            .sender(new MailAddress(RECEIVER_DOMAIN_COM))
            .recipient(new MailAddress("sender@any.com"))
            .fromMailet()
            .build();
        assertThat(fakeMailContext.getSentMails()).containsExactly(expectedSentMail);
    }

    @Test
    public void vacationShouldNotSendNotificationToMailingLists() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/vacationReason.script");
        Mail mail = createMail();
        mail.getMessage().addHeader("List-Id", "0123456789");

        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(INBOX.getName()));
        assertThat(fakeMailContext.getSentMails()).isEmpty();
    }

    @Test
    public void vacationShouldNotGenerateNotificationIfTooOld() throws Exception {
        prepareTestUsingScriptAndDates("org/apache/james/transport/mailets/delivery/vacationReason.script", DATE_OLD, DATE_NEW);

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(INBOX.getName()));
        assertThat(fakeMailContext.getSentMails()).isEmpty();
    }

    @Test
    public void vacationShouldNotCancelFileIntoActionIfNotExecuted() throws Exception {
        prepareTestUsingScriptAndDates("org/apache/james/transport/mailets/delivery/vacationReasonAndFileInto.script", DATE_OLD, DATE_NEW);

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(SELECTED_MAILBOX.getName()));
        assertThat(fakeMailContext.getSentMails()).isEmpty();
    }

    @Test
    public void vacationDaysParameterShouldFilterTooOldDates() throws Exception {
        prepareTestUsingScriptAndDates("org/apache/james/transport/mailets/delivery/vacationDaysReason.script", DATE_DEFAULT, DATE_NEW);

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(INBOX.getName()));
        assertThat(fakeMailContext.getSentMails()).isEmpty();
    }

    @Test
    public void vacationDaysParameterShouldKeepDatesInRange() throws Exception {
        prepareTestUsingScriptAndDates("org/apache/james/transport/mailets/delivery/vacationDaysReason.script", DATE_CLOSE, DATE_NEW);

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(INBOX.getName()));
        FakeMailContext.SentMail expectedSentMail = FakeMailContext.sentMailBuilder()
            .sender(new MailAddress(RECEIVER_DOMAIN_COM))
            .recipient(new MailAddress("sender@any.com"))
            .fromMailet()
            .build();
        assertThat(fakeMailContext.getSentMails()).containsExactly(expectedSentMail);
    }

    @Test
    public void vacationShouldNotCancelFileIntoActionIfExecuted() throws Exception {
        prepareTestUsingScriptAndDates("org/apache/james/transport/mailets/delivery/vacationReasonAndFileInto.script", DATE_DEFAULT, DATE_NEW);

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(SELECTED_MAILBOX.getName()));
        FakeMailContext.SentMail expectedSentMail = FakeMailContext.sentMailBuilder()
            .sender(new MailAddress(RECEIVER_DOMAIN_COM))
            .recipient(new MailAddress("sender@any.com"))
            .fromMailet()
            .build();
        assertThat(fakeMailContext.getSentMails()).containsExactly(expectedSentMail);
    }

    @Test
    public void vacationFromSubjectShouldWork() throws Exception {
        prepareTestUsingScriptAndDates("org/apache/james/transport/mailets/delivery/vacationSubjectFromReason.script", DATE_DEFAULT, DATE_NEW);

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(INBOX.getName()));
        FakeMailContext.SentMail expectedSentMail = FakeMailContext.sentMailBuilder()
            .sender(new MailAddress("benwa@apache.org"))
            .recipient(new MailAddress("sender@any.com"))
            .fromMailet()
            .build();
        assertThat(fakeMailContext.getSentMails()).containsExactly(expectedSentMail);
    }

    @Test
    public void vacationDaysAddressesShouldWork() throws Exception {
        prepareTestUsingScriptAndDates("org/apache/james/transport/mailets/delivery/vacationDaysAddressesReason.script", DATE_CLOSE, DATE_NEW);

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + LOCAL_PART)).isEqualTo(expressMailboxNameWithSlash(INBOX.getName()));
        FakeMailContext.SentMail expectedSentMail = FakeMailContext.sentMailBuilder()
            .sender(new MailAddress(RECEIVER_DOMAIN_COM))
            .recipient(new MailAddress("sender@any.com"))
            .fromMailet()
            .build();
        assertThat(fakeMailContext.getSentMails()).containsExactly(expectedSentMail);
    }

    private void prepareTestUsingScript(final String script) throws Exception {
        prepareTestUsingScriptAndDates(script, DATE_DEFAULT, DATE_DEFAULT);
    }

    private void prepareTestUsingScriptAndDates(String script, DateTime scriptCreationDate, DateTime scriptExecutionDate) throws Exception {
        when(usersRepository.supportVirtualHosting()).thenReturn(false);
        when(usersRepository.getUser(new MailAddress(LOCAL_PART + "@localhost"))).thenReturn(LOCAL_PART);
        when(usersRepository.getUser(new MailAddress(LOCAL_PART + "@domain.com"))).thenReturn(LOCAL_PART);
        when(resourceLocator.get(new MailAddress(RECEIVER_DOMAIN_COM))).thenReturn(new ResourceLocator.UserSieveInformation(scriptCreationDate,
            scriptExecutionDate,
            ClassLoader.getSystemResourceAsStream(script)));
    }

    private FakeMail createMail() throws MessagingException, IOException {
        return createMailWithSubject("Subject");
    }

    private FakeMail createMailWithSubject(String subject) throws MessagingException, IOException {
        return createMailWithSubjectAndHeaders(subject);
    }

    private FakeMail createMailWithSubjectAndHeaders(String subject, MimeMessageBuilder.Header... headers) throws MessagingException, IOException {
        return FakeMail.builder()
            .mimeMessage(
                MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject(subject)
                    .addHeaders(headers)
                    .setSender("sender@any.com")
                    .addToRecipient(RECEIVER_DOMAIN_COM)
                    .setMultipartWithBodyParts(
                        MimeMessageBuilder.bodyPartBuilder()
                            .data("A text to match")
                            .addHeader("Content-Type", "text/plain; charset=UTF-8")
                            .filename("file.txt")
                            .disposition(MimeBodyPart.ATTACHMENT)))
            .state(Mail.DEFAULT)
            .recipient(new MailAddress(RECEIVER_DOMAIN_COM))
            .sender(new MailAddress("sender@any.com"))
            .build();
    }

    private String expressMailboxNameWithSlash(String name) {
        return '/' + name.replace('.', '/');
    }
}
