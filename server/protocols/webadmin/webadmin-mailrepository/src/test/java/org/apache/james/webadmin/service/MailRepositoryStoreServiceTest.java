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
package org.apache.james.webadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.mailrepository.memory.MemoryMailRepository;
import org.apache.james.util.streams.Limit;
import org.apache.james.webadmin.dto.MailKey;
import org.apache.james.webadmin.dto.MailRepositoryResponse;
import org.apache.james.webadmin.routes.MailRepositoriesRoutes;
import org.apache.mailet.base.test.FakeMail;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class MailRepositoryStoreServiceTest {
    private static final String FIRST_REPOSITORY = "url://repository";
    private static final String SECOND_REPOSITORY = "url://repository2";
    private static final String NAME_1 = "name1";
    private static final String NAME_2 = "name2";

    private MailRepositoryStore mailRepositoryStore;
    private MailRepositoryStoreService testee;
    private MemoryMailRepository repository;

    @Before
    public void setUp() {
        mailRepositoryStore = mock(MailRepositoryStore.class);
        repository = new MemoryMailRepository();
        testee = new MailRepositoryStoreService(mailRepositoryStore);
    }

    @Test
    public void listMailRepositoriesShouldReturnEmptyWhenEmpty() {
        assertThat(testee.listMailRepositories()).isEmpty();
    }

    @Test
    public void listMailRepositoriesShouldReturnOneRepositoryWhenOne() {
        when(mailRepositoryStore.getUrls())
            .thenReturn(ImmutableList.of(FIRST_REPOSITORY));
        assertThat(testee.listMailRepositories())
            .extracting(MailRepositoryResponse::getRepository)
            .containsOnly(FIRST_REPOSITORY);
    }

    @Test
    public void listMailRepositoriesShouldReturnTwoRepositoriesWhentwo() {
        when(mailRepositoryStore.getUrls())
            .thenReturn(ImmutableList.of(FIRST_REPOSITORY, SECOND_REPOSITORY));
        assertThat(testee.listMailRepositories())
            .extracting(MailRepositoryResponse::getRepository)
            .containsOnly(FIRST_REPOSITORY, SECOND_REPOSITORY);
    }

    @Test
    public void listMailsShouldThrowWhenMailRepositoryStoreThrows() throws Exception {
        when(mailRepositoryStore.select(FIRST_REPOSITORY))
            .thenThrow(new MailRepositoryStore.MailRepositoryStoreException("message"));

        assertThatThrownBy(() -> testee.listMails(FIRST_REPOSITORY, MailRepositoriesRoutes.NO_OFFSET, Limit.unlimited()))
            .isInstanceOf(MailRepositoryStore.MailRepositoryStoreException.class);
    }

    @Test
    public void listMailsShouldReturnEmptyWhenMailRepositoryIsEmpty() throws Exception {
        when(mailRepositoryStore.select(FIRST_REPOSITORY)).thenReturn(repository);

        assertThat(testee.listMails(FIRST_REPOSITORY, MailRepositoriesRoutes.NO_OFFSET, Limit.unlimited()))
            .isEmpty();
    }

    @Test
    public void listMailsShouldReturnContainedMailKeys() throws Exception {
        when(mailRepositoryStore.select(FIRST_REPOSITORY)).thenReturn(repository);

        repository.store(FakeMail.builder()
            .name(NAME_1)
            .build());
        repository.store(FakeMail.builder()
            .name(NAME_2)
            .build());

        assertThat(testee.listMails(FIRST_REPOSITORY, MailRepositoriesRoutes.NO_OFFSET, Limit.unlimited()))
            .containsOnly(new MailKey(NAME_1), new MailKey(NAME_2));
    }

    @Test
    public void listMailsShouldApplyLimitAndOffset() throws Exception {
        when(mailRepositoryStore.select(FIRST_REPOSITORY)).thenReturn(repository);

        repository.store(FakeMail.builder()
            .name(NAME_1)
            .build());
        repository.store(FakeMail.builder()
            .name(NAME_2)
            .build());
        repository.store(FakeMail.builder()
            .name("name3")
            .build());

        int offset = 1;
        assertThat(testee.listMails(FIRST_REPOSITORY, offset, Limit.from(1)))
            .containsOnly(new MailKey(NAME_2));
    }
}
