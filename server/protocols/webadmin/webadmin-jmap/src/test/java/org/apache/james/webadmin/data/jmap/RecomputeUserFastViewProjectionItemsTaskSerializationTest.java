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

package org.apache.james.webadmin.data.jmap;

import static org.apache.james.JsonSerializationVerifier.recursiveComparisonConfiguration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.core.Username;
import org.apache.james.json.JsonGenericSerializer;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecomputeUserFastViewProjectionItemsTaskSerializationTest {
    MessageFastViewProjectionCorrector corrector;

    @BeforeEach
    void setUp() {
        corrector = mock(MessageFastViewProjectionCorrector.class);
    }

    @Test
    void shouldMatchJsonSerializationContract() throws Exception {
        JsonSerializationVerifier.dtoModule(RecomputeUserFastViewProjectionItemsTask.module(corrector))
            .bean(new RecomputeUserFastViewProjectionItemsTask(corrector,
                RunningOptions.withMessageRatePerSecond(2),
                Username.of("bob")))
            .json(ClassLoaderUtils.getSystemResourceAsString("json/recomputeUser.task.json"))
            .verify();
    }

    @Test
    void shouldDeserializeLegacy() throws Exception {
        RecomputeUserFastViewProjectionItemsTask legacyTask = JsonGenericSerializer.forModules(RecomputeUserFastViewProjectionItemsTask.module(corrector))
            .withoutNestedType()
            .deserialize(ClassLoaderUtils.getSystemResourceAsString("json/recomputeUser.task.legacy.json"));

        RecomputeUserFastViewProjectionItemsTask expected = new RecomputeUserFastViewProjectionItemsTask(corrector,
            RunningOptions.DEFAULT,
            Username.of("bob"));

        assertThat(legacyTask)
            .usingRecursiveComparison(recursiveComparisonConfiguration)
            .isEqualTo(expected);
    }
}