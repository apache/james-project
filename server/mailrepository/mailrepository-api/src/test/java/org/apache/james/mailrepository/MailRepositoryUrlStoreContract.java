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

package org.apache.james.mailrepository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.mailrepository.api.MailRepositoryUrlStore;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.Test;

public interface MailRepositoryUrlStoreContract {
    MailRepositoryUrl URL_1 = MailRepositoryUrl.from("proto://var/mail/toto");
    MailRepositoryUrl URL_2 = MailRepositoryUrl.from("proto://var/mail/tata");

    @Test
    default void listDistinctShouldBeEmptyByDefault(MailRepositoryUrlStore store) {
        assertThat(store.listDistinct()).isEmpty();
    }

    @Test
    default void listDistinctShouldReturnAddedUrl(MailRepositoryUrlStore store) {
        store.add(URL_1);

        assertThat(store.listDistinct()).containsOnly(URL_1);
    }

    @Test
    default void listDistinctShouldNotReturnDuplicates(MailRepositoryUrlStore store) {
        store.add(URL_1);
        store.add(URL_1);

        assertThat(store.listDistinct()).containsOnly(URL_1);
    }

    @Test
    default void listDistinctShouldReturnAddedUrls(MailRepositoryUrlStore store) {
        store.add(URL_1);
        store.add(URL_2);

        assertThat(store.listDistinct()).containsOnly(URL_1, URL_2);
    }

    @Test
    default void containsShouldReturnFalseWhenNotExisting(MailRepositoryUrlStore store) {
        assertThat(store.contains(URL_1)).isFalse();
    }

    @Test
    default void containsShouldReturnTrueWhenExisting(MailRepositoryUrlStore store) {
        store.add(URL_1);

        assertThat(store.contains(URL_1)).isTrue();
    }

    @Test
    default void addShouldWorkInConcurrentEnvironment(MailRepositoryUrlStore store) throws Exception {
        int operationCount = 10;
        int threadCount = 10;

        ConcurrentTestRunner.builder()
            .operation((a, b) -> store.add(MailRepositoryUrl.from("proto://" + a + "/" + b)))
            .threadCount(threadCount)
            .operationCount(operationCount)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        assertThat(store.listDistinct()).hasSize(threadCount * operationCount);
    }

    @Test
    default void addShouldNotAddDuplicatesInConcurrentEnvironment(MailRepositoryUrlStore store) throws Exception {
        int operationCount = 10;

        ConcurrentTestRunner.builder()
            .operation((a, b) -> store.add(MailRepositoryUrl.from("proto://" + b)))
            .threadCount(10)
            .operationCount(operationCount)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        assertThat(store.listDistinct()).hasSize(operationCount);
    }

}