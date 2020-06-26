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

package org.apache.james.rrt.lib;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class MappingSourceTest {
    private static final String DOMAIN_AS_STRING = "domain.tld";
    private static final Domain DOMAIN = Domain.of(DOMAIN_AS_STRING);
    private static final  String USER = "alice";
    private static final String MAIL_ADDRESS = USER + "@" + DOMAIN_AS_STRING;

    @Test
    void shouldRespectBeanContract() {
        EqualsVerifier.forClass(MappingSource.class)
            .verify();
    }

    @Test
    void asMailAddressStringShouldSerializeWilcard() {
        MappingSource mappingSource = MappingSource.wildCard();

        assertThat(mappingSource.asMailAddressString()).isEqualTo("*@*");
    }

    @Test
    void asMailAddressStringShouldSerializeDomain() {
        MappingSource mappingSource = MappingSource.fromDomain(DOMAIN);

        assertThat(mappingSource.asMailAddressString()).isEqualTo("*@" + DOMAIN_AS_STRING);
    }

    @Test
    void asMailAddressStringShouldSerializeUser() {
        MappingSource mappingSource = MappingSource.fromUser(USER, DOMAIN_AS_STRING);

        assertThat(mappingSource.asMailAddressString()).isEqualTo(MAIL_ADDRESS);
    }

    @Test
    void asMailAddressStringShouldSerializeUserWithoutDomain() {
        MappingSource mappingSource = MappingSource.fromUser(Username.of(USER));

        assertThat(mappingSource.asMailAddressString()).isEqualTo(USER + "@*");
    }

    @Test
    void asMailAddressStringShouldSerializeMailAddress() throws Exception {
        MappingSource mappingSource = MappingSource.fromMailAddress(new MailAddress(MAIL_ADDRESS));

        assertThat(mappingSource.asMailAddressString()).isEqualTo(MAIL_ADDRESS);
    }

    @Test
    void availableDomainShouldReturnUserDomainIfExist() throws Exception {
        MappingSource mappingSource = MappingSource.fromMailAddress(new MailAddress(MAIL_ADDRESS));

        assertThat(mappingSource.availableDomain())
            .contains(DOMAIN);
    }

    @Test
    void availableDomainShouldReturnDomainIfExist() {
        MappingSource mappingSource = MappingSource.fromDomain(DOMAIN);

        assertThat(mappingSource.availableDomain())
            .contains(DOMAIN);
    }

    @Test
    void availableDomainShouldReturnEmptyWhenNoDomainUserOrDomain() {
        MappingSource mappingSource = MappingSource.wildCard();

        assertThat(mappingSource.availableDomain())
            .isEmpty();
    }
}