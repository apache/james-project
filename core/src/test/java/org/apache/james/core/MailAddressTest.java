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

package org.apache.james.core;

import static org.assertj.core.api.Assertions.assertThat;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class MailAddressTest {

    private static final String GOOD_LOCAL_PART = "\"quoted@local part\"";
    private static final String GOOD_QUOTED_LOCAL_PART = "\"quoted@local part\"@james.apache.org";
    private static final String GOOD_ADDRESS = "server-dev@james.apache.org";
    private static final Domain GOOD_DOMAIN = Domain.of("james.apache.org");

    private static final String[] GOOD_ADDRESSES = {
            GOOD_ADDRESS,
            GOOD_QUOTED_LOCAL_PART,
            "server-dev@james-apache.org",
            "server-dev@[127.0.0.1]",
            "server-dev@#123",
            "server-dev@#123.apache.org",
            "server.dev@james.apache.org",
            "\\.server-dev@james.apache.org",
            "server-dev\\.@james.apache.org",
    };

    private static final String[] BAD_ADDRESSES = {
            "",
            "server-dev",
            "server-dev@",
            "[]",
            "server-dev@[]",
            "server-dev@#",
            "quoted local-part@james.apache.org",
            "quoted@local-part@james.apache.org",
            "local-part.@james.apache.org",
            ".local-part@james.apache.org",
            "local-part@.james.apache.org",
            "local-part@james.apache.org.",
            "local-part@james.apache..org",
            "server-dev@-james.apache.org",
            "server-dev@james.apache.org-",
            "server-dev@#james.apache.org",
            "server-dev@#123james.apache.org",
            "server-dev@#-123.james.apache.org",
            "server-dev@james. apache.org",
            "server-dev@james\\.apache.org",
            "server-dev@[300.0.0.1]",
            "server-dev@[127.0.1]",
            "server-dev@[0127.0.0.1]",
            "server-dev@[127.0.1.1a]",
            "server-dev@[127\\.0.1.1]",
            "server-dev@[127.0.1.1.1]",
            "server-dev@[127.0.1.-1]"
    };

    /**
     * Test method for {@link MailAddress#hashCode()}.
     *
     * @throws AddressException
     */
    @Test
    public void testHashCode() throws AddressException {

        MailAddress a = new MailAddress(GOOD_ADDRESS);
        MailAddress b = new MailAddress(GOOD_ADDRESS);
        assertThat(a.hashCode() == b.hashCode()).describedAs(a.hashCode() + " != " + b.hashCode()).isTrue();
    }

    /**
     * Test method for {@link MailAddress#MailAddress(java.lang.String)}.
     *
     * @throws AddressException
     */
    @Test
    public void testMailAddressString() throws AddressException {

        MailAddress a = new MailAddress(GOOD_ADDRESS);
        assertThat(GOOD_ADDRESS.equals(a.toString())).isTrue();

        for (String goodAddress : GOOD_ADDRESSES) {
            try {
                a = new MailAddress(goodAddress);
            } catch (AddressException e) {
                Assert.fail(e.getMessage());
            }
        }

        for (String badAddress : BAD_ADDRESSES) {
            Assertions.assertThatThrownBy(() -> new MailAddress(badAddress))
                .isInstanceOf(AddressException.class);
        }
    }

    /**
     * Test method for {@link MailAddress#MailAddress(java.lang.String, java.lang.String)}.
     */
    @Test
    public void testMailAddressStringString() {

        try {
            new MailAddress("local-part", "domain");
        } catch (AddressException e) {
            assertThat(false).describedAs(e.getMessage()).isTrue();
        }
        try {
            MailAddress a = new MailAddress("local-part", "-domain");
            assertThat(true).describedAs(a.toString()).isFalse();
        } catch (AddressException e) {
            assertThat(true).isTrue();
        }
    }

    /**
     * Test method for {@link MailAddress#MailAddress(javax.mail.internet.InternetAddress)}.
     */
    @Test
    public void testMailAddressInternetAddress() {

        try {
            new MailAddress(new InternetAddress(GOOD_QUOTED_LOCAL_PART));
        } catch (AddressException e) {
            System.out.println("AddressException" + e.getMessage());
            assertThat(false).describedAs(e.getMessage()).isTrue();
        }
    }

    /**
     * Test method for {@link MailAddress#getDomain()}.
     */
    @Test
    public void testGetDomain() {

        try {
            MailAddress a = new MailAddress(new InternetAddress(GOOD_ADDRESS));
            assertThat(a.getDomain().equals(GOOD_DOMAIN)).describedAs(a.getDomain() + " != " + GOOD_DOMAIN).isTrue();
        } catch (AddressException e) {
            System.out.println("AddressException" + e.getMessage());
            assertThat(false).describedAs(e.getMessage()).isTrue();
        }
    }

    /**
     * Test method for {@link MailAddress#getLocalPart()}.
     */
    @Test
    public void testGetLocalPart() {

        try {
            MailAddress a = new MailAddress(new InternetAddress(GOOD_QUOTED_LOCAL_PART));
            assertThat(a.getLocalPart().equals(GOOD_LOCAL_PART)).describedAs(GOOD_LOCAL_PART + " != " + a.getLocalPart()).isTrue();
        } catch (AddressException e) {
            System.out.println("AddressException" + e.getMessage());
            assertThat(false).describedAs(e.getMessage()).isTrue();
        }
    }

    /**
     * Test method for {@link MailAddress#toString()}.
     */
    @Test
    public void testToString() {

        try {
            MailAddress a = new MailAddress(new InternetAddress(GOOD_ADDRESS));
            assertThat(a.toString().equals(GOOD_ADDRESS)).describedAs(a.toString() + " != " + GOOD_ADDRESS).isTrue();
        } catch (AddressException e) {
            System.out.println("AddressException" + e.getMessage());
            assertThat(false).describedAs(e.getMessage()).isTrue();
        }
    }

    /**
     * Test method for {@link MailAddress#toInternetAddress()}.
     */
    @Test
    public void testToInternetAddress() {

        try {
            InternetAddress b = new InternetAddress(GOOD_ADDRESS);
            MailAddress a = new MailAddress(b);
            assertThat(a.toInternetAddress().equals(b)).isTrue();
            assertThat(a.toString().equals(GOOD_ADDRESS)).describedAs(a.toString() + " != " + GOOD_ADDRESS).isTrue();
        } catch (AddressException e) {
            System.out.println("AddressException" + e.getMessage());
            assertThat(false).describedAs(e.getMessage()).isTrue();
        }
    }

    /**
     * Test method for {@link MailAddress#equals(java.lang.Object)}.
     *
     * @throws AddressException
     */
    @Test
    public void testEqualsObject() throws AddressException {

        MailAddress a = new MailAddress(GOOD_ADDRESS);
        MailAddress b = new MailAddress(GOOD_ADDRESS);

        assertThat(a.equals(b)).describedAs(a.toString() + " != " + b.toString()).isTrue();
        assertThat(a.equals(null)).describedAs(a.toString() + " != " + null).isFalse();
    }

    @Test
    public void equalsShouldReturnTrueWhenBothNullSender() {
        assertThat(MailAddress.nullSender())
            .isEqualTo(MailAddress.nullSender());
    }

    @Test
    public void getMailSenderShouldReturnNullSenderWhenNullSender() {
        assertThat(MailAddress.getMailSender(MailAddress.NULL_SENDER_AS_STRING))
            .isEqualTo(MailAddress.nullSender());
    }

    @Test
    public void getMailSenderShouldReturnParsedAddressWhenNotNullAddress() throws Exception {
        assertThat(MailAddress.getMailSender(GOOD_ADDRESS))
            .isEqualTo(new MailAddress(GOOD_ADDRESS));
    }

    @Test
    public void shouldMatchBeanContract() {
        EqualsVerifier.forClass(MailAddress.class)
            .verify();
    }
}
