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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.webadmin.dto.MailRepositoryResponse;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class MailRepositoryStoreServiceTest {
    private static final String FIRST_REPOSITORY = "url://repository";
    private static final String SECOND_REPOSITORY = "url://repository2";
    private MailRepositoryStore mailRepositoryStore;
    private MailRepositoryStoreService testee;

    @Before
    public void setUp() {
        mailRepositoryStore = mock(MailRepositoryStore.class);
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
}
