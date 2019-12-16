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

import java.io.IOException;

import org.apache.james.json.DTO;
import org.apache.james.json.DTOModule;
import org.apache.james.json.JsonGenericSerializer;

public class JsonSerializationVerifier<T, U extends DTO> {
    @FunctionalInterface
    public interface RequireBean<T, U extends DTO> {
        RequireJson<T, U> bean(T bean);
    }

    @FunctionalInterface
    public interface RequireJson<T, U extends DTO> {
        JsonSerializationVerifier<T, U> json(String json);
    }

    public static <T, U extends DTO> RequireBean<T, U> dtoModule(DTOModule<T, U> dtoModule) {
        return bean -> json -> new JsonSerializationVerifier<>(dtoModule, json, bean);
    }

    private final DTOModule<T, U> dtoModule;
    private final String json;
    private final T bean;

    private JsonSerializationVerifier(DTOModule<T, U> dtoModule, String json, T bean) {
        this.dtoModule = dtoModule;
        this.json = json;
        this.bean = bean;
    }

    public void verify() throws IOException {
        JsonGenericSerializer<T, U> seriliazer = JsonGenericSerializer
            .forModules(dtoModule)
            .withoutNestedType();

        assertThatJson(seriliazer.serialize(bean))
            .describedAs("Serialization test")
            .isEqualTo(json);

        assertThat(seriliazer.deserialize(json))
            .describedAs("Deserialization test")
            .isEqualTo(bean);
    }
}
