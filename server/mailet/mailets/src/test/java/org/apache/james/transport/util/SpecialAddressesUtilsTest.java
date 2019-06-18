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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.transport.mailets.redirect.RedirectNotify;
import org.apache.james.transport.mailets.redirect.SpecialAddress;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.MailetContext;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableList;

public class SpecialAddressesUtilsTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private MailAddress postmaster;
    private SpecialAddressesUtils testee;

    @Before
    public void setup() throws Exception {
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
    public void replaceMailAddressesShouldReturnEmptyWhenEmptyList() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.<MailAddress>of());

        assertThat(addresses).isEmpty();
    }

    @Test
    public void replaceMailAddressesShouldReturnSameContentWhenAddressesDoesntMatchAddressMarkerDomain() throws Exception {
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
    public void replaceMailAddressesShouldReturnSenderWhenAddressesMatchSender() throws Exception {
        MailAddress sender = MailAddressFixture.ANY_AT_JAMES;
        FakeMail mail = FakeMail.builder()
                .name("name")
                .sender(sender)
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.SENDER));

        assertThat(addresses).containsOnly(sender);
    }

    @Test
    public void replaceMailAddressesShouldReturnSenderWhenAddressesMatchFrom() throws Exception {
        MailAddress sender = MailAddressFixture.ANY_AT_JAMES;
        FakeMail mail = FakeMail.builder()
                .name("name")
                .sender(sender)
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.FROM));

        assertThat(addresses).containsOnly(sender);
    }

    @Test
    public void replaceMailAddressesShouldReturnEmptyWhenAddressesMatchSenderAndSenderIsNull() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.SENDER));

        assertThat(addresses).isEmpty();
    }

    @Test
    public void replaceMailAddressesShouldReturnEmptyWhenAddressesMatchReplyToAndReplyToIsNull() throws Exception {
        FakeMail mail = FakeMail.from(MimeMessageBuilder.mimeMessageBuilder());

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.REPLY_TO));

        assertThat(addresses).isEmpty();
    }

    @Test
    public void replaceMailAddressesShouldReturnReplyToWhenAddressesMatchReplyTo() throws Exception {
        MimeMessage message = MimeMessageUtil.defaultMimeMessage();
        message.setReplyTo(InternetAddress.parse(MailAddressFixture.ANY_AT_JAMES.toString() + ", " + MailAddressFixture.OTHER_AT_JAMES.toString()));
        FakeMail mail = FakeMail.from(message);

        MailAddress expectedReplyTo = MailAddressFixture.ANY_AT_JAMES;
        MailAddress expectedReplyTo2 = MailAddressFixture.OTHER_AT_JAMES;

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.REPLY_TO));

        assertThat(addresses).containsOnly(expectedReplyTo, expectedReplyTo2);
    }

    @Test
    public void replaceMailAddressesShouldReturnSenderWhenAddressesMatchReplyToAndNoReplyTo() throws Exception {
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
    public void replaceMailAddressesShouldReturnSenderWhenAddressesMatchReversePath() throws Exception {
        MailAddress sender = MailAddressFixture.ANY_AT_JAMES;
        FakeMail mail = FakeMail.builder()
                .name("name")
                .sender(sender)
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.REVERSE_PATH));

        assertThat(addresses).containsOnly(sender);
    }

    @Test
    public void replaceMailAddressesShouldReturnEmptyWhenAddressesMatchReversePathAndNoSender() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.REVERSE_PATH));

        assertThat(addresses).isEmpty();
    }

    @Test
    public void replaceMailAddressesShouldReturnRecipientsWhenAddressesMatchRecipients() throws Exception {
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
    public void replaceMailAddressesShouldReturnRecipientsWhenAddressesMatchTo() throws Exception {
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
    public void replaceMailAddressesShouldReturnEmptyWhenAddressesMatchUnaltered() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.UNALTERED));

        assertThat(addresses).isEmpty();
    }

    @Test
    public void replaceMailAddressesShouldReturnEmptyWhenAddressesMatchNull() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.NULL));

        assertThat(addresses).isEmpty();
    }

    @Test
    public void replaceMailAddressesShouldReturnSameAddressWhenAddressesDoesntMatch() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .build();

        MailAddress address = new MailAddress("user", "address.marker");
        MailAddress address2 = new MailAddress("user2", "address.marker");
        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(address, address2));

        assertThat(addresses).containsOnly(address, address2);
    }

    @Test
    public void replaceMailAddressesShouldReturnSameListWhenAddressesMatchDelete() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.DELETE));

        MailAddress expected = new MailAddress("delete", "address.marker");
        assertThat(addresses).containsOnly(expected);
    }

    @Test
    public void replaceInternetAddressesShouldReturnEmptyWhenEmptyList() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .build();

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.<InternetAddress>of());

        assertThat(addresses).isEmpty();
    }

    @Test
    public void replaceInternetAddressesShouldReturnSameContentWhenAddressesDoesntMatchAddressMarkerDomain() throws Exception {
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
    public void replaceInternetAddressesShouldReturnSenderWhenAddressesMatchSender() throws Exception {
        MailAddress sender = MailAddressFixture.ANY_AT_JAMES;
        FakeMail mail = FakeMail.builder()
                .name("name")
                .sender(sender)
                .build();

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.SENDER.toInternetAddress()));

        assertThat(addresses).containsOnly(sender);
    }

    @Test
    public void replaceInternetAddressesShouldReturnFromWhenAddressesMatchFrom() throws Exception {
        MailAddress from = MailAddressFixture.ANY_AT_JAMES;
        MailAddress from2 = MailAddressFixture.OTHER_AT_JAMES;
        FakeMail mail = FakeMail.from(MimeMessageBuilder.mimeMessageBuilder()
            .addFrom(from.toInternetAddress(), from2.toInternetAddress()));

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.FROM.toInternetAddress()));

        assertThat(addresses).containsOnly(from, from2);
    }

    @Test
    public void replaceInternetAddressesShouldReturnSenderWhenAddressesMatchFromAndNoFrom() throws Exception {
        MailAddress sender = MailAddressFixture.ANY_AT_JAMES;
        FakeMail mail = FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder())
                .sender(sender)
                .build();

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.FROM.toInternetAddress()));

        assertThat(addresses).containsOnly(sender);
    }

    @Test
    public void replaceInternetAddressesShouldReturnEmptyWhenAddressesMatchSenderAndSenderIsNull() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .build();

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.SENDER.toInternetAddress()));

        assertThat(addresses).isEmpty();
    }

    @Test
    public void replaceInternetAddressesShouldReturnEmptyWhenAddressesMatchReplyToAndReplyToIsNull() throws Exception {
        FakeMail mail = FakeMail.from(MimeMessageBuilder.mimeMessageBuilder());

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.REPLY_TO.toInternetAddress()));

        assertThat(addresses).isEmpty();
    }

    @Test
    public void replaceInternetAddressesShouldReturnReplyToWhenAddressesMatchReplyTo() throws Exception {
        MimeMessage message = MimeMessageUtil.defaultMimeMessage();
        message.setReplyTo(InternetAddress.parse(MailAddressFixture.ANY_AT_JAMES.toString() + ", " + MailAddressFixture.OTHER_AT_JAMES.toString()));
        FakeMail mail = FakeMail.from(message);

        MailAddress expectedReplyTo = MailAddressFixture.ANY_AT_JAMES;
        MailAddress expectedReplyTo2 = MailAddressFixture.OTHER_AT_JAMES;

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.REPLY_TO.toInternetAddress()));

        assertThat(addresses).containsOnly(expectedReplyTo, expectedReplyTo2);
    }

    @Test
    public void replaceInternetAddressesShouldReturnSenderWhenAddressesMatchReplyToAndNoReplyTo() throws Exception {
        MailAddress sender = MailAddressFixture.ANY_AT_JAMES;
        FakeMail mail = FakeMail.builder()
                .name("name")
                .sender(sender)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder())
                .build();

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.REPLY_TO.toInternetAddress()));

        assertThat(addresses).containsOnly(sender);
    }

    @Test
    public void replaceInternetAddressesShouldReturnSenderWhenAddressesMatchReversePath() throws Exception {
        MailAddress sender = MailAddressFixture.ANY_AT_JAMES;
        FakeMail mail = FakeMail.builder()
                .name("name")
                .sender(sender)
                .build();

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.REVERSE_PATH.toInternetAddress()));

        assertThat(addresses).containsOnly(sender);
    }

    @Test
    public void replaceInternetAddressesShouldReturnEmptyWhenAddressesMatchReversePathAndNoSender() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .build();

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.REVERSE_PATH.toInternetAddress()));

        assertThat(addresses).isEmpty();
    }

    @Test
    public void replaceInternetAddressesShouldReturnToWhenAddressesMatchRecipients() throws Exception {
        MailAddress to = MailAddressFixture.ANY_AT_JAMES;
        MailAddress to2 = MailAddressFixture.OTHER_AT_JAMES;
        FakeMail mail = FakeMail.from(MimeMessageBuilder.mimeMessageBuilder()
            .addToRecipient(to.asString(), to2.asString()));

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.RECIPIENTS.toInternetAddress()));

        assertThat(addresses).containsOnly(to, to2);
    }

    @Test
    public void replaceInternetAddressesShouldReturnToWhenAddressesMatchTo() throws Exception {
        MailAddress to = MailAddressFixture.ANY_AT_JAMES;
        MailAddress to2 = MailAddressFixture.OTHER_AT_JAMES;
        FakeMail mail = FakeMail.from(MimeMessageBuilder.mimeMessageBuilder()
            .addToRecipient(to.asString(), to2.asString()));

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.TO.toInternetAddress()));

        assertThat(addresses).containsOnly(to, to2);
    }

    @Test
    public void replaceInternetAddressesShouldReturnEmptyWhenAddressesMatchUnaltered() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .build();

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.UNALTERED.toInternetAddress()));

        assertThat(addresses).isEmpty();
    }

    @Test
    public void replaceInternetAddressesShouldReturnEmptyWhenAddressesMatchNull() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .build();

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.NULL.toInternetAddress()));

        assertThat(addresses).isEmpty();
    }

    @Test
    public void replaceInternetAddressesShouldReturnSameAddressWhenAddressesDoesntMatch() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .build();

        MailAddress address = new MailAddress("user", "address.marker");
        MailAddress address2 = new MailAddress("user2", "address.marker");
        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(address.toInternetAddress(), address2.toInternetAddress()));

        assertThat(addresses).containsOnly(address, address2);
    }

    @Test
    public void replaceInternetAddressesShouldReturnSameListWhenAddressesMatchDelete() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .build();

        List<MailAddress> addresses = testee.replaceInternetAddresses(mail, ImmutableList.of(SpecialAddress.DELETE.toInternetAddress()));

        MailAddress expected = new MailAddress("delete@address.marker");
        assertThat(addresses).containsOnly(expected);
    }

    @Test
    public void getFirstSpecialAddressIfMatchingOrGivenAddressShouldThrowWhenSenderInitParameterIsNull() throws Exception {
        expectedException.expect(NullPointerException.class);
        testee.getFirstSpecialAddressIfMatchingOrGivenAddress(null, ImmutableList.of("postmaster", "sender", "unaltered"));
    }

    @Test
    public void getFirstSpecialAddressIfMatchingOrGivenAddressShouldReturnAbsentWhenSenderInitParameterIsAbsent() throws Exception {
        Optional<MailAddress> sender = testee.getFirstSpecialAddressIfMatchingOrGivenAddress(Optional.empty(), ImmutableList.of("postmaster", "sender", "unaltered"));

        assertThat(sender).isEmpty();
    }

    @Test
    public void getFirstSpecialAddressIfMatchingOrGivenAddressShouldReturnAbsentWhenSenderInitParameterIsEmpty() throws Exception {
        Optional<MailAddress> sender = testee.getFirstSpecialAddressIfMatchingOrGivenAddress(Optional.of(""), ImmutableList.of("postmaster", "sender", "unaltered"));

        assertThat(sender).isEmpty();
    }

    @Test
    public void getFirstSpecialAddressIfMatchingOrGivenAddressShouldReturnGivenAddressWhenNoSpecialAddressMatches() throws Exception {
        Optional<MailAddress> sender = testee.getFirstSpecialAddressIfMatchingOrGivenAddress(Optional.of("test@james.org"), ImmutableList.of("postmaster", "sender", "unaltered"));

        MailAddress expectedMailAddress = new MailAddress("test", "james.org");
        assertThat(sender).contains(expectedMailAddress);
    }

    @Test
    public void getFirstSpecialAddressIfMatchingOrGivenAddressShouldReturnFirstSpecialAddressWhenMatching() throws Exception {
        Optional<MailAddress> sender = testee.getFirstSpecialAddressIfMatchingOrGivenAddress(Optional.of("postmaster"), ImmutableList.of("postmaster", "sender", "unaltered"));

        assertThat(sender).contains(postmaster);
    }

    @Test
    public void getFirstSpecialAddressIfMatchingOrGivenAddressShouldReturnSecondSpecialAddressWhenMatching() throws Exception {
        Optional<MailAddress> sender = testee.getFirstSpecialAddressIfMatchingOrGivenAddress(Optional.of("sender"), ImmutableList.of("postmaster", "sender", "unaltered"));

        MailAddress expectedMailAddress = SpecialAddress.SENDER;
        assertThat(sender).contains(expectedMailAddress);
    }

    @Test
    public void getFirstSpecialAddressIfMatchingOrGivenAddressShouldReturnLastSpecialAddressWhenMatching() throws Exception {
        Optional<MailAddress> sender = testee.getFirstSpecialAddressIfMatchingOrGivenAddress(Optional.of("unaltered"), ImmutableList.of("postmaster", "sender", "unaltered"));

        MailAddress expectedMailAddress = SpecialAddress.UNALTERED;
        assertThat(sender).contains(expectedMailAddress);
    }
}
