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
import static org.apache.james.SerializationFixture.DUPLICATE_TYPE_JSON;
import static org.apache.james.SerializationFixture.FIRST;
import static org.apache.james.SerializationFixture.FIRST_JSON;
import static org.apache.james.SerializationFixture.FIRST_JSON_WITH_NESTED;
import static org.apache.james.SerializationFixture.FIRST_WITH_NESTED;
import static org.apache.james.SerializationFixture.MISSING_TYPE_JSON;
import static org.apache.james.SerializationFixture.SECOND;
import static org.apache.james.SerializationFixture.SECOND_JSON;
import static org.apache.james.SerializationFixture.SECOND_WITH_NESTED;
import static org.apache.james.SerializationFixture.SECOND_WITH_NESTED_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;

import org.apache.james.dto.BaseType;
import org.apache.james.dto.TestModules;
import org.apache.james.json.DTO;
import org.apache.james.json.JsonGenericSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JsonGenericSerializerTest {

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
            .withMultipleNestedTypeModules(TestModules.FIRST_NESTED, TestModules.SECOND_NESTED)
            .deserialize(SECOND_WITH_NESTED_JSON))
            .isEqualTo(SECOND_WITH_NESTED);
    }

    @Test
    void shouldDeserializeNestedTypeWithFirst() throws Exception {
        assertThat(JsonGenericSerializer
            .forModules(TestModules.FIRST_TYPE, TestModules.SECOND_TYPE)
            .withMultipleNestedTypeModules(TestModules.FIRST_NESTED, TestModules.SECOND_NESTED)
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
        assertThat(JsonGenericSerializer
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
            .withMultipleNestedTypeModules(TestModules.FIRST_NESTED))
            .isInstanceOf(IllegalArgumentException.class);
    }

}
