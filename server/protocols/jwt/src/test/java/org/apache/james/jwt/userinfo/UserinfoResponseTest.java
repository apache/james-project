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

package org.apache.james.jwt.userinfo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;

class UserinfoResponseTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static UserinfoResponse parse(String json) throws Exception {
        return new UserinfoResponse(MAPPER.readTree(json));
    }

    @Test
    void textualClaimShouldReturnAStringClaim() throws Exception {
        assertThat(parse("{\"domain\":\"mine.example\"}").textualClaim("domain")).contains("mine.example");
    }

    @Test
    void textualClaimShouldKeepAnEmptyString() throws Exception {
        assertThat(parse("{\"domain\":\"\"}").textualClaim("domain")).contains("");
    }

    @Test
    void textualClaimShouldBeEmptyWhenAbsent() throws Exception {
        assertThat(parse("{\"sub\":\"admin\"}").textualClaim("domain")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "42", "100.0", "true", "[\"mine.example\"]", "{\"d\":\"mine.example\"}"})
    void textualClaimShouldBeEmptyWhenNotAJsonString(String rawClaim) throws Exception {
        assertThat(parse("{\"domain\":" + rawClaim + "}").textualClaim("domain")).isEmpty();
    }

    @Test
    void claimByPropertyNameShouldStillReadAnyValueAsText() throws Exception {
        assertThat(parse("{\"domain\":null}").claimByPropertyName("domain")).contains("null");
    }

    @Test
    void jsonShouldExposeTheParsedAnswer() throws Exception {
        assertThat(parse("{\"sid\":1}").json().get("sid").isNumber()).isTrue();
    }
}
