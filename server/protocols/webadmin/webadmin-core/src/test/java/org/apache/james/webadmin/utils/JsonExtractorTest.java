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

package org.apache.james.webadmin.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;

class JsonExtractorTest {

    private JsonExtractor<Request> jsonExtractor;

    @BeforeEach
    void setUp() {
        jsonExtractor = new JsonExtractor<>(Request.class);
    }

    @Test
    void parseShouldThrowOnNullInput() {
        assertThatThrownBy(() -> jsonExtractor.parse(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void parseShouldThrowOnEmptyInput() {
        assertThatThrownBy(() -> jsonExtractor.parse("")).isInstanceOf(JsonExtractException.class);
    }

    @Test
    void parseShouldThrowOnBrokenJson() {
        assertThatThrownBy(() -> jsonExtractor.parse("{\"field1\":\"broken")).isInstanceOf(JsonExtractException.class);
    }

    @Test
    void parseShouldThrowOnEmptyJson() {
        assertThatThrownBy(() -> jsonExtractor.parse("{}")).isInstanceOf(JsonExtractException.class);
    }

    @Test
    void parseShouldThrowOnMissingMandatoryField() {
        assertThatThrownBy(() -> jsonExtractor.parse("{\"field1\":\"any\"}")).isInstanceOf(JsonExtractException.class);
    }

    @Test
    void parseShouldThrowOnValidationProblemIllegalArgumentException() {
        assertThatThrownBy(() -> jsonExtractor.parse("{\"field1\":\"\",\"field2\":\"any\"}")).isInstanceOf(JsonExtractException.class);
    }

    @Test
    void parseShouldThrowOnValidationProblemNPE() {
        assertThatThrownBy(() -> jsonExtractor.parse("{\"field1\":null,\"field2\":\"any\"}")).isInstanceOf(JsonExtractException.class);
    }

    @Test
    void parseShouldThrowOnExtraFiled() {
        assertThatThrownBy(() -> jsonExtractor.parse("{\"field1\":\"value\",\"field2\":\"any\",\"extra\":\"extra\"}")).isInstanceOf(JsonExtractException.class);
    }

    @Test
    void parseShouldInstantiateDestinationClass() throws Exception {
        String field1 = "value1";
        String field2 = "value2";
        Request request = jsonExtractor.parse("{\"field1\":\"" + field1 + "\",\"field2\":\"" + field2 + "\"}");

        assertThat(request.getField1()).isEqualTo(field1);
        assertThat(request.getField2()).isEqualTo(field2);
    }

    static class Request {
        private final String field1;
        private final String field2;

        @JsonCreator
        public Request(@JsonProperty("field1") String field1,
                       @JsonProperty("field2") String field2) {
            Preconditions.checkNotNull(field1);
            Preconditions.checkNotNull(field2);
            Preconditions.checkArgument(!field1.isEmpty());
            this.field1 = field1;
            this.field2 = field2;
        }

        public String getField1() {
            return field1;
        }

        public String getField2() {
            return field2;
        }
    }

}
