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
package org.apache.james.transport.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.transport.mailets.redirect.RedirectNotify;
import org.apache.james.transport.mailets.redirect.SpecialAddress;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.MailetContext;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class SpecialAddressesUtilsTest {

    private MailAddress postmaster;
    private SpecialAddressesUtils testee;

    @BeforeEach
    void setup() throws Exception {
        final MailetContext mailetContext = mock(MailetContext.class);
        postmaster = new MailAddress("postmaster@james.org");
        when(mailetContext.getPostmaster())
            .thenReturn(postmaster);

        RedirectNotify mailet = mock(RedirectNotify.class);
        when(mailet.getMailetContext())
            .thenReturn(mailetContext);
        testee = SpecialAddressesUtils.from(mailet);
    }

    @Test
    void replaceMailAddressesShouldReturnEmptyWhenEmptyList() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.<MailAddress>of());

        assertThat(addresses).isEmpty();
    }

    @Test
    void replaceMailAddressesShouldReturnSameContentWhenAddressesDoesntMatchAddressMarkerDomain() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .build();

        MailAddress mailAddress = new MailAddress("user", "addres.marker");
        MailAddress mailAddress2 = new MailAddress("user2", "address.mar");
        ImmutableList<MailAddress> list = ImmutableList.of(mailAddress, mailAddress2);

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, list);

        assertThat(addresses).containsOnly(mailAddress, mailAddress2);
    }

    @Test
    void replaceMailAddressesShouldReturnSenderWhenAddressesMatchSender() throws Exception {
        MailAddress sender = MailAddressFixture.ANY_AT_JAMES;
        FakeMail mail = FakeMail.builder()
                .name("name")
                .sender(sender)
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.SENDER));

        assertThat(addresses).containsOnly(sender);
    }

    @Test
    void replaceMailAddressesShouldReturnSenderWhenAddressesMatchFrom() throws Exception {
        MailAddress sender = MailAddressFixture.ANY_AT_JAMES;
        FakeMail mail = FakeMail.builder()
                .name("name")
                .sender(sender)
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.FROM));

        assertThat(addresses).containsOnly(sender);
    }

    @Test
    void replaceMailAddressesShouldReturnEmptyWhenAddressesMatchSenderAndSenderIsNull() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.SENDER));

        assertThat(addresses).isEmpty();
    }

    @Test
    void replaceMailAddressesShouldReturnEmptyWhenAddressesMatchReplyToAndReplyToIsNull() throws Exception {
        FakeMail mail = FakeMail.from(MimeMessageBuilder.mimeMessageBuilder());

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.REPLY_TO));

        assertThat(addresses).isEmpty();
    }

    @Test
    void replaceMailAddressesShouldReturnReplyToWhenAddressesMatchReplyTo() throws Exception {
        MimeMessage message = MimeMessageUtil.defaultMimeMessage();
        message.setReplyTo(InternetAddress.parse(MailAddressFixture.ANY_AT_JAMES.toString() + ", " + MailAddressFixture.OTHER_AT_JAMES.toString()));
        FakeMail mail = FakeMail.from(message);

        MailAddress expectedReplyTo = MailAddressFixture.ANY_AT_JAMES;
        MailAddress expectedReplyTo2 = MailAddressFixture.OTHER_AT_JAMES;

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.REPLY_TO));

        assertThat(addresses).containsOnly(expectedReplyTo, expectedReplyTo2);
    }

    @Test
    void replaceMailAddressesShouldReturnSenderWhenAddressesMatchReplyToAndNoReplyTo() throws Exception {
        MailAddress sender = MailAddressFixture.ANY_AT_JAMES;
        FakeMail mail = FakeMail.builder()
                .name("name")
                .sender(sender)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder())
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.REPLY_TO));

        assertThat(addresses).containsOnly(sender);
    }

    @Test
    void replaceMailAddressesShouldReturnSenderWhenAddressesMatchReversePath() throws Exception {
        MailAddress sender = MailAddressFixture.ANY_AT_JAMES;
        FakeMail mail = FakeMail.builder()
                .name("name")
                .sender(sender)
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.REVERSE_PATH));

        assertThat(addresses).containsOnly(sender);
    }

    @Test
    void replaceMailAddressesShouldReturnEmptyWhenAddressesMatchReversePathAndNoSender() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.REVERSE_PATH));

        assertThat(addresses).isEmpty();
    }

    @Test
    void replaceMailAddressesShouldReturnRecipientsWhenAddressesMatchRecipients() throws Exception {
        MailAddress recipient = MailAddressFixture.ANY_AT_JAMES;
        MailAddress recipient2 = MailAddressFixture.OTHER_AT_JAMES;
        FakeMail mail = FakeMail.builder()
                .name("name")
                .recipients(recipient, recipient2)
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.RECIPIENTS));

        assertThat(addresses).containsOnly(recipient, recipient2);
    }

    @Test
    void replaceMailAddressesShouldReturnRecipientsWhenAddressesMatchTo() throws Exception {
        MailAddress recipient = MailAddressFixture.ANY_AT_JAMES;
        MailAddress recipient2 = MailAddressFixture.OTHER_AT_JAMES;
        FakeMail mail = FakeMail.builder()
                .name("name")
                .recipients(recipient, recipient2)
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.TO));

        assertThat(addresses).containsOnly(recipient, recipient2);
    }

    @Test
    void replaceMailAddressesShouldReturnEmptyWhenAddressesMatchUnaltered() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.UNALTERED));

        assertThat(addresses).isEmpty();
    }

    @Test
    void replaceMailAddressesShouldReturnEmptyWhenAddressesMatchNull() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.NULL));

        assertThat(addresses).isEmpty();
    }

    @Test
    void replaceMailAddressesShouldReturnSameAddressWhenAddressesDoesntMatch() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .build();

        MailAddress address = new MailAddress("user", "address.marker");
        MailAddress address2 = new MailAddress("user2", "address.marker");
        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(address, address2));

        assertThat(addresses).containsOnly(address, address2);
    }

    @Test
    void replaceMailAddressesShouldReturnSameListWhenAddressesMatchDelete() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.DELETE));

        MailAddress expected = new MailAddress("delete", "address.marker");
        assertThat(addresses).containsOnly(expected);
    }

    @Test
    void replaceInternetAddressesShouldReturnEmptyWhenEmptyList() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .build();

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.<InternetAddress>of());

        assertThat(addresses).isEmpty();
    }

    @Test
    void replaceInternetAddressesShouldReturnSameContentWhenAddressesDoesntMatchAddressMarkerDomain() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .build();

        InternetAddress internetAddress = new InternetAddress("user@addres.marker");
        InternetAddress internetAddress2 = new InternetAddress("user2@address.mar");
        ImmutableList<InternetAddress> list = ImmutableList.of(internetAddress, internetAddress2);

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, list);

        assertThat(addresses).containsOnly(new MailAddress(internetAddress), new MailAddress(internetAddress2));
    }

    @Test
    void replaceInternetAddressesShouldReturnSenderWhenAddressesMatchSender() throws Exception {
        MailAddress sender = MailAddressFixture.ANY_AT_JAMES;
        FakeMail mail = FakeMail.builder()
                .name("name")
                .sender(sender)
                .build();

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.SENDER.toInternetAddress().get()));

        assertThat(addresses).containsOnly(sender);
    }

    @Test
    void replaceInternetAddressesShouldReturnFromWhenAddressesMatchFrom() throws Exception {
        MailAddress from = MailAddressFixture.ANY_AT_JAMES;
        MailAddress from2 = MailAddressFixture.OTHER_AT_JAMES;
        FakeMail mail = FakeMail.from(MimeMessageBuilder.mimeMessageBuilder()
            .addFrom(from.toInternetAddress().get(), from2.toInternetAddress().get()));

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.FROM.toInternetAddress().get()));

        assertThat(addresses).containsOnly(from, from2);
    }

    @Test
    void replaceInternetAddressesShouldReturnSenderWhenAddressesMatchFromAndNoFrom() throws Exception {
        MailAddress sender = MailAddressFixture.ANY_AT_JAMES;
        FakeMail mail = FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder())
                .sender(sender)
                .build();

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.FROM.toInternetAddress().get()));

        assertThat(addresses).containsOnly(sender);
    }

    @Test
    void replaceInternetAddressesShouldReturnEmptyWhenAddressesMatchSenderAndSenderIsNull() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .build();

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.SENDER.toInternetAddress().get()));

        assertThat(addresses).isEmpty();
    }

    @Test
    void replaceInternetAddressesShouldReturnEmptyWhenAddressesMatchReplyToAndReplyToIsNull() throws Exception {
        FakeMail mail = FakeMail.from(MimeMessageBuilder.mimeMessageBuilder());

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.REPLY_TO.toInternetAddress().get()));

        assertThat(addresses).isEmpty();
    }

    @Test
    void replaceInternetAddressesShouldReturnReplyToWhenAddressesMatchReplyTo() throws Exception {
        MimeMessage message = MimeMessageUtil.defaultMimeMessage();
        message.setReplyTo(InternetAddress.parse(MailAddressFixture.ANY_AT_JAMES.toString() + ", " + MailAddressFixture.OTHER_AT_JAMES.toString()));
        FakeMail mail = FakeMail.from(message);

        MailAddress expectedReplyTo = MailAddressFixture.ANY_AT_JAMES;
        MailAddress expectedReplyTo2 = MailAddressFixture.OTHER_AT_JAMES;

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.REPLY_TO.toInternetAddress().get()));

        assertThat(addresses).containsOnly(expectedReplyTo, expectedReplyTo2);
    }

    @Test
    void replaceInternetAddressesShouldReturnSenderWhenAddressesMatchReplyToAndNoReplyTo() throws Exception {
        MailAddress sender = MailAddressFixture.ANY_AT_JAMES;
        FakeMail mail = FakeMail.builder()
                .name("name")
                .sender(sender)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder())
                .build();

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.REPLY_TO.toInternetAddress().get()));

        assertThat(addresses).containsOnly(sender);
    }

    @Test
    void replaceInternetAddressesShouldReturnSenderWhenAddressesMatchReversePath() throws Exception {
        MailAddress sender = MailAddressFixture.ANY_AT_JAMES;
        FakeMail mail = FakeMail.builder()
                .name("name")
                .sender(sender)
                .build();

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.REVERSE_PATH.toInternetAddress().get()));

        assertThat(addresses).containsOnly(sender);
    }

    @Test
    void replaceInternetAddressesShouldReturnEmptyWhenAddressesMatchReversePathAndNoSender() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .build();

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.REVERSE_PATH.toInternetAddress().get()));

        assertThat(addresses).isEmpty();
    }

    @Test
    void replaceInternetAddressesShouldReturnToWhenAddressesMatchRecipients() throws Exception {
        MailAddress to = MailAddressFixture.ANY_AT_JAMES;
        MailAddress to2 = MailAddressFixture.OTHER_AT_JAMES;
        FakeMail mail = FakeMail.from(MimeMessageBuilder.mimeMessageBuilder()
            .addToRecipient(to.asString(), to2.asString()));

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.RECIPIENTS.toInternetAddress().get()));

        assertThat(addresses).containsOnly(to, to2);
    }

    @Test
    void replaceInternetAddressesShouldReturnToWhenAddressesMatchTo() throws Exception {
        MailAddress to = MailAddressFixture.ANY_AT_JAMES;
        MailAddress to2 = MailAddressFixture.OTHER_AT_JAMES;
        FakeMail mail = FakeMail.from(MimeMessageBuilder.mimeMessageBuilder()
            .addToRecipient(to.asString(), to2.asString()));

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.TO.toInternetAddress().get()));

        assertThat(addresses).containsOnly(to, to2);
    }

    @Test
    void replaceInternetAddressesShouldReturnEmptyWhenAddressesMatchUnaltered() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .build();

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.UNALTERED.toInternetAddress().get()));

        assertThat(addresses).isEmpty();
    }

    @Test
    void replaceInternetAddressesShouldReturnEmptyWhenAddressesMatchNull() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .build();

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.NULL.toInternetAddress().get()));

        assertThat(addresses).isEmpty();
    }

    @Test
    void replaceInternetAddressesShouldReturnSameAddressWhenAddressesDoesntMatch() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .build();

        MailAddress address = new MailAddress("user", "address.marker");
        MailAddress address2 = new MailAddress("user2", "address.marker");
        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(address.toInternetAddress().get(), address2.toInternetAddress().get()));

        assertThat(addresses).containsOnly(address, address2);
    }

    @Test
    void replaceInternetAddressesShouldReturnSameListWhenAddressesMatchDelete() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .build();

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.DELETE.toInternetAddress().get()));

        MailAddress expected = new MailAddress("delete@address.marker");
        assertThat(addresses).containsOnly(expected);
    }

    @Test
    void getFirstSpecialAddressIfMatchingOrGivenAddressShouldThrowWhenSenderInitParameterIsNull() throws Exception {
        assertThatThrownBy(() -> testee.getFirstSpecialAddressIfMatchingOrGivenAddress(null, ImmutableList.of("postmaster", "sender", "unaltered")))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void getFirstSpecialAddressIfMatchingOrGivenAddressShouldReturnAbsentWhenSenderInitParameterIsAbsent() throws Exception {
        Optional<MailAddress> sender = testee.getFirstSpecialAddressIfMatchingOrGivenAddress(Optional.empty(), ImmutableList.of("postmaster", "sender", "unaltered"));

        assertThat(sender).isEmpty();
    }

    @Test
    void getFirstSpecialAddressIfMatchingOrGivenAddressShouldReturnAbsentWhenSenderInitParameterIsEmpty() throws Exception {
        Optional<MailAddress> sender = testee.getFirstSpecialAddressIfMatchingOrGivenAddress(Optional.of(""), ImmutableList.of("postmaster", "sender", "unaltered"));

        assertThat(sender).isEmpty();
    }

    @Test
    void getFirstSpecialAddressIfMatchingOrGivenAddressShouldReturnGivenAddressWhenNoSpecialAddressMatches() throws Exception {
        Optional<MailAddress> sender = testee.getFirstSpecialAddressIfMatchingOrGivenAddress(Optional.of("test@james.org"), ImmutableList.of("postmaster", "sender", "unaltered"));

        MailAddress expectedMailAddress = new MailAddress("test", "james.org");
        assertThat(sender).contains(expectedMailAddress);
    }

    @Test
    void getFirstSpecialAddressIfMatchingOrGivenAddressShouldReturnFirstSpecialAddressWhenMatching() throws Exception {
        Optional<MailAddress> sender = testee.getFirstSpecialAddressIfMatchingOrGivenAddress(Optional.of("postmaster"), ImmutableList.of("postmaster", "sender", "unaltered"));

        assertThat(sender).contains(postmaster);
    }

    @Test
    void getFirstSpecialAddressIfMatchingOrGivenAddressShouldReturnSecondSpecialAddressWhenMatching() throws Exception {
        Optional<MailAddress> sender = testee.getFirstSpecialAddressIfMatchingOrGivenAddress(Optional.of("sender"), ImmutableList.of("postmaster", "sender", "unaltered"));

        MailAddress expectedMailAddress = SpecialAddress.SENDER;
        assertThat(sender).contains(expectedMailAddress);
    }

    @Test
    void getFirstSpecialAddressIfMatchingOrGivenAddressShouldReturnLastSpecialAddressWhenMatching() throws Exception {
        Optional<MailAddress> sender = testee.getFirstSpecialAddressIfMatchingOrGivenAddress(Optional.of("unaltered"), ImmutableList.of("postmaster", "sender", "unaltered"));

        MailAddress expectedMailAddress = SpecialAddress.UNALTERED;
        assertThat(sender).contains(expectedMailAddress);
    }
}
