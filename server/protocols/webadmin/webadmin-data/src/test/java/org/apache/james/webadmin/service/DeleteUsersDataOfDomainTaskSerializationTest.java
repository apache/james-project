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

import static org.mockito.Mockito.mock;

import java.time.Instant;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.user.api.DeleteUserDataTaskStep;
import org.apache.james.user.api.UsersRepository;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;

class DeleteUsersDataOfDomainTaskSerializationTest {
    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");
    private static final Domain DOMAIN = Domain.of("domain");
    private static final long SUCCESSFUL_USERS_COUNT = 99L;
    private static final long FAILED_USERS_COUNT = 1L;
    private static final DeleteUserDataTaskStep.StepName STEP_A = new DeleteUserDataTaskStep.StepName("A");
    private static final DeleteUserDataTaskStep.StepName STEP_B = new DeleteUserDataTaskStep.StepName("B");
    private static final DeleteUserDataTaskStep.StepName STEP_C = new DeleteUserDataTaskStep.StepName("C");
    private static final DeleteUserDataTaskStep.StepName STEP_D = new DeleteUserDataTaskStep.StepName("D");
    private static final DeleteUserDataTaskStep A = asStep(STEP_A);
    private static final DeleteUserDataTaskStep B = asStep(STEP_B);
    private static final DeleteUserDataTaskStep C = asStep(STEP_C);
    private static final DeleteUserDataTaskStep D = asStep(STEP_D);

    private static DeleteUserDataTaskStep asStep(DeleteUserDataTaskStep.StepName name) {
        return new DeleteUserDataTaskStep() {
            @Override
            public StepName name() {
                return name;
            }

            @Override
            public int priority() {
                return 0;
            }

            @Override
            public Publisher<Void> deleteUserData(Username username) {
                return Mono.empty();
            }
        };
    }

    private static final String SERIALIZED_TASK = "{\"type\":\"DeleteUsersDataOfDomainTask\",\"domain\":\"domain\"}";
    private static final String SERIALIZED_ADDITIONAL_INFORMATION = "{\"type\":\"DeleteUsersDataOfDomainTask\",\"domain\":\"domain\",\"successfulUsersCount\":99,\"failedUsersCount\":1,\"timestamp\":\"2018-11-13T12:00:55Z\"}";

    private static final DeleteUserDataService SERVICE = new DeleteUserDataService(ImmutableSet.of(A, B, C, D));

    @Test
    void taskShouldBeSerializable() throws Exception {
        UsersRepository usersRepository = mock(UsersRepository.class);
        JsonSerializationVerifier.dtoModule(DeleteUsersDataOfDomainTaskDTO.module(SERVICE, usersRepository))
            .bean(new DeleteUsersDataOfDomainTask(SERVICE, DOMAIN, usersRepository))
            .json(SERIALIZED_TASK)
            .verify();
    }

    @Test
    void additionalInformationShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(DeleteUsersDataOfDomainTaskAdditionalInformationDTO.module())
            .bean(new DeleteUsersDataOfDomainTask.AdditionalInformation(
                TIMESTAMP, DOMAIN, SUCCESSFUL_USERS_COUNT, FAILED_USERS_COUNT))
            .json(SERIALIZED_ADDITIONAL_INFORMATION)
            .verify();
    }
}