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

import java.util.List;

import javax.mail.internet.InternetAddress;

import org.apache.james.core.MailAddress;
import org.apache.james.transport.mailets.redirect.SpecialAddress;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableList;

public class MailAddressUtilsTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void fromShouldThrowWhenInternetAddressesIsNull() throws Exception {
        expectedException.expect(NullPointerException.class);
        MailAddressUtils.from(null);
    }

    @Test
    public void fromShouldReturnOneMailAddressWhenOneInternetAddresse() throws Exception {
        List<MailAddress> mailAddresses = MailAddressUtils.from(InternetAddress.parse("user@james.org"));

        MailAddress expectedMailAddress = new MailAddress("user", "james.org");
        assertThat(mailAddresses).containsOnly(expectedMailAddress);
    }

    @Test
    public void fromShouldReturnMailAddressesWhenInternetAddresses() throws Exception {
        List<MailAddress> mailAddresses = MailAddressUtils.from(InternetAddress.parse("user@james.org, user2@apache.org"));

        MailAddress expectedMailAddress = new MailAddress("user", "james.org");
        MailAddress expectedMailAddress2 = new MailAddress("user2", "apache.org");
        assertThat(mailAddresses).containsOnly(expectedMailAddress, expectedMailAddress2);
    }

    @Test
    public void toInternetAddressArrayShouldThrowWhenMailAddressesIsNull() {
        expectedException.expect(NullPointerException.class);
        MailAddressUtils.toInternetAddressArray(null);
    }

    @Test
    public void toInternetAddressArrayShouldReturnOneInternetAddressWhenOneMailAddress() throws Exception {
        InternetAddress[] internetAddresses = MailAddressUtils.toInternetAddressArray(ImmutableList.of(new MailAddress("user", "james.org")));

        assertThat(internetAddresses).containsOnly(new InternetAddress("user@james.org"));
    }

    @Test
    public void toInternetAddressArrayShouldReturnInternetAddressesWhenMailAddresses() throws Exception {
        InternetAddress[] internetAddresses = MailAddressUtils.toInternetAddressArray(ImmutableList.of(new MailAddress("user", "james.org"), new MailAddress("user2", "apache.org")));

        assertThat(internetAddresses).containsOnly(new InternetAddress("user@james.org"), new InternetAddress("user2@apache.org"));
    }

    @Test
    public void toInternetAddressesShouldThrowWhenMailAddressesIsNull() {
        expectedException.expect(NullPointerException.class);
        MailAddressUtils.toInternetAddresses(null);
    }

    @Test
    public void toInternetAddressesShouldReturnOneInternetAddressWhenOneMailAddress() throws Exception {
        List<InternetAddress> internetAddresses = MailAddressUtils.toInternetAddresses(ImmutableList.of(new MailAddress("user", "james.org")));

        assertThat(internetAddresses).containsOnly(new InternetAddress("user@james.org"));
    }

    @Test
    public void toInternetAddressesShouldReturnInternetAddressesWhenMailAddresses() throws Exception {
        List<InternetAddress> internetAddresses = MailAddressUtils.toInternetAddresses(ImmutableList.of(new MailAddress("user", "james.org"), new MailAddress("user2", "apache.org")));

        assertThat(internetAddresses).containsOnly(new InternetAddress("user@james.org"), new InternetAddress("user2@apache.org"));
    }

    @Test
    public void isUnalteredOrReversePathOrSenderShouldReturnTrueWhenMailAddressIsSpecialUnaltered() {
        boolean unalteredOrReversePathOrSender = MailAddressUtils.isUnalteredOrReversePathOrSender(SpecialAddress.UNALTERED);

        assertThat(unalteredOrReversePathOrSender).isTrue();
    }

    @Test
    public void isUnalteredOrReversePathOrSenderShouldReturnTrueWhenMailAddressIsSpecialReversePath() {
        boolean unalteredOrReversePathOrSender = MailAddressUtils.isUnalteredOrReversePathOrSender(SpecialAddress.REVERSE_PATH);

        assertThat(unalteredOrReversePathOrSender).isTrue();
    }

    @Test
    public void isUnalteredOrReversePathOrSenderShouldReturnTrueWhenMailAddressIsSpecialSender() {
        boolean unalteredOrReversePathOrSender = MailAddressUtils.isUnalteredOrReversePathOrSender(SpecialAddress.SENDER);

        assertThat(unalteredOrReversePathOrSender).isTrue();
    }

    @Test
    public void isUnalteredOrReversePathOrSenderShouldReturnFalseWhenMailAddressIsSpecialDelete() {
        boolean unalteredOrReversePathOrSender = MailAddressUtils.isUnalteredOrReversePathOrSender(SpecialAddress.DELETE);

        assertThat(unalteredOrReversePathOrSender).isFalse();
    }

    @Test
    public void isUnalteredOrReversePathOrSenderShouldReturnFalseWhenMailAddressIsSpecialFrom() {
        boolean unalteredOrReversePathOrSender = MailAddressUtils.isUnalteredOrReversePathOrSender(SpecialAddress.FROM);

        assertThat(unalteredOrReversePathOrSender).isFalse();
    }

    @Test
    public void isUnalteredOrReversePathOrSenderShouldReturnFalseWhenMailAddressIsSpecialNull() {
        boolean unalteredOrReversePathOrSender = MailAddressUtils.isUnalteredOrReversePathOrSender(SpecialAddress.NULL);

        assertThat(unalteredOrReversePathOrSender).isFalse();
    }

    @Test
    public void isUnalteredOrReversePathOrSenderShouldReturnFalseWhenMailAddressIsSpecialRecipients() {
        boolean unalteredOrReversePathOrSender = MailAddressUtils.isUnalteredOrReversePathOrSender(SpecialAddress.RECIPIENTS);

        assertThat(unalteredOrReversePathOrSender).isFalse();
    }

    @Test
    public void isUnalteredOrReversePathOrSenderShouldReturnFalseWhenMailAddressIsSpecialReplyTo() {
        boolean unalteredOrReversePathOrSender = MailAddressUtils.isUnalteredOrReversePathOrSender(SpecialAddress.REPLY_TO);

        assertThat(unalteredOrReversePathOrSender).isFalse();
    }

    @Test
    public void isUnalteredOrReversePathOrSenderShouldReturnFalseWhenMailAddressIsSpecialTo() {
        boolean unalteredOrReversePathOrSender = MailAddressUtils.isUnalteredOrReversePathOrSender(SpecialAddress.TO);

        assertThat(unalteredOrReversePathOrSender).isFalse();
    }

    @Test
    public void isUnalteredOrReversePathOrSenderShouldReturnFalseWhenMailAddressIsNotSpecial() throws Exception {
        boolean unalteredOrReversePathOrSender = MailAddressUtils.isUnalteredOrReversePathOrSender(new MailAddress("common", "james.org"));

        assertThat(unalteredOrReversePathOrSender).isFalse();
    }
}
