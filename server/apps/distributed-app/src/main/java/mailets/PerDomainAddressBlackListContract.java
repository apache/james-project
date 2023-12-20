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

package com.linagora.james.blacklist.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import javax.mail.internet.AddressException;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.junit.jupiter.api.Test;

public interface PerDomainAddressBlackListContract {
    Domain DOMAIN = Domain.of("domain.tld");

    PerDomainAddressBlackList testee();

    @Test
    default void listShouldReturnEmptyWhenNone() {
        assertThat(testee().list(DOMAIN))
            .isEmpty();
    }

    @Test
    default void listShouldReturnAddedAddresses() throws AddressException {
        MailAddress address = new MailAddress("spammer@any.tld");
        testee().add(DOMAIN, address);

        assertThat(testee().list(DOMAIN))
            .containsExactly(address);
    }

    @Test
    default void addShouldBeIdempotent() throws AddressException {
        MailAddress address = new MailAddress("spammer@any.tld");
        testee().add(DOMAIN, address);

        testee().add(DOMAIN, address);

        assertThat(testee().list(DOMAIN))
            .containsExactly(address);
    }

    @Test
    default void listShouldNotReturnRemovedEntries() throws AddressException {
        MailAddress address = new MailAddress("spammer@any.tld");
        testee().add(DOMAIN, address);

        testee().remove(DOMAIN, address);

        assertThat(testee().list(DOMAIN))
            .isEmpty();
    }

    @Test
    default void removeShouldNotThrowWhenAddressDoNotExist() throws AddressException {
        MailAddress address = new MailAddress("spammer@any.tld");

        assertThatCode(() -> testee().remove(DOMAIN, address))
            .doesNotThrowAnyException();
    }
}