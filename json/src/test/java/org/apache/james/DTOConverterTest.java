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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.james.dto.BaseType;
import org.apache.james.dto.FirstDTO;
import org.apache.james.dto.FirstDomainObject;
import org.apache.james.dto.NestedType;
import org.apache.james.dto.SecondDTO;
import org.apache.james.dto.SecondDomainObject;
import org.apache.james.dto.TestModules;
import org.apache.james.json.DTO;
import org.apache.james.json.DTOConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DTOConverterTest {
    private static final Optional<NestedType> NO_CHILD = Optional.empty();
    private static final Optional<DTO> NO_CHILD_DTO = Optional.empty();
    private static final BaseType FIRST = new FirstDomainObject(Optional.of(1L), ZonedDateTime.parse("2016-04-03T02:01+07:00[Asia/Vientiane]"), "first payload", NO_CHILD);
    private static final DTO FIRST_DTO = new FirstDTO("first", Optional.of(1L), "2016-04-03T02:01+07:00[Asia/Vientiane]", "first payload", NO_CHILD_DTO);
    private static final BaseType SECOND = new SecondDomainObject(UUID.fromString("4a2c853f-7ffc-4ce3-9410-a47e85b3b741"), "second payload", NO_CHILD);
    private static final DTO SECOND_DTO = new SecondDTO("second", "4a2c853f-7ffc-4ce3-9410-a47e85b3b741", "second payload", NO_CHILD_DTO);

    @SuppressWarnings("unchecked")
    @Test
    void shouldConvertFromKnownDTO() throws Exception {
        assertThat(DTOConverter
            .<BaseType, DTO>of(TestModules.FIRST_TYPE)
            .toDomainObject(FIRST_DTO))
            .contains(FIRST);
    }

    @Test
    void shouldReturnEmptyWhenConvertingFromUnknownDTO() {
        assertThat(DTOConverter.of()
            .toDomainObject(FIRST_DTO))
            .isEmpty();
    }

    @ParameterizedTest
    @MethodSource
    void convertFromDomainObjectShouldHandleAllKnownTypes(BaseType domainObject, DTO dto) throws Exception {
        @SuppressWarnings("unchecked")
        DTOConverter<BaseType, DTO> serializer = DTOConverter.of(
                TestModules.FIRST_TYPE,
                TestModules.SECOND_TYPE);

        assertThat(serializer.toDTO(domainObject))
            .hasValueSatisfying(result -> assertThat(result).isInstanceOf(dto.getClass()).isEqualToComparingFieldByField(dto));
    }

    private static Stream<Arguments> convertFromDomainObjectShouldHandleAllKnownTypes() {
        return allKnownTypes();
    }

    @ParameterizedTest
    @MethodSource
    void convertFromDTOShouldHandleAllKnownTypes(BaseType domainObject, DTO dto) throws Exception {
        @SuppressWarnings("unchecked")
        DTOConverter<BaseType, DTO> serializer = DTOConverter.of(
                TestModules.FIRST_TYPE,
                TestModules.SECOND_TYPE);

        assertThat(serializer.toDomainObject(dto))
            .hasValueSatisfying(result -> assertThat(result).isInstanceOf(domainObject.getClass()).isEqualToComparingFieldByField(domainObject));
    }

    private static Stream<Arguments> convertFromDTOShouldHandleAllKnownTypes() {
        return allKnownTypes();
    }

    private static Stream<Arguments> allKnownTypes() {
        return Stream.of(
                Arguments.of(SECOND, SECOND_DTO),
                Arguments.of(FIRST, FIRST_DTO)
        );
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldConvertFromKnownDomainObject() throws Exception {
        assertThat(DTOConverter.<BaseType, DTO>of(TestModules.FIRST_TYPE)
            .toDTO(FIRST))
            .hasValueSatisfying(result -> assertThat(result).isInstanceOf(FirstDTO.class).isEqualToComparingFieldByField(FIRST_DTO));
    }

    @Test
    void shouldReturnEmptyWhenConvertUnknownDomainObject() {
        assertThat(DTOConverter.of().toDTO(FIRST))
            .isEmpty();
    }
}
