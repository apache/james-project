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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.apache.james.DefaultUserEntityValidator;
import org.apache.james.RecipientRewriteTableUserEntityValidator;
import org.apache.james.UserEntityValidator;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.rrt.api.MappingConflictException;
import org.apache.james.rrt.api.RecipientRewriteTableConfiguration;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemoryRecipientRewriteTableEntityValidationTest {
    private static final Domain DOMAIN = Domain.of("example.com");

    private MemoryRecipientRewriteTable recipientRewriteTable;

    @BeforeEach
    void setUp() throws Exception {
        MemoryDomainList domainList = new MemoryDomainList(mock(DNSService.class));
        domainList.configure(DomainListConfiguration.DEFAULT);
        domainList.addDomain(DOMAIN);
        MemoryUsersRepository usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);
        usersRepository.addUser(Username.of("bob@example.com"), "secret");

        recipientRewriteTable = new MemoryRecipientRewriteTable();
        recipientRewriteTable.setDomainList(domainList);
        recipientRewriteTable.setUsersRepository(usersRepository);
        // Same order as Guice: the validator is injected, the configuration is read later
        recipientRewriteTable.setUserEntityValidator(UserEntityValidator.aggregate(
            new DefaultUserEntityValidator(usersRepository),
            new RecipientRewriteTableUserEntityValidator(recipientRewriteTable)));
        recipientRewriteTable.setConfiguration(RecipientRewriteTableConfiguration.DEFAULT_ENABLED);
    }

    @Test
    void addAliasMappingShouldRejectAnExistingUserAsSourceOnceConfigured() {
        assertThatThrownBy(() -> recipientRewriteTable.addAliasMapping(MappingSource.fromUser("bob", DOMAIN), "alice@example.com"))
            .isInstanceOf(MappingConflictException.class);
    }

    @Test
    void addGroupMappingShouldRejectAnExistingUserAsSourceOnceConfigured() {
        assertThatThrownBy(() -> recipientRewriteTable.addGroupMapping(MappingSource.fromUser("bob", DOMAIN), "alice@example.com"))
            .isInstanceOf(MappingConflictException.class);
    }
}
