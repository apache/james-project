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

package org.apache.james.dlp.eventsourcing.commands;

import static org.apache.james.dlp.api.DLPFixture.RULE;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.core.Domain;
import org.apache.james.dlp.api.DLPRules;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import nl.jqno.equalsverifier.EqualsVerifier;

class StoreCommandTest {

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(StoreCommand.class)
            .verify();
    }

    @Test
    void constructorShouldThrowWhenNullDomain() {
        assertThatThrownBy(() -> new StoreCommand(null, new DLPRules(ImmutableList.of(RULE))))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorShouldThrowWhenNullRules() {
        assertThatThrownBy(() -> new StoreCommand(Domain.LOCALHOST, null))
            .isInstanceOf(NullPointerException.class);
    }

}