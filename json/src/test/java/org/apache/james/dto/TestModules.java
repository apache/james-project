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

package org.apache.james.dto;

import org.apache.james.json.DTO;
import org.apache.james.json.DTOConverter;
import org.apache.james.json.DTOModule;

public interface TestModules {
    TestNestedModule<?, ?> FIRST_NESTED = DTOModule
        .forDomainObject(FirstNestedType.class)
        .convertToDTO(FirstNestedDTO.class)
        .toDomainObjectConverter(FirstNestedDTO::toDomainObject)
        .toDTOConverter((domainObject, typeName) -> new FirstNestedDTO(
            domainObject.getFoo(),
            typeName))
            .typeName("first-nested")
        .withFactory(TestNestedModule::new);

    TestNestedModule<?, ?> SECOND_NESTED = DTOModule
        .forDomainObject(SecondNestedType.class)
        .convertToDTO(SecondNestedDTO.class)
        .toDomainObjectConverter(SecondNestedDTO::toDomainObject)
        .toDTOConverter((domainObject, typeName) -> new SecondNestedDTO(
            domainObject.getBar(),
            typeName))
        .typeName("second-nested")
        .withFactory(TestNestedModule::new);

    DTOConverter<NestedType, DTO> NESTED_CONVERTERS = DTOConverter.of(FIRST_NESTED, SECOND_NESTED);

    TestModule<FirstDomainObject, FirstDTO> FIRST_TYPE = DTOModule
        .forDomainObject(FirstDomainObject.class)
        .convertToDTO(FirstDTO.class)
        .toDomainObjectConverter(dto -> dto.toDomainObject(NESTED_CONVERTERS))
        .toDTOConverter((domainObject, typeName) -> new FirstDTO(
            typeName,
            domainObject.getId(),
            domainObject.getTime().toString(),
            domainObject.getPayload(),
            domainObject.getChild().flatMap(NESTED_CONVERTERS::toDTO)))
        .typeName("first")
        .withFactory(TestModule::new);

    TestModule<?, ?> SECOND_TYPE = DTOModule
        .forDomainObject(SecondDomainObject.class)
        .convertToDTO(SecondDTO.class)
        .toDomainObjectConverter(dto -> dto.toDomainObject(NESTED_CONVERTERS))
        .toDTOConverter((domainObject, typeName) -> new SecondDTO(
            typeName,
            domainObject.getId().toString(),
            domainObject.getPayload(),
            domainObject.getChild().flatMap(NESTED_CONVERTERS::toDTO)))
        .typeName("second")
        .withFactory(TestModule::new);

}
