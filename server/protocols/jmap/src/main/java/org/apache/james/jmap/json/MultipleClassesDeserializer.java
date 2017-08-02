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
package org.apache.james.jmap.json;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

public class MultipleClassesDeserializer extends StdDeserializer<Object> {

    private final Map<String, Class<?>> registry = new HashMap<>();

    MultipleClassesDeserializer() {
        super(Object.class);
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        final JsonNode root = mapper.readTree(p);

        return registry.entrySet().stream()
                .filter(req -> ! (root.at(req.getKey()).isMissingNode()))
                .map(x -> readValue(mapper, root, x.getValue()))
                .findFirst()
                .orElseThrow(() -> new JsonMappingException("Can't map request to a known registered class"));
    }

    private Object readValue(ObjectMapper mapper, JsonNode root, Class<?> clazz) {
        try {
            return mapper.treeToValue(root, clazz);
        } catch (JsonProcessingException e) {
            throw Throwables.propagate(e);
        }
    }

    public void registerClass(String uniqueJsonPath, Class<?> clazz) {
        Preconditions.checkArgument(! registry.containsKey(uniqueJsonPath), "Path %s has already been registered", uniqueJsonPath);
        registry.put(uniqueJsonPath, clazz);
    }
}
