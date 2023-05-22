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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.util.Set;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.task.Task;
import org.apache.james.user.api.DeleteUserDataTaskStep;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

class DeleteUsersDataOfDomainTaskTest {
    public static class FailureStepUponUser implements DeleteUserDataTaskStep {
        private final Set<Username> usersToBeFailed;

        public FailureStepUponUser(Set<Username> usersToBeFailed) {
            this.usersToBeFailed = usersToBeFailed;
        }

        @Override
        public StepName name() {
            return new StepName("FailureStepUponUser");
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public Publisher<Void> deleteUserData(Username username) {
            if (usersToBeFailed.contains(username)) {
                return Mono.error(new RuntimeException());
            }
            return Mono.empty();
        }
    }

    private static final Domain DOMAIN_1 = Domain.of("domain1.tld");
    private static final Domain DOMAIN_2 = Domain.of("domain2.tld");

    private DeleteUserDataService service;
    private MemoryUsersRepository usersRepository;

    @BeforeEach
    void setup() throws Exception {
        DNSService dnsService = mock(DNSService.class);
        when(dnsService.getHostName(any())).thenReturn("localhost");
        when(dnsService.getLocalHost()).thenReturn(InetAddress.getByName("localhost"));
        MemoryDomainList domainList = new MemoryDomainList(dnsService);
        domainList.configure(DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false)
            .build());
        domainList.addDomain(DOMAIN_1);
        domainList.addDomain(DOMAIN_2);

        usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);
    }

    @Test
    void shouldCountSuccessfulUsers() throws UsersRepositoryException {
        // GIVEN DOMAIN1 has 2 users
        usersRepository.addUser(Username.of("user1@domain1.tld"), "password");
        usersRepository.addUser(Username.of("user2@domain1.tld"), "password");

        // WHEN run task for DOMAIN1
        service = new DeleteUserDataService(Set.of());
        DeleteUsersDataOfDomainTask task = new DeleteUsersDataOfDomainTask(service, DOMAIN_1, usersRepository);
        Task.Result result = task.run();

        // THEN should count successful DOMAIN1 users
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(result).isEqualTo(Task.Result.COMPLETED);
            softly.assertThat(task.getContext().getSuccessfulUsersCount()).isEqualTo(2L);
            softly.assertThat(task.getContext().getFailedUsersCount()).isEqualTo(0L);
        });
    }

    @Test
    void shouldCountOnlySuccessfulUsersOfRequestedDomain() throws UsersRepositoryException {
        // GIVEN DOMAIN1 has 2 users and DOMAIN2 has 1 user
        usersRepository.addUser(Username.of("user1@domain1.tld"), "password");
        usersRepository.addUser(Username.of("user2@domain1.tld"), "password");
        usersRepository.addUser(Username.of("user3@domain2.tld"), "password");

        // WHEN run task for DOMAIN1
        service = new DeleteUserDataService(Set.of());
        DeleteUsersDataOfDomainTask task = new DeleteUsersDataOfDomainTask(service, DOMAIN_1, usersRepository);
        Task.Result result = task.run();

        // THEN should count only successful DOMAIN1 users
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(result).isEqualTo(Task.Result.COMPLETED);
            softly.assertThat(task.getContext().getSuccessfulUsersCount()).isEqualTo(2L);
            softly.assertThat(task.getContext().getFailedUsersCount()).isEqualTo(0L);
        });
    }

    @Test
    void shouldCountFailedUsers() throws UsersRepositoryException {
        // GIVEN DOMAIN1 has 2 users
        usersRepository.addUser(Username.of("user1@domain1.tld"), "password");
        usersRepository.addUser(Username.of("user2@domain1.tld"), "password");

        // WHEN run task for DOMAIN1
        Set<Username> usersTobeFailed = Set.of(Username.of("user1@domain1.tld"), Username.of("user2@domain1.tld"));
        service = new DeleteUserDataService(Set.of(new FailureStepUponUser(usersTobeFailed)));
        DeleteUsersDataOfDomainTask task = new DeleteUsersDataOfDomainTask(service, DOMAIN_1, usersRepository);
        Task.Result result = task.run();

        // THEN should count failed DOMAIN1 users
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(result).isEqualTo(Task.Result.PARTIAL);
            softly.assertThat(task.getContext().getSuccessfulUsersCount()).isEqualTo(0L);
            softly.assertThat(task.getContext().getFailedUsersCount()).isEqualTo(2L);
            softly.assertThat(task.getContext().getFailedUsers())
                .containsExactlyInAnyOrder(Username.of("user1@domain1.tld"), Username.of("user2@domain1.tld"));
        });
    }

    @Test
    void shouldCountOnlyFailedUsersOfRequestedDomain() throws UsersRepositoryException {
        // GIVEN DOMAIN1 has 2 users and DOMAIN2 has 1 user
        usersRepository.addUser(Username.of("user1@domain1.tld"), "password");
        usersRepository.addUser(Username.of("user2@domain1.tld"), "password");
        usersRepository.addUser(Username.of("user3@domain2.tld"), "password");

        // WHEN run task for DOMAIN1
        Set<Username> usersTobeFailed = Set.of(Username.of("user1@domain1.tld"), Username.of("user2@domain1.tld"), Username.of("user3@domain2.tld"));
        service = new DeleteUserDataService(Set.of(new FailureStepUponUser(usersTobeFailed)));
        DeleteUsersDataOfDomainTask task = new DeleteUsersDataOfDomainTask(service, DOMAIN_1, usersRepository);
        Task.Result result = task.run();

        // THEN should count only failed DOMAIN1 users
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(result).isEqualTo(Task.Result.PARTIAL);
            softly.assertThat(task.getContext().getSuccessfulUsersCount()).isEqualTo(0L);
            softly.assertThat(task.getContext().getFailedUsersCount()).isEqualTo(2L);
            softly.assertThat(task.getContext().getFailedUsers())
                .containsExactlyInAnyOrder(Username.of("user1@domain1.tld"), Username.of("user2@domain1.tld"));
        });
    }

    @Test
    void mixedSuccessfulAndFailedUsersCase() throws UsersRepositoryException {
        // GIVEN DOMAIN1 has 3 users
        usersRepository.addUser(Username.of("user1@domain1.tld"), "password");
        usersRepository.addUser(Username.of("user2@domain1.tld"), "password");
        usersRepository.addUser(Username.of("user3@domain1.tld"), "password");

        // WHEN run task for DOMAIN1
        Set<Username> usersTobeFailed = Set.of(Username.of("user1@domain1.tld"));
        service = new DeleteUserDataService(Set.of(new FailureStepUponUser(usersTobeFailed)));
        DeleteUsersDataOfDomainTask task = new DeleteUsersDataOfDomainTask(service, DOMAIN_1, usersRepository);
        Task.Result result = task.run();

        // THEN should count both successful and failed users
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(result).isEqualTo(Task.Result.PARTIAL);
            softly.assertThat(task.getContext().getSuccessfulUsersCount()).isEqualTo(2L);
            softly.assertThat(task.getContext().getFailedUsersCount()).isEqualTo(1L);
            softly.assertThat(task.getContext().getFailedUsers()).containsExactly(Username.of("user1@domain1.tld"));
        });
    }
}
