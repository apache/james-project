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

package org.apache.james.json;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableSet;

public class DTOConverter<T, U extends DTO> {

    private final Map<String, DTOModule<? extends T, ? extends U>> typeToModule;
    private final Map<Class<? extends T>, DTOModule<? extends T, ? extends U>> domainClassToModule;

    @SafeVarargs
    public static <T, U extends DTO> DTOConverter<T, U> of(DTOModule<? extends T, ? extends U>... modules) {
        return new DTOConverter<>(ImmutableSet.copyOf(modules));
    }

    public DTOConverter(Set<? extends DTOModule<? extends T, ? extends U>> modules) {
        typeToModule = modules.stream()
            .collect(Guavate.toImmutableMap(
                DTOModule::getDomainObjectType,
                Function.identity()));

        domainClassToModule = modules.stream()
            .collect(Guavate.toImmutableMap(
                DTOModule::getDomainObjectClass,
                Function.identity()));
    }

    @SuppressWarnings("unchecked")
    public Optional<U> toDTO(T domainObject) {
        return Optional
            .ofNullable(domainClassToModule.get(domainObject.getClass()))
            .map(module -> (DTOModule<T, U>) module)
            .map(module -> module.toDTO(domainObject));
    }

    @SuppressWarnings("unchecked")
    public Optional<T> toDomainObject(U dto) {
        String type = dto.getType();
        return Optional
            .ofNullable(typeToModule.get(type))
            .map(module -> (DTOModule<T, U>) module)
            .map(DTOModule::getToDomainObjectConverter)
            .map(convert -> convert.convert(dto));
    }
}
