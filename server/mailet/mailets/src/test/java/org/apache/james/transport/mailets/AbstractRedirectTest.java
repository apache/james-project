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

import java.util.Collection;
import java.util.Properties;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.james.transport.mailets.AbstractRedirect.SpecialAddress;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.RFC2822Headers;
import org.apache.mailet.base.test.FakeMail;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class AbstractRedirectTest {

    private TesteeRedirect testee;

    @Before
    public void setup() {
        testee = new TesteeRedirect();
    }

    private class TesteeRedirect extends AbstractRedirect {

        @Override
        protected boolean isNotifyMailet() {
            return false;
        }

        @Override
        protected String[] getAllowedInitParameters() {
            return null;
        }
    }

    @Test
    public void determineMailHeaderEncodingCharsetShouldReturnNullWhenRawTextIsNull() {
        String charset = testee.determineMailHeaderEncodingCharset(null);

        assertThat(charset).isNull();
    }

    @Test
    public void determineMailHeaderEncodingCharsetShouldReturnNullWhenRawTextIsEmpty() {
        String charset = testee.determineMailHeaderEncodingCharset("");

        assertThat(charset).isNull();
    }

    @Test
    public void determineMailHeaderEncodingCharsetShouldReturnNullWhenRawTextDoesNotContainEncodingPrefix() {
        String charset = testee.determineMailHeaderEncodingCharset("iso-8859-2?Q?leg=FAjabb_pr=F3ba_l=F5elemmel?=");

        assertThat(charset).isNull();
    }

    @Test
    public void determineMailHeaderEncodingCharsetShouldReturnNullWhenRawTextDoesNotContainSecondQuestionMark() {
        String charset = testee.determineMailHeaderEncodingCharset("=?iso-8859-2");

        assertThat(charset).isNull();
    }

    @Test
    public void determineMailHeaderEncodingCharsetShouldReturnNullWhenRawTextDoesNotContainCharset() {
        String charset = testee.determineMailHeaderEncodingCharset("=??");

        assertThat(charset).isNull();
    }

    @Test
    public void determineMailHeaderEncodingCharsetShouldReturnNullWhenRawTextDoesNotContainThirdQuestionMark() {
        String charset = testee.determineMailHeaderEncodingCharset("=?iso-8859-2?");

        assertThat(charset).isNull();
    }

    @Test
    public void determineMailHeaderEncodingCharsetShouldReturnNullWhenRawTextDoesNotContainClosingTag() {
        String charset = testee.determineMailHeaderEncodingCharset("=?iso-8859-2?Q?leg=FAjabb_pr=F3ba_l=F5elemmel");

        assertThat(charset).isNull();
    }

    @Test
    public void determineMailHeaderEncodingCharsetShouldReturnCharsetWhenRawTextIsWellFormatted() {
        String charset = testee.determineMailHeaderEncodingCharset("=?iso-8859-2?Q?leg=FAjabb_pr=F3ba_l=F5elemmel?=");

        assertThat(charset).isEqualTo("iso-8859-2");
    }

    @Test
    public void replaceMailAddressesShouldReturnEmptyWhenEmptyList() throws Exception {
        FakeMail mail = FakeMail.builder()
                .build();

        Collection<MailAddress> addresses = testee.replaceMailAddresses(mail, ImmutableList.<MailAddress> of());

        assertThat(addresses).isEmpty();
    }

    @Test
    public void replaceMailAddressesShouldReturnSameContentWhenAddressesDoesntMatchAddressMarkerDomain() throws Exception {
        FakeMail mail = FakeMail.builder()
                .build();

        MailAddress mailAddress = new MailAddress("user", "addres.marker");
        MailAddress mailAddress2 = new MailAddress("user2", "address.mar");
        ImmutableList<MailAddress> list = ImmutableList.of(mailAddress, mailAddress2);

        Collection<MailAddress> addresses = testee.replaceMailAddresses(mail, list);

        assertThat(addresses).containsOnly(mailAddress, mailAddress2);
    }

    @Test
    public void replaceMailAddressesShouldReturnSenderWhenAddressesMatchesSender() throws Exception {
        MailAddress sender = new MailAddress("user", "james.org");
        FakeMail mail = FakeMail.builder()
                .sender(sender)
                .build();

        Collection<MailAddress> addresses = testee.replaceMailAddresses(mail, ImmutableList.of(SpecialAddress.SENDER));

        assertThat(addresses).containsOnly(sender);
    }

    @Test
    public void replaceMailAddressesShouldReturnSenderWhenAddressesMatchesFrom() throws Exception {
        MailAddress sender = new MailAddress("user", "james.org");
        FakeMail mail = FakeMail.builder()
                .sender(sender)
                .build();

        Collection<MailAddress> addresses = testee.replaceMailAddresses(mail, ImmutableList.of(SpecialAddress.FROM));

        assertThat(addresses).containsOnly(sender);
    }

    @Test
    public void replaceMailAddressesShouldReturnEmptyWhenAddressesMatchesSenderAndSenderIsNull() throws Exception {
        FakeMail mail = FakeMail.builder()
                .build();

        Collection<MailAddress> addresses = testee.replaceMailAddresses(mail, ImmutableList.of(SpecialAddress.SENDER));

        assertThat(addresses).isEmpty();
    }

    @Test
    public void replaceMailAddressesShouldReturnEmptyWhenAddressesMatchesReplyToAndReplyToIsNull() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        FakeMail mail = FakeMail.builder()
                .mimeMessage(message)
                .build();

        Collection<MailAddress> addresses = testee.replaceMailAddresses(mail, ImmutableList.of(SpecialAddress.REPLY_TO));

        assertThat(addresses).isEmpty();
    }

    @Test
    public void replaceMailAddressesShouldReturnReplyToWhenAddressesMatchesReplyTo() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setReplyTo(InternetAddress.parse("user@james.org, user2@james.org"));
        FakeMail mail = FakeMail.builder()
                .mimeMessage(message)
                .build();

        MailAddress expectedReplyTo = new MailAddress("user", "james.org");
        MailAddress expectedReplyTo2 = new MailAddress("user2", "james.org");

        Collection<MailAddress> addresses = testee.replaceMailAddresses(mail, ImmutableList.of(SpecialAddress.REPLY_TO));

        assertThat(addresses).containsOnly(expectedReplyTo, expectedReplyTo2);
    }

    @Test
    public void replaceMailAddressesShouldReturnSenderWhenAddressesMatchesReplyToAndNoReplyTo() throws Exception {
        MailAddress sender = new MailAddress("user", "james.org");
        FakeMail mail = FakeMail.builder()
                .sender(sender)
                .mimeMessage(new MimeMessage(Session.getDefaultInstance(new Properties())))
                .build();

        Collection<MailAddress> addresses = testee.replaceMailAddresses(mail, ImmutableList.of(SpecialAddress.REPLY_TO));

        assertThat(addresses).containsOnly(sender);
    }

    @Test
    public void replaceMailAddressesShouldReturnSenderWhenAddressesMatchesReversePath() throws Exception {
        MailAddress sender = new MailAddress("user", "james.org");
        FakeMail mail = FakeMail.builder()
                .sender(sender)
                .build();

        Collection<MailAddress> addresses = testee.replaceMailAddresses(mail, ImmutableList.of(SpecialAddress.REVERSE_PATH));

        assertThat(addresses).containsOnly(sender);
    }

    @Test
    public void replaceMailAddressesShouldReturnEmptyWhenAddressesMatchesReversePathAndNoSender() throws Exception {
        FakeMail mail = FakeMail.builder()
                .build();

        Collection<MailAddress> addresses = testee.replaceMailAddresses(mail, ImmutableList.of(SpecialAddress.REVERSE_PATH));

        assertThat(addresses).isEmpty();
    }

    @Test
    public void replaceMailAddressesShouldReturnRecipientsWhenAddressesMatchesRecipients() throws Exception {
        MailAddress recipient = new MailAddress("user", "james.org");
        MailAddress recipient2 = new MailAddress("user2", "james.org");
        FakeMail mail = FakeMail.builder()
                .recipients(recipient, recipient2)
                .build();

        Collection<MailAddress> addresses = testee.replaceMailAddresses(mail, ImmutableList.of(SpecialAddress.RECIPIENTS));

        assertThat(addresses).containsOnly(recipient, recipient2);
    }

    @Test
    public void replaceMailAddressesShouldReturnRecipientsWhenAddressesMatchesTo() throws Exception {
        MailAddress recipient = new MailAddress("user", "james.org");
        MailAddress recipient2 = new MailAddress("user2", "james.org");
        FakeMail mail = FakeMail.builder()
                .recipients(recipient, recipient2)
                .build();

        Collection<MailAddress> addresses = testee.replaceMailAddresses(mail, ImmutableList.of(SpecialAddress.TO));

        assertThat(addresses).containsOnly(recipient, recipient2);
    }

    @Test
    public void replaceMailAddressesShouldReturnEmptyWhenAddressesMatchesUnaltered() throws Exception {
        FakeMail mail = FakeMail.builder()
                .build();

        Collection<MailAddress> addresses = testee.replaceMailAddresses(mail, ImmutableList.of(SpecialAddress.UNALTERED));

        assertThat(addresses).isEmpty();
    }

    @Test
    public void replaceMailAddressesShouldReturnEmptyWhenAddressesMatchesNull() throws Exception {
        FakeMail mail = FakeMail.builder()
                .build();

        Collection<MailAddress> addresses = testee.replaceMailAddresses(mail, ImmutableList.of(SpecialAddress.NULL));

        assertThat(addresses).isEmpty();
    }

    @Test
    public void replaceMailAddressesShouldReturnSameAddressWhenAddressesDoesntMatch() throws Exception {
        FakeMail mail = FakeMail.builder()
                .build();

        MailAddress address = new MailAddress("user", "address.marker");
        MailAddress address2 = new MailAddress("user2", "address.marker");
        Collection<MailAddress> addresses = testee.replaceMailAddresses(mail, ImmutableList.of(address, address2));

        assertThat(addresses).containsOnly(address, address2);
    }

    @Test
    public void replaceMailAddressesShouldSameListWhenAddressesMatchesDelete() throws Exception {
        FakeMail mail = FakeMail.builder()
                .build();

        Collection<MailAddress> addresses = testee.replaceMailAddresses(mail, ImmutableList.of(SpecialAddress.DELETE));

        MailAddress expected = new MailAddress("delete", "address.marker");
        assertThat(addresses).containsOnly(expected);
    }

    @Test
    public void replaceInternetAddressesShouldReturnEmptyWhenEmptyList() throws Exception {
        FakeMail mail = FakeMail.builder()
                .build();

        Collection<InternetAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.<InternetAddress> of());

        assertThat(addresses).isEmpty();
    }

    @Test
    public void replaceInternetAddressesShouldReturnSameContentWhenAddressesDoesntMatchAddressMarkerDomain() throws Exception {
        FakeMail mail = FakeMail.builder()
                .build();

        InternetAddress internetAddress = new InternetAddress("user@addres.marker");
        InternetAddress internetAddress2 = new InternetAddress("user2@address.mar");
        ImmutableList<InternetAddress> list = ImmutableList.of(internetAddress, internetAddress2);

        Collection<InternetAddress> addresses = testee.replaceInternetAddresses(mail, list);

        assertThat(addresses).containsOnly(internetAddress, internetAddress2);
    }

    @Test
    public void replaceInternetAddressesShouldReturnSenderWhenAddressesMatchesSender() throws Exception {
        MailAddress sender = new MailAddress("user", "james.org");
        FakeMail mail = FakeMail.builder()
                .sender(sender)
                .build();

        Collection<InternetAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.SENDER.toInternetAddress()));

        assertThat(addresses).containsOnly(sender.toInternetAddress());
    }

    @Test
    public void replaceInternetAddressesShouldReturnFromWhenAddressesMatchesFrom() throws Exception {
        MailAddress from = new MailAddress("user", "james.org");
        MailAddress from2 = new MailAddress("user2", "james.org");
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.addFrom(new InternetAddress[] { from.toInternetAddress(), from2.toInternetAddress() });
        FakeMail mail = FakeMail.builder()
                .mimeMessage(message)
                .build();

        Collection<InternetAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.FROM.toInternetAddress()));

        assertThat(addresses).containsOnly(from.toInternetAddress(), from2.toInternetAddress());
    }

    @Test
    public void replaceInternetAddressesShouldReturnSenderWhenAddressesMatchesFromAndNoFrom() throws Exception {
        MailAddress sender = new MailAddress("user", "james.org");
        FakeMail mail = FakeMail.builder()
                .mimeMessage(new MimeMessage(Session.getDefaultInstance(new Properties())))
                .sender(sender)
                .build();

        Collection<InternetAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.FROM.toInternetAddress()));

        assertThat(addresses).containsOnly(sender.toInternetAddress());
    }

    @Test
    public void replaceInternetAddressesShouldReturnEmptyWhenAddressesMatchesSenderAndSenderIsNull() throws Exception {
        FakeMail mail = FakeMail.builder()
                .build();

        Collection<InternetAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.SENDER.toInternetAddress()));

        assertThat(addresses).isEmpty();
    }

    @Test
    public void replaceInternetAddressesShouldReturnEmptyWhenAddressesMatchesReplyToAndReplyToIsNull() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        FakeMail mail = FakeMail.builder()
                .mimeMessage(message)
                .build();

        Collection<InternetAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.REPLY_TO.toInternetAddress()));

        assertThat(addresses).isEmpty();
    }

    @Test
    public void replaceInternetAddressesShouldReturnReplyToWhenAddressesMatchesReplyTo() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setReplyTo(InternetAddress.parse("user@james.org, user2@james.org"));
        FakeMail mail = FakeMail.builder()
                .mimeMessage(message)
                .build();

        MailAddress expectedReplyTo = new MailAddress("user", "james.org");
        MailAddress expectedReplyTo2 = new MailAddress("user2", "james.org");

        Collection<InternetAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.REPLY_TO.toInternetAddress()));

        assertThat(addresses).containsOnly(expectedReplyTo.toInternetAddress(), expectedReplyTo2.toInternetAddress());
    }

    @Test
    public void replaceInternetAddressesShouldReturnSenderWhenAddressesMatchesReplyToAndNoReplyTo() throws Exception {
        MailAddress sender = new MailAddress("user", "james.org");
        FakeMail mail = FakeMail.builder()
                .sender(sender)
                .mimeMessage(new MimeMessage(Session.getDefaultInstance(new Properties())))
                .build();

        Collection<InternetAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.REPLY_TO.toInternetAddress()));

        assertThat(addresses).containsOnly(sender.toInternetAddress());
    }

    @Test
    public void replaceInternetAddressesShouldReturnSenderWhenAddressesMatchesReversePath() throws Exception {
        MailAddress sender = new MailAddress("user", "james.org");
        FakeMail mail = FakeMail.builder()
                .sender(sender)
                .build();

        Collection<InternetAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.REVERSE_PATH.toInternetAddress()));

        assertThat(addresses).containsOnly(sender.toInternetAddress());
    }

    @Test
    public void replaceInternetAddressesShouldReturnEmptyWhenAddressesMatchesReversePathAndNoSender() throws Exception {
        FakeMail mail = FakeMail.builder()
                .build();

        Collection<InternetAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.REVERSE_PATH.toInternetAddress()));

        assertThat(addresses).isEmpty();
    }

    @Test
    public void replaceInternetAddressesShouldReturnToWhenAddressesMatchesRecipients() throws Exception {
        MailAddress to = new MailAddress("user", "james.org");
        MailAddress to2 = new MailAddress("user2", "james.org");
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.addHeader(RFC2822Headers.TO, "user@james.org, user2@james.org");
        FakeMail mail = FakeMail.builder()
                .mimeMessage(message)
                .build();

        Collection<InternetAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.RECIPIENTS.toInternetAddress()));

        assertThat(addresses).containsOnly(to.toInternetAddress(), to2.toInternetAddress());
    }

    @Test
    public void replaceInternetAddressesShouldReturnToWhenAddressesMatchesTo() throws Exception {
        MailAddress to = new MailAddress("user", "james.org");
        MailAddress to2 = new MailAddress("user2", "james.org");
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.addHeader(RFC2822Headers.TO, "user@james.org, user2@james.org");
        FakeMail mail = FakeMail.builder()
                .mimeMessage(message)
                .build();

        Collection<InternetAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.TO.toInternetAddress()));

        assertThat(addresses).containsOnly(to.toInternetAddress(), to2.toInternetAddress());
    }

    @Test
    public void replaceInternetAddressesShouldReturnEmptyWhenAddressesMatchesUnaltered() throws Exception {
        FakeMail mail = FakeMail.builder()
                .build();

        Collection<InternetAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.UNALTERED.toInternetAddress()));

        assertThat(addresses).isEmpty();
    }

    @Test
    public void replaceInternetAddressesShouldReturnEmptyWhenAddressesMatchesNull() throws Exception {
        FakeMail mail = FakeMail.builder()
                .build();

        Collection<InternetAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.NULL.toInternetAddress()));

        assertThat(addresses).isEmpty();
    }

    @Test
    public void replaceInternetAddressesShouldReturnSameAddressWhenAddressesDoesntMatch() throws Exception {
        FakeMail mail = FakeMail.builder()
                .build();

        MailAddress address = new MailAddress("user", "address.marker");
        MailAddress address2 = new MailAddress("user2", "address.marker");
        Collection<InternetAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(address.toInternetAddress(), address2.toInternetAddress()));

        assertThat(addresses).containsOnly(address.toInternetAddress(), address2.toInternetAddress());
    }

    @Test
    public void replaceInternetAddressesShouldSameListWhenAddressesMatchesDelete() throws Exception {
        FakeMail mail = FakeMail.builder()
                .build();

        Collection<InternetAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.DELETE.toInternetAddress()));

        InternetAddress expected = new InternetAddress("delete@address.marker");
        assertThat(addresses).containsOnly(expected);
    }
}
