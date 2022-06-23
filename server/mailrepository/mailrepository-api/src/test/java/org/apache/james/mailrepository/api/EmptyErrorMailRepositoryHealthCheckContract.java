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

package org.apache.james.mailrepository.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.Result;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.Test;

public interface EmptyErrorMailRepositoryHealthCheckContract {
    MailRepositoryPath ERROR_REPOSITORY_PATH = MailRepositoryPath.from("var/mail/error");

    MailRepositoryStore repositoryStore();

    void createRepository();

    default EmptyErrorMailRepositoryHealthCheck testee() {
        createRepository();
        return new EmptyErrorMailRepositoryHealthCheck(ERROR_REPOSITORY_PATH, repositoryStore());
    }

    @Test
    default void componentNameShouldReturnTheRightValue() {
        assertThat(testee().componentName().getName())
            .isEqualTo("EmptyErrorMailRepository");
    }

    @Test
    default void checkShouldReturnHealthyWhenRepositorySizeIsEmpty() {
        EmptyErrorMailRepositoryHealthCheck testee = testee();
        assertThat(testee.check().block())
            .isEqualTo(Result.healthy(new ComponentName("EmptyErrorMailRepository")));
    }

    @Test
    default void checkShouldReturnHealthyWhenRepositoryIsNotCreated() {
        EmptyErrorMailRepositoryHealthCheck testee = new EmptyErrorMailRepositoryHealthCheck(ERROR_REPOSITORY_PATH, repositoryStore());
        assertThat(testee.check().block())
            .isEqualTo(Result.healthy(new ComponentName("EmptyErrorMailRepository")));
    }

    @Test
    default void checkShouldReturnDegradedWhenRepositorySizeIsNotEmpty() throws Exception {
        EmptyErrorMailRepositoryHealthCheck testee = testee();
        repositoryStore().getByPath(ERROR_REPOSITORY_PATH)
            .findFirst().orElseThrow()
            .store(FakeMail.builder()
                .name("name1")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("Any body"))
                .build());

        assertThat(testee.check().block().isDegraded())
            .isTrue();
    }

    @Test
    default void checkShouldReturnHealthyWhenRepositorySizeReturnEmptyAgain() throws Exception {
        EmptyErrorMailRepositoryHealthCheck testee = testee();
        MailRepository mailRepository = repositoryStore().getByPath(ERROR_REPOSITORY_PATH).findFirst().orElseThrow();
        mailRepository.store(FakeMail.builder()
            .name("name1")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setText("Any body"))
            .build());

        assertThat(testee.check().block().isDegraded())
            .isTrue();

        mailRepository.removeAll();
        assertThat(testee.check().block().isHealthy())
            .isTrue();
    }
}
