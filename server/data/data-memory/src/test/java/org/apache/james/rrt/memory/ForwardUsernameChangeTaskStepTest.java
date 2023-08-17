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
import static org.mockito.Mockito.mock;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.UserEntityValidator;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.rrt.ForwardUsernameChangeTaskStep;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.lib.MappingsImpl;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class ForwardUsernameChangeTaskStepTest {
    private static final Username BOB_OLD = Username.of("bob-old@domain.tld");
    private static final Username BOB_NEW = Username.of("bob-new@domain.tld");
    private MemoryRecipientRewriteTable rrt;
    private ForwardUsernameChangeTaskStep testee;

    @BeforeEach
    void setUp() throws Exception {
        DNSService dnsService = mock(DNSService.class);
        MemoryDomainList domainList = new MemoryDomainList(dnsService);
        domainList.configure(DomainListConfiguration.DEFAULT);
        domainList.addDomain(Domain.of("domain.tld"));
        rrt = new MemoryRecipientRewriteTable();
        rrt.setUsersRepository(MemoryUsersRepository.withVirtualHosting(domainList));
        rrt.setUserEntityValidator(UserEntityValidator.NOOP);
        rrt.setDomainList(domainList);
        rrt.configure(new BaseHierarchicalConfiguration());
        testee = new ForwardUsernameChangeTaskStep(rrt);
    }

    @Test
    void shouldCreateForwardFromOldToNewUser() {
        Mono.from(testee.changeUsername(BOB_OLD, BOB_NEW)).block();

        assertThat(rrt.getAllMappings())
            .hasSize(1)
            .containsEntry(MappingSource.fromUser(BOB_OLD),
                MappingsImpl.builder()
                    .add(Mapping.forward(BOB_NEW.asString()))
                    .build());
    }

    @Test
    void shouldMigratePreviousForwards() throws Exception {
        rrt.addForwardMapping(MappingSource.fromUser(BOB_OLD), "alice@domain.tld");

        Mono.from(testee.changeUsername(BOB_OLD, BOB_NEW)).block();

        assertThat(rrt.getAllMappings())
            .hasSize(2)
            .containsEntry(MappingSource.fromUser(BOB_OLD),
                MappingsImpl.builder()
                    .add(Mapping.forward(BOB_NEW.asString()))
                    .build())
            .containsEntry(MappingSource.fromUser(BOB_NEW),
                MappingsImpl.builder()
                    .add(Mapping.forward("alice@domain.tld"))
                    .build());
    }

    @Test
    void shouldNotAlterDestinationForwards() throws Exception {
        rrt.addForwardMapping(MappingSource.fromUser(BOB_NEW), "alice@domain.tld");

        Mono.from(testee.changeUsername(BOB_OLD, BOB_NEW)).block();

        assertThat(rrt.getAllMappings())
            .hasSize(2)
            .containsEntry(MappingSource.fromUser(BOB_OLD),
                MappingsImpl.builder()
                    .add(Mapping.forward(BOB_NEW.asString()))
                    .build())
            .containsEntry(MappingSource.fromUser(BOB_NEW),
                MappingsImpl.builder()
                    .add(Mapping.forward("alice@domain.tld"))
                    .build());
    }

    @Test
    void shouldPreserveKeepACopy() throws Exception {
        rrt.addForwardMapping(MappingSource.fromUser(BOB_OLD), "alice@domain.tld");
        rrt.addForwardMapping(MappingSource.fromUser(BOB_OLD), BOB_OLD.asString());

        Mono.from(testee.changeUsername(BOB_OLD, BOB_NEW)).block();

        assertThat(rrt.getAllMappings())
            .hasSize(2)
            .containsEntry(MappingSource.fromUser(BOB_OLD),
                MappingsImpl.builder()
                    .add(Mapping.forward(BOB_NEW.asString()))
                    .build())
            .containsEntry(MappingSource.fromUser(BOB_NEW),
                MappingsImpl.builder()
                    .add(Mapping.forward(BOB_NEW.asString()))
                    .add(Mapping.forward("alice@domain.tld"))
                    .build());
    }
}
