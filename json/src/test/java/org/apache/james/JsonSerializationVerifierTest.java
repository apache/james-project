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

package org.apache.james;

import static org.apache.james.SerializationFixture.FIRST;
import static org.apache.james.SerializationFixture.FIRST_JSON;
import static org.apache.james.SerializationFixture.FIRST_JSON_BAD;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.dto.TestModules;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

class JsonSerializationVerifierTest {
    @Test
    void verifyShouldNotThrowWhenValid() {
        assertThatCode(() ->
            JsonSerializationVerifier.dtoModule(TestModules.FIRST_TYPE)
                .bean(FIRST)
                .json(FIRST_JSON)
                .verify())
            .doesNotThrowAnyException();
    }

    @Test
    void verifyShouldThrowOnUnexpectedJson() {
        assertThatThrownBy(() ->
            JsonSerializationVerifier.dtoModule(TestModules.FIRST_TYPE)
                .bean(FIRST)
                .json(FIRST_JSON_BAD)
                .verify())
            .isInstanceOf(AssertionFailedError.class)
            .hasMessageContaining("[Serialization test [org.apache.james.dto.FirstDomainObject@7650497c]]")
            .hasMessageContaining("JSON documents are different:")
            .hasMessageContaining("Different value found in node \"id\"");
    }
}
