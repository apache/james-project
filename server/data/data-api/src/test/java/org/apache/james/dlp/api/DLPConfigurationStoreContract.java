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

package org.apache.james.dlp.api;

import static org.apache.james.dlp.api.DLPFixture.RULE;
import static org.apache.james.dlp.api.DLPFixture.RULE_2;
import static org.apache.james.dlp.api.DLPFixture.RULE_UPDATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.core.Domain;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

public interface DLPConfigurationStoreContract {

    Domain OTHER_DOMAIN = Domain.of("any.com");

    @Test
    default void listShouldReturnEmptyWhenNone(DLPConfigurationStore dlpConfigurationStore) {
        assertThat(dlpConfigurationStore.list(Domain.LOCALHOST))
            .isEmpty();
    }

    @Test
    default void listShouldReturnExistingEntries(DLPConfigurationStore dlpConfigurationStore) {
        dlpConfigurationStore.store(Domain.LOCALHOST, RULE, RULE_2);

        assertThat(dlpConfigurationStore.list(Domain.LOCALHOST)).containsOnly(RULE, RULE_2);
    }

    @Test
    default void listShouldNotReturnEntriesOfOtherDomains(DLPConfigurationStore dlpConfigurationStore) {
        dlpConfigurationStore.store(Domain.LOCALHOST, RULE);
        dlpConfigurationStore.store(OTHER_DOMAIN, RULE_2);

        assertThat(dlpConfigurationStore.list(Domain.LOCALHOST)).containsOnly(RULE);
    }

    @Test
    default void clearShouldRemoveAllEnriesOfADomain(DLPConfigurationStore dlpConfigurationStore) {
        dlpConfigurationStore.store(Domain.LOCALHOST, RULE, RULE_2);

        dlpConfigurationStore.clear(Domain.LOCALHOST);

        assertThat(dlpConfigurationStore.list(Domain.LOCALHOST)).isEmpty();
    }

    @Test
    default void clearShouldNotFailWhenDomainDoesNotExist(DLPConfigurationStore dlpConfigurationStore) {
        assertThatCode(() -> dlpConfigurationStore.clear(Domain.LOCALHOST))
            .doesNotThrowAnyException();
    }

    @Test
    default void clearShouldOnlyRemoveEntriesOfADomain(DLPConfigurationStore dlpConfigurationStore) {
        dlpConfigurationStore.store(Domain.LOCALHOST, RULE);
        dlpConfigurationStore.store(OTHER_DOMAIN, RULE_2);

        dlpConfigurationStore.clear(Domain.LOCALHOST);

        assertThat(dlpConfigurationStore.list(OTHER_DOMAIN)).containsOnly(RULE_2);
    }

    @Test
    default void clearShouldOnlyRemovePreviouslyExistingEntries(DLPConfigurationStore dlpConfigurationStore) {
        dlpConfigurationStore.store(Domain.LOCALHOST, RULE, RULE_2);

        dlpConfigurationStore.clear(Domain.LOCALHOST);

        dlpConfigurationStore.store(Domain.LOCALHOST, RULE);

        assertThat(dlpConfigurationStore.list(Domain.LOCALHOST)).containsOnly(RULE);
    }

    @Test
    default void storeShouldOverwritePreviouslyStoredEntries(DLPConfigurationStore dlpConfigurationStore) {
        dlpConfigurationStore.store(Domain.LOCALHOST, RULE, RULE_2);
        dlpConfigurationStore.store(Domain.LOCALHOST, RULE);

        assertThat(dlpConfigurationStore.list(Domain.LOCALHOST)).containsOnly(RULE);
    }

    @Test
    default void storeShouldBeAbleToAddRules(DLPConfigurationStore dlpConfigurationStore) {
        dlpConfigurationStore.store(Domain.LOCALHOST, RULE);
        dlpConfigurationStore.store(Domain.LOCALHOST, RULE, RULE_2);

        assertThat(dlpConfigurationStore.list(Domain.LOCALHOST)).containsOnly(RULE, RULE_2);
    }

    @Test
    default void storeShouldRejectDuplicateIds(DLPConfigurationStore dlpConfigurationStore) {
        assertThatThrownBy(() -> dlpConfigurationStore.store(Domain.LOCALHOST, RULE, RULE))
            .isInstanceOf(DLPRules.DuplicateRulesIdsException.class);
    }

    @Test
    default void storeShouldUpdate(DLPConfigurationStore dlpConfigurationStore) {
        dlpConfigurationStore.store(Domain.LOCALHOST, RULE);
        dlpConfigurationStore.store(Domain.LOCALHOST, RULE_UPDATED);

        assertThat(dlpConfigurationStore.list(Domain.LOCALHOST)).containsOnly(RULE_UPDATED);
    }

    @Test
    default void storingSameRuleShouldBeIdempotent(DLPConfigurationStore dlpConfigurationStore) {
        dlpConfigurationStore.store(Domain.LOCALHOST, RULE);
        dlpConfigurationStore.store(Domain.LOCALHOST, RULE);

        assertThat(dlpConfigurationStore.list(Domain.LOCALHOST)).containsOnly(RULE);
    }

    @Test
    default void storeShouldClearRulesWhenEmpty(DLPConfigurationStore dlpConfigurationStore) {
        dlpConfigurationStore.store(Domain.LOCALHOST, RULE);
        dlpConfigurationStore.store(Domain.LOCALHOST, new DLPRules(ImmutableList.of()));

        assertThat(dlpConfigurationStore.list(Domain.LOCALHOST)).isEmpty();
    }
}
