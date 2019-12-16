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

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.james.dto.BaseType;
import org.apache.james.dto.FirstDomainObject;
import org.apache.james.dto.FirstNestedType;
import org.apache.james.dto.NestedType;
import org.apache.james.dto.SecondDomainObject;
import org.apache.james.dto.SecondNestedType;
import org.apache.james.dto.TestModules;
import org.apache.james.json.DTO;
import org.apache.james.json.JsonGenericSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JsonGenericSerializerTest {
    private static final Optional<NestedType> NO_CHILD = Optional.empty();
    private static final BaseType FIRST = new FirstDomainObject(Optional.of(1L), ZonedDateTime.parse("2016-04-03T02:01+07:00[Asia/Vientiane]"), "first payload", NO_CHILD);
    private static final BaseType SECOND = new SecondDomainObject(UUID.fromString("4a2c853f-7ffc-4ce3-9410-a47e85b3b741"), "second payload", NO_CHILD);
    private static final BaseType SECOND_WITH_NESTED = new SecondDomainObject(UUID.fromString("4a2c853f-7ffc-4ce3-9410-a47e85b3b741"), "second payload", Optional.of(new FirstNestedType(12)));
    private static final BaseType FIRST_WITH_NESTED = new FirstDomainObject(Optional.of(1L), ZonedDateTime.parse("2016-04-03T02:01+07:00[Asia/Vientiane]"), "payload", Optional.of(new SecondNestedType("bar")));

    private static final String MISSING_TYPE_JSON = "{\"id\":1,\"time\":\"2016-04-03T02:01+07:00[Asia/Vientiane]\",\"payload\":\"first payload\"}";
    private static final String DUPLICATE_TYPE_JSON = "{\"type\":\"first\", \"type\":\"second\", \"id\":1,\"time\":\"2016-04-03T02:01+07:00[Asia/Vientiane]\",\"payload\":\"first payload\"}";
    private static final String FIRST_JSON = "{\"type\":\"first\",\"id\":1,\"time\":\"2016-04-03T02:01+07:00[Asia/Vientiane]\",\"payload\":\"first payload\"}";
    private static final String FIRST_JSON_WITH_NESTED = "{\"type\":\"first\",\"id\":1,\"time\":\"2016-04-03T02:01+07:00[Asia/Vientiane]\",\"payload\":\"payload\", \"child\": {\"bar\": \"bar\", \"type\": \"second-nested\"}}";
    private static final String SECOND_JSON = "{\"type\":\"second\",\"id\":\"4a2c853f-7ffc-4ce3-9410-a47e85b3b741\",\"payload\":\"second payload\"}";
    private static final String SECOND_WITH_NESTED_JSON = "{\"type\":\"second\",\"id\":\"4a2c853f-7ffc-4ce3-9410-a47e85b3b741\",\"payload\":\"second payload\", \"child\": {\"foo\": 12, \"type\": \"first-nested\"}}";

    @Test
    void shouldDeserializeKnownType() throws Exception {
        assertThat(JsonGenericSerializer.forModules(TestModules.FIRST_TYPE).withoutNestedType()
            .deserialize(FIRST_JSON))
            .isEqualTo(FIRST);
    }

    @Test
    void shouldDeserializeNestedTypeWithSecond() throws Exception {
        assertThat(JsonGenericSerializer
            .forModules(TestModules.FIRST_TYPE, TestModules.SECOND_TYPE)
            .withNestedTypeModules(TestModules.FIRST_NESTED, TestModules.SECOND_NESTED)
            .deserialize(SECOND_WITH_NESTED_JSON))
            .isEqualTo(SECOND_WITH_NESTED);
    }

    @Test
    void shouldDeserializeNestedTypeWithFirst() throws Exception {
        assertThat(JsonGenericSerializer
            .forModules(TestModules.FIRST_TYPE, TestModules.SECOND_TYPE)
            .withNestedTypeModules(TestModules.FIRST_NESTED, TestModules.SECOND_NESTED)
            .deserialize(FIRST_JSON_WITH_NESTED))
            .isEqualTo(FIRST_WITH_NESTED);
    }

    @Test
    void shouldThrowWhenDeserializeEventWithMissingType() {
        assertThatThrownBy(() -> JsonGenericSerializer.forModules(TestModules.FIRST_TYPE).withoutNestedType()
            .deserialize(MISSING_TYPE_JSON))
            .isInstanceOf(JsonGenericSerializer.InvalidTypeException.class);
    }

    @Test
    void shouldThrowWhenDeserializeEventWithDuplicatedTypes() {
        assertThatThrownBy(() -> JsonGenericSerializer
            .forModules(TestModules.FIRST_TYPE, TestModules.SECOND_TYPE)
            .withoutNestedType()
            .deserialize(DUPLICATE_TYPE_JSON))
            .isInstanceOf(JsonGenericSerializer.InvalidTypeException.class);
    }

    @Test
    void shouldThrowWhenDeserializeUnknownType() {
        assertThatThrownBy(() -> JsonGenericSerializer.forModules().withoutNestedType()
            .deserialize(FIRST_JSON))
            .isInstanceOf(JsonGenericSerializer.UnknownTypeException.class);
    }

    @ParameterizedTest
    @MethodSource
    void serializeShouldHandleAllKnownTypes(BaseType domainObject, String serializedJson) throws Exception {

        assertThatJson(JsonGenericSerializer
            .forModules(TestModules.FIRST_TYPE, TestModules.SECOND_TYPE)
            .withoutNestedType()
            .serialize(domainObject))
            .isEqualTo(serializedJson);
    }

    private static Stream<Arguments> serializeShouldHandleAllKnownTypes() {
        return allKnownTypes();
    }

    @ParameterizedTest
    @MethodSource
    void deserializeShouldHandleAllKnownTypes(BaseType domainObject, String serializedJson) throws Exception {
        assertThatJson(JsonGenericSerializer
            .forModules(TestModules.FIRST_TYPE, TestModules.SECOND_TYPE)
            .withoutNestedType()
            .deserialize(serializedJson))
            .isEqualTo(domainObject);
    }

    private static Stream<Arguments> deserializeShouldHandleAllKnownTypes() {
        return allKnownTypes();
    }

    private static Stream<Arguments> allKnownTypes() {
        return Stream.of(
                Arguments.of(SECOND, SECOND_JSON),
                Arguments.of(FIRST, FIRST_JSON)
        );
    }

    @Test
    void shouldSerializeKnownType() throws Exception {
        assertThatJson(JsonGenericSerializer
            .<BaseType, DTO>forModules(TestModules.FIRST_TYPE)
            .withoutNestedType()
            .serialize(FIRST))
            .isEqualTo(FIRST_JSON);
    }

    @Test
    void shouldThrowWhenSerializeUnknownType() {
        assertThatThrownBy(() -> JsonGenericSerializer
            .forModules()
            .withoutNestedType()
            .serialize(FIRST))
            .isInstanceOf(JsonGenericSerializer.UnknownTypeException.class);
    }

    @Test
    void shouldThrowWhenRegisteringDuplicateTypeIds() {
        assertThatThrownBy(() -> JsonGenericSerializer
            .forModules(TestModules.FIRST_NESTED)
            .withNestedTypeModules(TestModules.FIRST_NESTED))
            .isInstanceOf(IllegalArgumentException.class);
    }

}
