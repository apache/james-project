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

package org.apache.james.jmap.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.james.mime4j.dom.address.AddressList;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.dom.address.MailboxList;
import org.junit.Test;

public class EmailerTest {

    @Test(expected = IllegalStateException.class)
    public void buildShouldThrowWhenNameIsNull() {
        Emailer.builder().build();
    }

    @Test(expected = IllegalStateException.class)
    public void buildShouldThrowWhenEmailIsNull() {
        Emailer.builder().build();
    }

    @Test(expected = IllegalStateException.class)
    public void buildShouldThrowWhenNameIsEmpty() {
        Emailer.builder().name("").email("email@domain").build();
    }

    @Test(expected = IllegalStateException.class)
    public void buildShouldThrowWhenEmailIsEmpty() {
        Emailer.builder().name("name").email("").build();
    }

    @Test(expected = IllegalStateException.class)
    public void buildShouldThrowWhenEmailWithoutArobase() {
        Emailer.builder().name("name").email("email.without.arobase").build();
    }

    @Test
    public void buildShouldWork() {
        Emailer expected = new Emailer(Optional.of("name"), Optional.of("user@domain"));
        Emailer emailer = Emailer.builder()
            .name("name")
            .email("user@domain")
            .build();
        assertThat(emailer).isEqualToComparingFieldByField(expected);
    }

    @Test
    public void buildInvalidAllowedShouldConsiderNullValuesAsInvalid() {
        Emailer expected = new Emailer(Optional.empty(), Optional.empty());

        Emailer actual = Emailer.builder()
            .allowInvalid()
            .build();

        assertThat(actual).isEqualToComparingFieldByField(expected);
    }

    @Test
    public void buildInvalidAllowedShouldConsiderEmptyValuesAsInvalid() {
        Emailer expected = new Emailer(Optional.empty(), Optional.empty());

        Emailer actual = Emailer.builder()
            .name("")
            .email("")
            .allowInvalid()
            .build();

        assertThat(actual).isEqualToComparingFieldByField(expected);
    }

    @Test
    public void buildInvalidAllowedShouldDeclareInvalidAddressesAsInvalid() {
        Emailer expected = new Emailer(Optional.empty(), Optional.of("invalidAddress"));

        Emailer actual = Emailer.builder()
            .email("invalidAddress")
            .allowInvalid()
            .build();

        assertThat(actual).isEqualToComparingFieldByField(expected);
    }

    @Test
    public void buildInvalidAllowedShouldWork() {
        String name = "bob";
        String address = "me@apache.org";
        Emailer expected = new Emailer(Optional.of(name), Optional.of(address));

        Emailer actual = Emailer.builder()
            .name(name)
            .email(address)
            .allowInvalid()
            .build();

        assertThat(actual).isEqualToComparingFieldByField(expected);
    }

    @Test
    public void fromAddressListShouldReturnEmptyWhenNullAddress() {
        assertThat(Emailer.fromAddressList(null))
            .isEmpty();
    }

    @Test
    public void fromAddressListShouldReturnListOfEmailersContainingAddresses() {
        assertThat(Emailer.fromAddressList(new AddressList(
                new Mailbox("user1", "james.org"),
                new Mailbox("user2", "james.org"))))
            .containsExactly(
                Emailer.builder()
                    .name("user1@james.org")
                    .email("user1@james.org")
                    .build(),
                Emailer.builder()
                    .name("user2@james.org")
                    .email("user2@james.org")
                    .build());
    }

    @Test
    public void fromAddressListShouldReturnListOfEmailersContainingAddressesWithNames() {
        assertThat(Emailer.fromAddressList(new AddressList(
                new Mailbox("myInbox", "user1", "james.org"),
                new Mailbox("hisInbox", "user2", "james.org"))))
            .containsExactly(
                Emailer.builder()
                    .name("myInbox")
                    .email("user1@james.org")
                    .build(),
                Emailer.builder()
                    .name("hisInbox")
                    .email("user2@james.org")
                    .build());
    }

    @Test
    public void firstFromMailboxListShouldReturnEmptyWhenNullMailboxList() {
        assertThat(Emailer.firstFromMailboxList(null))
            .isEmpty();
    }

    @Test
    public void firstFromMailboxListShouldReturnTheFirstAddressInList() {
        assertThat(Emailer.firstFromMailboxList(new MailboxList(
                new Mailbox("user1Inbox", "user1", "james.org"),
                new Mailbox("user2Inbox", "user2", "james.org"))))
            .contains(Emailer.builder()
                .name("user1Inbox")
                .email("user1@james.org")
                .build());
    }
}
