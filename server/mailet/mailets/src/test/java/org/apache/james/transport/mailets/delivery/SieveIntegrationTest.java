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
import java.time.ZonedDateTime;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.core.builder.MimeMessageBuilder.Header;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.transport.mailets.Sieve;
import org.apache.james.transport.mailets.jsieve.ResourceLocator;
import org.apache.james.transport.mailets.jsieve.delivery.SieveExecutor;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class SieveIntegrationTest {

    private static final String LOCAL_PART = "receiver";
    private static final String RECEIVER_DOMAIN_COM = LOCAL_PART + "@domain.com";
    private static final Username LOCAL_USER = Username.of(LOCAL_PART);

    private static final ZonedDateTime DATE_CLOSE = ZonedDateTime.parse("2016-01-16T00:00:00Z");
    private static final ZonedDateTime DATE_DEFAULT = ZonedDateTime.parse("2016-01-14T00:00:00Z");
    private static final ZonedDateTime DATE_NEW = ZonedDateTime.parse("2016-01-18T00:00:00Z");
    private static final ZonedDateTime DATE_OLD = ZonedDateTime.parse("2011-01-18T00:00:00Z");
    private static final MailboxPath NOT_SELECTED_MAILBOX = MailboxPath.forUser(LOCAL_USER, "INBOX.not.selected");
    private static final MailboxPath SELECTED_MAILBOX = MailboxPath.forUser(LOCAL_USER, "INBOX.select");
    private static final MailboxPath INBOX = MailboxPath.inbox(LOCAL_USER);
    private static final MailboxPath INBOX_ANY = MailboxPath.forUser(LOCAL_USER, "INBOX.any");

    private static final AttributeName ATTRIBUTE_NAME = AttributeName.of("DeliveryPath_" + LOCAL_PART);
    private static final Attribute ATTRIBUTE_INBOX = new Attribute(ATTRIBUTE_NAME, AttributeValue.of(expressMailboxNameWithSlash(INBOX.getName())));
    private static final Attribute ATTRIBUTE_INBOX_ANY = new Attribute(ATTRIBUTE_NAME, AttributeValue.of(expressMailboxNameWithSlash(INBOX_ANY.getName())));
    private static final Attribute ATTRIBUTE_SELECTED_MAILBOX = new Attribute(ATTRIBUTE_NAME, AttributeValue.of(expressMailboxNameWithSlash(SELECTED_MAILBOX.getName())));
    private static final Attribute ATTRIBUTE_NOT_SELECTED_MAILBOX = new Attribute(ATTRIBUTE_NAME, AttributeValue.of(expressMailboxNameWithSlash(NOT_SELECTED_MAILBOX.getName())));
    private static final AttributeName ATTRIBUTE_NAME_DOMAIN = AttributeName.of("DeliveryPath_" + RECEIVER_DOMAIN_COM);
    private static final Attribute ATTRIBUTE_INBOX_DOMAIN = new Attribute(ATTRIBUTE_NAME_DOMAIN, AttributeValue.of(expressMailboxNameWithSlash(INBOX.getName())));

    private Sieve testee;
    private UsersRepository usersRepository;
    private ResourceLocator resourceLocator;
    private FakeMailContext fakeMailContext;

    @BeforeEach
    void setUp() throws Exception {
        resourceLocator = mock(ResourceLocator.class);
        usersRepository = mock(UsersRepository.class);
        fakeMailContext = FakeMailContext.builder().logger(mock(Logger.class)).build();

        testee = new Sieve(usersRepository, resourceLocator);
        testee.init(FakeMailetConfig.builder().mailetName("Sieve").mailetContext(fakeMailContext).build());
    }

    @Test
    void serviceShouldNotModifyEmailWhenErrorRetrievingScript() throws Exception {
        when(usersRepository.supportVirtualHosting()).thenReturn(true);
        when(usersRepository.getUsername(new MailAddress(RECEIVER_DOMAIN_COM))).thenReturn(Username.of(RECEIVER_DOMAIN_COM));
        when(resourceLocator.get(new MailAddress(RECEIVER_DOMAIN_COM))).thenThrow(new ScriptNotFoundException());

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME_DOMAIN)).isEmpty();
        assertThat(mail.getState()).isEqualTo(Mail.DEFAULT);
    }

    @Test
    void mailShouldBeWellDeliveredByDefaultToUserWhenVirtualHostingIsTurnedOn() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/keep.script");
        when(usersRepository.supportVirtualHosting()).thenReturn(true);
        when(usersRepository.getUsername(new MailAddress(RECEIVER_DOMAIN_COM))).thenReturn(Username.of(RECEIVER_DOMAIN_COM));

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME_DOMAIN)).contains(ATTRIBUTE_INBOX_DOMAIN);
    }

    @Test
    void mailShouldBeWellDeliveredByDefaultToUserWhenvirtualHostingIsTurnedOff() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/keep.script");
        when(usersRepository.supportVirtualHosting()).thenReturn(false);
        when(usersRepository.getUsername(new MailAddress("receiver@localhost"))).thenReturn(Username.of("receiver"));

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_INBOX);
    }

    @Test
    void keepScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/keep.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_INBOX);
    }

    @Test
    void discardScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/discard.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getRecipients()).isEmpty();
    }

    @Test
    void fileintoScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/fileinto.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_INBOX_ANY);
    }


    // ANCHOR
    @Test
    void shouldSupportSeveralRecipientsWhenFileInto() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/fileinto.script");
        when(usersRepository.getUsername(new MailAddress("other@domain.tld"))).thenReturn(Username.of("other@domain.tld"));
        when(resourceLocator.get(new MailAddress("other@domain.tld"))).thenThrow(new ScriptNotFoundException());

        FakeMail mail = createMail();
        mail.setRecipients(ImmutableList.of(new MailAddress(RECEIVER_DOMAIN_COM), new MailAddress("other@domain.tld")));
        testee.service(mail);

        assertThatAttribute(mail.getAttribute(ATTRIBUTE_NAME)).isEqualTo(ATTRIBUTE_INBOX_ANY);
    }

    @Test
    void allOfAllFalseScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/allofAllFalse.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void allOfOneFalseScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/allofOneFalse.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void allOfAllTrueScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/allofAllTrue.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_SELECTED_MAILBOX);
    }

    @Test
    void anyOfAllFalseScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/anyofAllFalse.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void falseScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/false.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void trueScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/true.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_SELECTED_MAILBOX);
    }

    @Test
    void notFalseScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/notFalse.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_SELECTED_MAILBOX);
    }

    @Test
    void notTrueScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/notTrue.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void anyOfOneFalseScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/anyofOneFalse.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_SELECTED_MAILBOX);
    }

    @Test
    void anyOfAllTrueScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/anyofAllTrue.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_SELECTED_MAILBOX);
    }

    @Test
    void stopScriptShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/stop.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_INBOX);
    }

    @Test
    void headerScriptShouldWorkIfHeaderIsAbsent() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/header.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void headerInstructionShouldSupportFoldedEncodedHeaders() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/headerEncodedFolded.script");

        FakeMail mail = FakeMail.builder()
            .name("name")
            .mimeMessage(MimeMessageUtil.mimeMessageFromStream(
                ClassLoader.getSystemResourceAsStream("eml/gmail.eml")))
            .state(Mail.DEFAULT)
            .recipient(RECEIVER_DOMAIN_COM)
            .sender("sender@any.com")
            .build();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME))
            .contains(ATTRIBUTE_SELECTED_MAILBOX);
    }


    @Test
    void headerScriptShouldWorkIfHeaderIsPresent() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/header.script");

        FakeMail mail = createMailWithSubject("JAMES-1620 revolution");
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_SELECTED_MAILBOX);
    }

    @Test
    void sizeOverScriptShouldWorkIfUnderLimit() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/sizeOver.script");

        FakeMail mail = createMail();
        mail.setMessageSize(100);
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void sizeUnderScriptShouldWorkIfUnderLimit() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/sizeUnder.script");

        FakeMail mail = createMail();
        mail.setMessageSize(100);
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_SELECTED_MAILBOX);
    }

    @Test
    void sizeOverScriptShouldWorkIfOverLimit() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/sizeOver.script");

        FakeMail mail = createMail();
        mail.setMessageSize(1000);
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_SELECTED_MAILBOX);
    }

    @Test
    void sizeUnderScriptShouldWorkIfOverLimit() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/sizeUnder.script");

        FakeMail mail = createMail();
        mail.setMessageSize(1000);
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void addressLocalPartShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressLocalPart.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Cc", "source@any.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_SELECTED_MAILBOX);
    }


    @Test
    void addressLocalPartShouldOnlyMatchLocalPart() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressLocalPart.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Cc", "source1@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }


    @Test
    void addressDomainShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressDomain.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Cc", "source1@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_SELECTED_MAILBOX);
    }

    @Test
    void addressDomainShouldOnlyMatchLocalPart() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressDomain.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Cc", "source@domain.org"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void addressBccAllShouldNotMatchOtherHeaders() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllBcc.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Cc", "source@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void addressBccAllShouldMatchSpecifiedAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllBcc.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Bcc", "source@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_SELECTED_MAILBOX);
    }

    @Test
    void addressBccAllShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllBcc.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Bcc", "source2@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void addressBccAllShouldNotMatchOtherDomain() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllBcc.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Bcc", "source@domain.org"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void redirectShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/redirect.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getRecipients()).isEmpty();
        FakeMailContext.SentMail expectedSentMail = FakeMailContext.sentMailBuilder()
            .sender("sender@any.com")
            .recipient(new MailAddress("redirect@apache.org"))
            .fromMailet()
            .build();
        assertThat(fakeMailContext.getSentMails())
            .containsExactly(expectedSentMail);
    }

    @Test
    void rejectShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/reject.script");

        FakeMail mail = createMail();
        testee.service(mail);

        FakeMailContext.SentMail expectedSentMail = FakeMailContext.sentMailBuilder()
            .recipient("sender@any.com")
            .fromMailet()
            .build();
        assertThat(fakeMailContext.getSentMails())
            .containsExactly(expectedSentMail);
        assertThat(mail.getRecipients()).isEmpty();
    }

    @Test
    void rejectShouldWorkWhenMultipleRecipients() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/reject.script");
        when(usersRepository.getUsername(new MailAddress("other@domain.tld"))).thenReturn(Username.of("other@domain.tld"));
        when(resourceLocator.get(new MailAddress("other@domain.tld"))).thenThrow(new ScriptNotFoundException());

        FakeMail mail = createMail();
        mail.setRecipients(ImmutableList.of(new MailAddress(RECEIVER_DOMAIN_COM), new MailAddress("other@domain.tld")));
        testee.service(mail);

        FakeMailContext.SentMail expectedSentMail = FakeMailContext.sentMailBuilder()
            .recipient("sender@any.com")
            .fromMailet()
            .build();
        assertThat(fakeMailContext.getSentMails())
            .containsExactly(expectedSentMail);
        assertThat(mail.getRecipients()).containsOnly(new MailAddress("other@domain.tld"));
    }

    @Test
    void redirectShouldWorkWhenSeveralRecipients() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/redirect.script");
        when(usersRepository.getUsername(new MailAddress("other@domain.tld"))).thenReturn(Username.of("other@domain.tld"));
        when(resourceLocator.get(new MailAddress("other@domain.tld"))).thenThrow(new ScriptNotFoundException());

        FakeMail mail = createMail();
        mail.setRecipients(ImmutableList.of(new MailAddress(RECEIVER_DOMAIN_COM), new MailAddress("other@domain.tld")));
        testee.service(mail);

        assertThat(mail.getRecipients()).containsOnly(new MailAddress("other@domain.tld"));
        FakeMailContext.SentMail expectedSentMail = FakeMailContext.sentMailBuilder()
            .sender("sender@any.com")
            .recipient(new MailAddress("redirect@apache.org"))
            .fromMailet()
            .build();
        assertThat(fakeMailContext.getSentMails())
            .containsExactly(expectedSentMail);
    }

    @Test
    void addressCcAllShouldNotMatchOtherHeaders() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllCc.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Resend-From", "source@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void addressCcAllShouldMatchSpecifiedAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllCc.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Cc", "source@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_SELECTED_MAILBOX);
    }

    @Test
    void addressCcAllShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllCc.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Cc", "source2@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void addressCcAllShouldNotMatchOtherDomain() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllCc.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Cc", "source@domain.org"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void addressFromAllShouldNotMatchOtherHeaders() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllFrom.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Cc", "source@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void addressFromAllShouldMatchSpecifiedAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllFrom.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("From", "source@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_SELECTED_MAILBOX);
    }

    @Test
    void addressFromAllShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllFrom.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("From", "source2@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void addressFromAllShouldNotMatchOtherDomain() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllFrom.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("From", "source@domain.org"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void addressToAllShouldNotMatchOtherHeaders() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllTo.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Resent-To", "source@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void addressToAllShouldMatchSpecifiedAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllTo.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("To", "source@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_SELECTED_MAILBOX);
    }

    @Test
    void addressToAllShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllTo.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("To", "source2@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void addressToAllShouldNotMatchOtherDomain() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllTo.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("To", "source@domain.org"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void addressSenderAllShouldNotMatchOtherHeaders() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllSender.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("To", "source@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void addressSenderAllShouldMatchSpecifiedAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllSender.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Sender", "source@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_SELECTED_MAILBOX);
    }

    @Test
    void addressSenderAllShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllSender.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Sender", "source2@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void addressSenderAllShouldNotMatchOtherDomain() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllSender.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Sender", "source@domain.org"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void addressResent_FromAllShouldNotMatchOtherHeaders() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-From.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("From", "source@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void addressResent_FromAllShouldMatchSpecifiedAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-From.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Resend-From", "source@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_SELECTED_MAILBOX);
    }

    @Test
    void addressResent_FromAllShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-From.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Resend-From", "source2@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void addressResent_FromAllShouldNotMatchOtherDomain() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-From.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Resend-From", "source@domain.org"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void addressResent_ToAllShouldNotMatchOtherHeaders() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-To.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("To", "source@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void addressResent_ToAllShouldMatchSpecifiedAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-To.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Resend-To", "source@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_SELECTED_MAILBOX);
    }

    @Test
    void addressResent_ToAllShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-To.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Resend-To", "source2@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void addressResent_ToAllShouldNotMatchOtherDomain() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/addressAllResend-To.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("Resend-To", "source@domain.org"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void envelopeFromShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/envelopeFrom.script");

        FakeMail mail = createMailWithSubjectAndHeaders("Default", new Header("From", "source@domain.com"));
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_SELECTED_MAILBOX);
    }

    @Test
    void envelopeFromShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/envelopeFromOtherSender.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void envelopeToShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/envelopeTo.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_SELECTED_MAILBOX);
    }

    @Test
    void envelopeToShouldNotMatchOtherAddress() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/envelopeToOtherReceiver.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void bodyRawShouldNotMatchNotContainedData() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/bodyRawInvalid.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void bodyRawShouldMatchContent() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/bodyRawMatch.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_SELECTED_MAILBOX);
    }

    @Test
    void bodyContentShouldMatchContent() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/bodyContentMatch.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_SELECTED_MAILBOX);
    }

    @Test
    void bodyContentShouldNotMatchNotContainedData() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/bodyContentInvalid.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void bodyContentShouldNotMatchWhenWrongContentType() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/bodyContentWrongContentType.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void bodyTextShouldNotMatchNotContainedData() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/bodyTextInvalid.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_NOT_SELECTED_MAILBOX);
    }

    @Test
    void bodyTextShouldMatchContent() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/bodyTextMatch.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_SELECTED_MAILBOX);
    }

    @Test
    void doubleVacationShouldNotBeExecutedAndReceiverShouldHaveANotificationAboutSieveError() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/doubleVacation.script");

        FakeMail mail = createMail();
        testee.service(mail);

        // Notification of script interpretation failure
        assertThat(fakeMailContext.getSentMails()).containsExactly(FakeMailContext.sentMailBuilder()
            .recipient(RECEIVER_DOMAIN_COM)
            .sender(new MailAddress(RECEIVER_DOMAIN_COM))
            .fromMailet()
            .attribute(new Attribute(SieveExecutor.SIEVE_NOTIFICATION, AttributeValue.of(true)))
            .build());
        // No action taken
        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).isEmpty();
    }

    @Test
    void vacationShouldWork() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/vacationReason.script");

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_INBOX);

        FakeMailContext.SentMail expectedSentMail = FakeMailContext.sentMailBuilder()
            .sender(new MailAddress(RECEIVER_DOMAIN_COM))
            .recipient(new MailAddress("sender@any.com"))
            .fromMailet()
            .build();
        assertThat(fakeMailContext.getSentMails()).containsExactly(expectedSentMail);
    }

    @Test
    void vacationShouldWorkWhenSeveralRecipients() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/vacationReason.script");
        when(usersRepository.getUsername(new MailAddress("other@domain.tld"))).thenReturn(Username.of("other@domain.tld"));
        when(resourceLocator.get(new MailAddress("other@domain.tld"))).thenThrow(new ScriptNotFoundException());

        FakeMail mail = createMail();
        mail.setRecipients(ImmutableList.of(new MailAddress(RECEIVER_DOMAIN_COM), new MailAddress("other@domain.tld")));
        testee.service(mail);

        assertThatAttribute(mail.getAttribute(ATTRIBUTE_NAME)).isEqualTo(ATTRIBUTE_INBOX);

        FakeMailContext.SentMail expectedSentMail = FakeMailContext.sentMailBuilder()
            .sender(new MailAddress(RECEIVER_DOMAIN_COM))
            .recipient(new MailAddress("sender@any.com"))
            .fromMailet()
            .build();
        assertThat(fakeMailContext.getSentMails()).containsExactly(expectedSentMail);
    }

    @Test
    void vacationShouldNotSendNotificationToMailingLists() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/vacationReason.script");
        Mail mail = createMail();
        mail.getMessage().addHeader("List-Id", "0123456789");

        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_INBOX);
        assertThat(fakeMailContext.getSentMails()).isEmpty();
    }

    @Test
    void vacationShouldNotGenerateNotificationIfTooOld() throws Exception {
        prepareTestUsingScriptAndDates("org/apache/james/transport/mailets/delivery/vacationReason.script", DATE_OLD, DATE_NEW);

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_INBOX);
        assertThat(fakeMailContext.getSentMails()).isEmpty();
    }

    @Test
    void vacationShouldNotCancelFileIntoActionIfNotExecuted() throws Exception {
        prepareTestUsingScriptAndDates("org/apache/james/transport/mailets/delivery/vacationReasonAndFileInto.script", DATE_OLD, DATE_NEW);

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_SELECTED_MAILBOX);
        assertThat(fakeMailContext.getSentMails()).isEmpty();
    }

    @Test
    void vacationDaysParameterShouldFilterTooOldDates() throws Exception {
        prepareTestUsingScriptAndDates("org/apache/james/transport/mailets/delivery/vacationDaysReason.script", DATE_DEFAULT, DATE_NEW);

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_INBOX);
        assertThat(fakeMailContext.getSentMails()).isEmpty();
    }

    @Test
    void vacationDaysParameterShouldKeepDatesInRange() throws Exception {
        prepareTestUsingScriptAndDates("org/apache/james/transport/mailets/delivery/vacationDaysReason.script", DATE_CLOSE, DATE_NEW);

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_INBOX);
        FakeMailContext.SentMail expectedSentMail = FakeMailContext.sentMailBuilder()
            .sender(new MailAddress(RECEIVER_DOMAIN_COM))
            .recipient(new MailAddress("sender@any.com"))
            .fromMailet()
            .build();
        assertThat(fakeMailContext.getSentMails()).containsExactly(expectedSentMail);
    }

    @Test
    void vacationShouldNotCancelFileIntoActionIfExecuted() throws Exception {
        prepareTestUsingScriptAndDates("org/apache/james/transport/mailets/delivery/vacationReasonAndFileInto.script", DATE_DEFAULT, DATE_NEW);

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_SELECTED_MAILBOX);
        FakeMailContext.SentMail expectedSentMail = FakeMailContext.sentMailBuilder()
            .sender(new MailAddress(RECEIVER_DOMAIN_COM))
            .recipient(new MailAddress("sender@any.com"))
            .fromMailet()
            .build();
        assertThat(fakeMailContext.getSentMails()).containsExactly(expectedSentMail);
    }

    @Test
    void vacationFromSubjectShouldWork() throws Exception {
        prepareTestUsingScriptAndDates("org/apache/james/transport/mailets/delivery/vacationSubjectFromReason.script", DATE_DEFAULT, DATE_NEW);

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_INBOX);
        FakeMailContext.SentMail expectedSentMail = FakeMailContext.sentMailBuilder()
            .sender(new MailAddress("benwa@apache.org"))
            .recipient(new MailAddress("sender@any.com"))
            .fromMailet()
            .build();
        assertThat(fakeMailContext.getSentMails()).containsExactly(expectedSentMail);
    }

    @Test
    void vacationDaysAddressesShouldWork() throws Exception {
        prepareTestUsingScriptAndDates("org/apache/james/transport/mailets/delivery/vacationDaysAddressesReason.script", DATE_CLOSE, DATE_NEW);

        FakeMail mail = createMail();
        testee.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).contains(ATTRIBUTE_INBOX);
        FakeMailContext.SentMail expectedSentMail = FakeMailContext.sentMailBuilder()
            .sender(new MailAddress(RECEIVER_DOMAIN_COM))
            .recipient(new MailAddress("sender@any.com"))
            .fromMailet()
            .build();
        assertThat(fakeMailContext.getSentMails()).containsExactly(expectedSentMail);
    }
    
    @Test
    void sieveErrorNotificationEmailsShouldNotBeProcessed() throws Exception {
        prepareTestUsingScript("org/apache/james/transport/mailets/delivery/keep.script");

        FakeMail mail = createMail();
        mail.setAttribute(new Attribute(SieveExecutor.SIEVE_NOTIFICATION, AttributeValue.of(true)));
        testee.service(mail);
        // check that the Sieve mailet performs normally, and nothing gets into ATTRIBUTE_NAME
        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).isEmpty();
    }

    private void prepareTestUsingScript(final String script) throws Exception {
        prepareTestUsingScriptAndDates(script, DATE_DEFAULT, DATE_DEFAULT);
    }

    private void prepareTestUsingScriptAndDates(String script, ZonedDateTime scriptCreationDate, ZonedDateTime scriptExecutionDate) throws Exception {
        when(usersRepository.supportVirtualHosting()).thenReturn(false);
        when(usersRepository.getUsername(new MailAddress(LOCAL_PART + "@localhost"))).thenReturn(Username.of(LOCAL_PART));
        when(usersRepository.getUsername(new MailAddress(LOCAL_PART + "@domain.com"))).thenReturn(Username.of(LOCAL_PART));
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

    private FakeMail createMailWithSubjectAndHeaders(String subject, MimeMessageBuilder.Header... headers) throws MessagingException {
        return FakeMail.builder()
            .name("name")
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
            .recipient(RECEIVER_DOMAIN_COM)
            .sender("sender@any.com")
            .build();
    }

    private static String expressMailboxNameWithSlash(String name) {
        return '/' + name.replace('.', '/');
    }
}
