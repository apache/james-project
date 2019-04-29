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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

public interface MailRepositoryPropertiesStoreContract {

    MailRepositoryProperties NON_BROWSABLE = MailRepositoryProperties.builder()
        .canNotBrowse()
        .build();
    MailRepositoryProperties BROWSABLE = MailRepositoryProperties.builder()
        .canBrowse()
        .build();

    MailRepositoryUrl URL_1 = MailRepositoryUrl.from("protocol://deletedMessages/user1");
    MailRepositoryUrl URL_2 = MailRepositoryUrl.from("protocol://deletedMessages/user2");
    MailRepositoryUrl URL_3 = MailRepositoryUrl.from("protocol://deletedMessages/user3");

    MailRepositoryPropertiesStore testee();

    @Test
    default void storeShouldStorePropertiesWithBelongingUrl() {
        Mono.from(testee().store(URL_1, NON_BROWSABLE))
            .block();

        assertThat(Mono.from(testee().retrieve(URL_1)).block())
            .isEqualTo(NON_BROWSABLE);
    }

    @Test
    default void storeShouldOverrideTheResultWhenCalledMultipleTimes() {
        Mono.from(testee().store(URL_1, NON_BROWSABLE)).block();
        Mono.from(testee().store(URL_1, BROWSABLE)).block();

        assertThat(Mono.from(testee().retrieve(URL_1)).block())
            .isEqualTo(BROWSABLE);
    }

    @Test
    default void storeShouldNotThrowWhenStoringDifferentUrl() {
        assertThatCode(() -> {
                Mono.from(testee().store(URL_1, NON_BROWSABLE)).block();
                Mono.from(testee().store(URL_2, BROWSABLE)).block();
            }).doesNotThrowAnyException();
    }

    @Test
    default void retrieveShouldReturnTheRightPropertiesByTheUrl() {
        Mono.from(testee().store(URL_1, NON_BROWSABLE)).block();
        Mono.from(testee().store(URL_2, BROWSABLE)).block();

        assertThat(Mono.from(testee().retrieve(URL_2)).block())
            .isEqualTo(BROWSABLE);
    }

    @Test
    default void retrieveShouldReturnEmptyWhenRetrievingByNonExistedUrl() {
        Mono.from(testee().store(URL_1, NON_BROWSABLE)).block();
        Mono.from(testee().store(URL_2, BROWSABLE)).block();

        assertThat(Mono.from(testee().retrieve(URL_3)).blockOptional())
            .isEmpty();
    }

    @Test
    default void doingMappingOnTheResultShouldNotThrowWhenEmptyResult() {
        assertThatCode(() ->
            Mono.from(testee().retrieve(URL_1))
                .map(MailRepositoryProperties::isBrowsable)
                .block())
        .doesNotThrowAnyException();
    }

    @Test
    default void doingMappingOnTheResultShouldNotThrowAfterANullMappingPipeLineWhenEmptyResult() {
        assertThatCode(() ->
            Mono.from(testee().retrieve(URL_1))
                .map(properties -> (MailRepositoryProperties) null)
                .map(MailRepositoryProperties::isBrowsable)
                .block())
        .doesNotThrowAnyException();
    }

    @Test
    default void doingMappingOnTheResultShouldThrowAfterANullMappingPipeLineWhenNotEmptyResult() {
        Mono.from(testee().store(URL_1, NON_BROWSABLE)).block();

        assertThatThrownBy(() ->
            Mono.from(testee().retrieve(URL_1))
                .map(properties -> (MailRepositoryProperties) null)
                .map(MailRepositoryProperties::isBrowsable)
                .block())
        .isInstanceOf(NullPointerException.class);
    }
}
