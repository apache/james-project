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

package org.apache.james.transport.matchers.dlp;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.regex.Pattern;

import org.apache.james.dlp.api.DLPConfigurationItem.Id;
import org.junit.jupiter.api.Test;

class DlpDomainRulesTest {

    private static final Pattern PATTERN_1 = Pattern.compile("1");
    private static final Pattern PATTERN_2 = Pattern.compile("2");

    @Test
    void builderShouldThrowWhenDuplicateIds() {
        assertThatThrownBy(() -> DlpDomainRules.builder()
                .senderRule(Id.of("1"), PATTERN_1)
                .senderRule(Id.of("1"), PATTERN_2)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void builderShouldNotThrowWhenDuplicateIdsOnDifferentTypes() {
        assertThatCode(() -> DlpDomainRules.builder()
                .senderRule(Id.of("1"), PATTERN_1)
                .contentRule(Id.of("1"), PATTERN_2)
                .build())
            .doesNotThrowAnyException();
    }


    @Test
    void builderShouldNotThrowWhenEmpty() {
        assertThatCode(() -> DlpDomainRules.builder().build())
            .doesNotThrowAnyException();
    }

}