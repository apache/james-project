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
package org.apache.james.domainlist.lib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Fail.fail;

import org.apache.james.core.Domain;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface DomainListContract {
    Logger LOGGER = LoggerFactory.getLogger(DomainListContract.class);

    Domain DOMAIN_1 = Domain.of("domain1.tld");
    Domain DOMAIN_2 = Domain.of("domain2.tld");
    Domain DOMAIN_3 = Domain.of("domain3.tld");
    Domain DOMAIN_4 = Domain.of("domain4.tld");
    Domain DOMAIN_5 = Domain.of("domain5.tld");
    Domain DOMAIN_UPPER_5 = Domain.of("Domain5.tld");

    DomainList domainList();

    @Test
    default void createListDomains() throws DomainListException {
        domainList().addDomain(DOMAIN_3);
        domainList().addDomain(DOMAIN_4);
        domainList().addDomain(DOMAIN_5);
        assertThat(domainList().getDomains()).containsOnly(DOMAIN_3, DOMAIN_4, DOMAIN_5,
            Domain.LOCALHOST /*default domain*/);
    }

    @Test
    default void domainsShouldBeListedInLowerCase() throws DomainListException {
        domainList().addDomain(DOMAIN_UPPER_5);
        assertThat(domainList().getDomains()).containsOnly(DOMAIN_5,
            Domain.LOCALHOST /*default domain*/);
    }

    @Test
    default void containShouldReturnTrueWhenThereIsADomain() throws DomainListException {
        domainList().addDomain(DOMAIN_2);
        assertThat(domainList().containsDomain(DOMAIN_2)).isTrue();
    }

    @Test
    default void containShouldBeCaseSensitive() throws DomainListException {
        domainList().addDomain(DOMAIN_5);
        assertThat(domainList().containsDomain(DOMAIN_UPPER_5)).isTrue();
    }

    @Test
    default void listDomainsShouldReturnNullWhenThereIsNoDomains() throws DomainListException {
        assertThat(domainList().getDomains()).containsOnly(Domain.LOCALHOST /*default domain*/);
    }

    @Test
    default void testAddRemoveContainsSameDomain() throws DomainListException {
        domainList().addDomain(DOMAIN_1);
        domainList().removeDomain(DOMAIN_1);
        assertThat(domainList().getDomains()).containsOnly(Domain.LOCALHOST /*default domain*/);
    }

    @Test
    default void addShouldBeCaseSensitive() {
        try {
            domainList().addDomain(DOMAIN_5);
        } catch (Exception e) {
            fail(e.getMessage());
        }
        assertThatThrownBy(() -> domainList().addDomain(DOMAIN_UPPER_5))
            .isInstanceOf(DomainListException.class);
    }

    @Test
    default void deletingADomainShouldNotDeleteOtherDomains() throws DomainListException {
        domainList().addDomain(DOMAIN_1);
        try {
            domainList().removeDomain(DOMAIN_2);
        } catch (DomainListException e) {
            LOGGER.info("Ignored error", e);
        }
        assertThat(domainList().getDomains()).containsOnly(DOMAIN_1,
            Domain.LOCALHOST /*default domain*/);
    }

    @Test
    default void containShouldReturnFalseWhenThereIsNoDomain() throws DomainListException {
        assertThat(domainList().containsDomain(DOMAIN_1)).isFalse();
    }

    @Test
    default void containsShouldReturnFalseWhenDomainIsRemoved() throws DomainListException {
        domainList().addDomain(DOMAIN_1);
        domainList().removeDomain(DOMAIN_1);
        assertThat(domainList().containsDomain(DOMAIN_1)).isFalse();
    }

    @Test
    default void removeShouldRemoveDomainsUsingUpperCases() throws DomainListException {
        domainList().addDomain(DOMAIN_UPPER_5);
        domainList().removeDomain(DOMAIN_UPPER_5);
        assertThat(domainList().containsDomain(DOMAIN_UPPER_5)).isFalse();
    }

    @Test
    default void removeShouldRemoveDomainsUsingLowerCases() throws DomainListException {
        domainList().addDomain(DOMAIN_UPPER_5);
        domainList().removeDomain(DOMAIN_5);
        assertThat(domainList().containsDomain(DOMAIN_UPPER_5)).isFalse();
    }

    @Test
    default void addDomainShouldThrowIfWeAddTwoTimesTheSameDomain() {
        try {
            domainList().addDomain(DOMAIN_1);
        } catch (Exception e) {
            fail(e.getMessage());
        }
        assertThatThrownBy(() -> domainList().addDomain(DOMAIN_1))
            .isInstanceOf(DomainListException.class);
    }

    @Test
    default void removeDomainShouldThrowIfTheDomainIsAbsent() {
        assertThatThrownBy(() -> domainList().removeDomain(DOMAIN_1))
            .isInstanceOf(DomainListException.class);
    }
}
