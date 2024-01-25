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

package org.apache.james.fetchmail;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Username;
import org.apache.james.user.api.UsersRepository;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FetchMailTest {

    @Test
    void shouldAddAccountWithGoodDataFetchMail() throws Exception {
        try (MockedConstruction<ParsedConfiguration> mockParsedConfiguration = Mockito.mockConstruction(ParsedConfiguration.class)) {
            UsersRepository fieldLocalUsers = mock(UsersRepository.class);
            when(fieldLocalUsers.countUsers()).thenReturn(4);
            when(fieldLocalUsers.list()).thenReturn(List.of(Username.of("user23@domain.com")).iterator()).thenReturn(List.of(Username.of("user23@domain.com")).iterator());

            FetchMail fetchMail = new FetchMail();
            fetchMail.setUsersRepository(fieldLocalUsers);
            fetchMail.configure(configuration());

            assertThat(mockParsedConfiguration.constructed().size()).isOne();
            assertThat(fetchMail.getStaticAccounts()).hasSize(2).extracting(Account::getSequenceNumber, Account::getUser).containsExactlyInAnyOrder(tuple(0, "user0"), tuple(1, "user1"));
            assertThat(fetchMail.getDynamicAccounts().values()).hasSize(2).extracting(DynamicAccount::getSequenceNumber, DynamicAccount::getUser).containsExactlyInAnyOrder(tuple(2, "user23@domain.com"), tuple(3, "user23@domain.com"));
        }
    }

    private HierarchicalConfiguration<ImmutableNode> configuration() {
        HierarchicalConfiguration<ImmutableNode> configuration = new BaseHierarchicalConfiguration();
        ImmutableNode account0 = new ImmutableNode.Builder().name("account").value("account0").addAttribute("ignorercpt-header", false).addAttribute("user", "user0").create();
        ImmutableNode account1 = new ImmutableNode.Builder().name("account").value("account1").addAttribute("ignorercpt-header", false).addAttribute("user", "user1").create();

        ImmutableNode account2 = new ImmutableNode.Builder().name("alllocal").value("account2").addAttribute("ignorercpt-header", false).create();
        ImmutableNode account3 = new ImmutableNode.Builder().name("alllocal").value("account3").addAttribute("ignorercpt-header", false).create();

        configuration.addNodes("accounts", List.of(account0, account1, account2, account3));
        return configuration;
    }
}
