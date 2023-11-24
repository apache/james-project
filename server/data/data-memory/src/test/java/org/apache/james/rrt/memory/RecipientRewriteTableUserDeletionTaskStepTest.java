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

package org.apache.james.rrt.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.UserEntityValidator;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.rrt.RecipientRewriteTableUserDeletionTaskStep;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class RecipientRewriteTableUserDeletionTaskStepTest {
    private static final Username BOB = Username.of("bob@domain.tld");
    private MemoryRecipientRewriteTable rrt;
    private RecipientRewriteTableUserDeletionTaskStep testee;

    @BeforeEach
    void setUp() throws Exception {
        MemoryDomainList domainList = new MemoryDomainList();
        domainList.configure(DomainListConfiguration.DEFAULT);
        domainList.addDomain(Domain.of("domain.tld"));

        rrt = new MemoryRecipientRewriteTable();
        rrt.setUsersRepository(MemoryUsersRepository.withVirtualHosting(domainList));
        rrt.setUserEntityValidator(UserEntityValidator.NOOP);
        rrt.setDomainList(domainList);
        rrt.configure(new BaseHierarchicalConfiguration());

        testee = new RecipientRewriteTableUserDeletionTaskStep(rrt);
    }

    @Test
    void shouldNotFailWhenNoMapping() {
        assertThatCode(() -> Mono.from(testee.deleteUserData(BOB)).block())
            .doesNotThrowAnyException();
    }

    @Test
    void shouldDeleteForwardMappings() throws Exception {
        rrt.addForwardMapping(MappingSource.fromUser(BOB), "alice@domain.tld");

        Mono.from(testee.deleteUserData(BOB)).block();

        assertThat(rrt.getAllMappings())
            .isEmpty();
    }

    @Test
    void shouldDeleteForwardMappingsWhenDestination() throws Exception {
        rrt.addForwardMapping(MappingSource.fromUser("alice", "domain.tld"), BOB.asString());

        Mono.from(testee.deleteUserData(BOB)).block();

        assertThat(rrt.getAllMappings())
            .isEmpty();
    }

    @Test
    void shouldDeleteAliasMapping() throws Exception {
        rrt.addAliasMapping(MappingSource.fromUser(BOB), "alice@domain.tld");

        Mono.from(testee.deleteUserData(BOB)).block();

        assertThat(rrt.getAllMappings())
            .isEmpty();
    }

    @Test
    void shouldDeleteAddressMapping() throws Exception {
        rrt.addAddressMapping(MappingSource.fromUser(BOB), "alice@domain.tld");

        Mono.from(testee.deleteUserData(BOB)).block();

        assertThat(rrt.getAllMappings())
            .isEmpty();
    }

    @Test
    void shouldDeleteRegexMapping() throws Exception {
        rrt.addRegexMapping(MappingSource.fromUser(BOB), "alice@domain.tld");

        Mono.from(testee.deleteUserData(BOB)).block();

        assertThat(rrt.getAllMappings())
            .isEmpty();
    }

    @Test
    void shouldDeleteErrorMapping() throws Exception {
        rrt.addErrorMapping(MappingSource.fromUser(BOB), "alice@domain.tld");

        Mono.from(testee.deleteUserData(BOB)).block();

        assertThat(rrt.getAllMappings())
            .isEmpty();
    }

    @Test
    void shouldDeleteGroupMapping() throws Exception {
        rrt.addGroupMapping(MappingSource.fromUser("alice", "domain.tld"), BOB.asString());

        Mono.from(testee.deleteUserData(BOB)).block();

        assertThat(rrt.getAllMappings())
            .isEmpty();
    }
}
