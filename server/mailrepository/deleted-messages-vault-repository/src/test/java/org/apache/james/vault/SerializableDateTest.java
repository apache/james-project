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

package org.apache.james.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;

import org.apache.mailet.AttributeValue;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

class SerializableDateTest {
    private static final ZonedDateTime DATE = ZonedDateTime.parse("2014-10-30T14:12:00Z");

    @Test
    void serializeBackAndForthShouldPreserveDate() {
        JsonNode serializableDate = AttributeValue.of(new SerializableDate(DATE)).toJson();

        ZonedDateTime deserializedDate = AttributeValue.fromJson(serializableDate)
            .valueAs(SerializableDate.class)
            .get()
            .getValue();

        assertThat(deserializedDate).isEqualTo(DATE);
    }

    @Test
    void constructorShouldThrowOnNull() {
        assertThatThrownBy(() -> new SerializableDate(null))
            .isInstanceOf(NullPointerException.class);
    }
}