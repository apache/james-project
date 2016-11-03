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

import java.util.Collection;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.james.transport.mailets.redirect.SpecialAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class SpecialAddressesUtilsTest {

    private SpecialAddressesUtils testee;

    @Before
    public void setup() {
        testee = SpecialAddressesUtils.from(new GenericMailet() {
            
            @Override
            public void service(Mail mail) throws MessagingException {
            }
        });
    }

    @Test
    public void replaceMailAddressesShouldReturnEmptyWhenEmptyList() throws Exception {
        FakeMail mail = FakeMail.builder()
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.<MailAddress> of());

        assertThat(addresses).isEmpty();
    }

    @Test
    public void replaceMailAddressesShouldReturnSameContentWhenAddressesDoesntMatchAddressMarkerDomain() throws Exception {
        FakeMail mail = FakeMail.builder()
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
                .sender(sender)
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.SENDER));

        assertThat(addresses).containsOnly(sender);
    }

    @Test
    public void replaceMailAddressesShouldReturnSenderWhenAddressesMatchFrom() throws Exception {
        MailAddress sender = MailAddressFixture.ANY_AT_JAMES;
        FakeMail mail = FakeMail.builder()
                .sender(sender)
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.FROM));

        assertThat(addresses).containsOnly(sender);
    }

    @Test
    public void replaceMailAddressesShouldReturnEmptyWhenAddressesMatchSenderAndSenderIsNull() throws Exception {
        FakeMail mail = FakeMail.builder()
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.SENDER));

        assertThat(addresses).isEmpty();
    }

    @Test
    public void replaceMailAddressesShouldReturnEmptyWhenAddressesMatchReplyToAndReplyToIsNull() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        FakeMail mail = FakeMail.from(message);

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.REPLY_TO));

        assertThat(addresses).isEmpty();
    }

    @Test
    public void replaceMailAddressesShouldReturnReplyToWhenAddressesMatchReplyTo() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
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
                .sender(sender)
                .mimeMessage(new MimeMessage(Session.getDefaultInstance(new Properties())))
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.REPLY_TO));

        assertThat(addresses).containsOnly(sender);
    }

    @Test
    public void replaceMailAddressesShouldReturnSenderWhenAddressesMatchReversePath() throws Exception {
        MailAddress sender = MailAddressFixture.ANY_AT_JAMES;
        FakeMail mail = FakeMail.builder()
                .sender(sender)
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.REVERSE_PATH));

        assertThat(addresses).containsOnly(sender);
    }

    @Test
    public void replaceMailAddressesShouldReturnEmptyWhenAddressesMatchReversePathAndNoSender() throws Exception {
        FakeMail mail = FakeMail.builder()
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.REVERSE_PATH));

        assertThat(addresses).isEmpty();
    }

    @Test
    public void replaceMailAddressesShouldReturnRecipientsWhenAddressesMatchRecipients() throws Exception {
        MailAddress recipient = MailAddressFixture.ANY_AT_JAMES;
        MailAddress recipient2 = MailAddressFixture.OTHER_AT_JAMES;
        FakeMail mail = FakeMail.builder()
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
                .recipients(recipient, recipient2)
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.TO));

        assertThat(addresses).containsOnly(recipient, recipient2);
    }

    @Test
    public void replaceMailAddressesShouldReturnEmptyWhenAddressesMatchUnaltered() throws Exception {
        FakeMail mail = FakeMail.builder()
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.UNALTERED));

        assertThat(addresses).isEmpty();
    }

    @Test
    public void replaceMailAddressesShouldReturnEmptyWhenAddressesMatchNull() throws Exception {
        FakeMail mail = FakeMail.builder()
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.NULL));

        assertThat(addresses).isEmpty();
    }

    @Test
    public void replaceMailAddressesShouldReturnSameAddressWhenAddressesDoesntMatch() throws Exception {
        FakeMail mail = FakeMail.builder()
                .build();

        MailAddress address = new MailAddress("user", "address.marker");
        MailAddress address2 = new MailAddress("user2", "address.marker");
        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(address, address2));

        assertThat(addresses).containsOnly(address, address2);
    }

    @Test
    public void replaceMailAddressesShouldReturnSameListWhenAddressesMatchDelete() throws Exception {
        FakeMail mail = FakeMail.builder()
                .build();

        Collection<MailAddress> addresses = testee.replaceSpecialAddresses(mail, ImmutableList.of(SpecialAddress.DELETE));

        MailAddress expected = new MailAddress("delete", "address.marker");
        assertThat(addresses).containsOnly(expected);
    }
}
