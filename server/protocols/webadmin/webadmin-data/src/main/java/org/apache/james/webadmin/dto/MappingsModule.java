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

package org.apache.james.webadmin.dto;

import java.io.IOException;
import java.util.List;

import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.rrt.lib.MappingsImpl;
import org.apache.james.webadmin.utils.JsonTransformerModule;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

public class MappingsModule implements JsonTransformerModule {

    private static class MappingSourceSerializer extends JsonSerializer<MappingSource> {
        @Override
        public void serialize(MappingSource mappingSource, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeString(mappingSource.asString());
        }
    }

    private static class MappingSourceDeserializer extends JsonDeserializer<MappingSource> {
        @Override
        public MappingSource deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            return MappingSource.parse(jsonParser.getText());
        }
    }

    private static class MappingSourceKeySerializer extends JsonSerializer<MappingSource> {
        @Override
        public void serialize(MappingSource mappingSource, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeFieldName(mappingSource.asString());
        }
    }

    private static class MappingSourceKeyDeserializer extends KeyDeserializer {
        @Override
        public Object deserializeKey(String key, DeserializationContext deserializationContext) throws IOException {
            return MappingSource.parse(key);
        }
    }

    private static class MappingSerializer extends JsonSerializer<Mapping> {
        @Override
        public void serialize(Mapping mapping, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField("type", mapping.getType().toString());
            jsonGenerator.writeStringField("mapping", mapping.getMappingValue());
            jsonGenerator.writeEndObject();
        }
    }

    private static class MappingDeserializer extends JsonDeserializer<Mapping> {
        @Override
        public Mapping deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);
            String type = node.get("type").asText();
            String mapping = node.get("mapping").asText();
            return Mapping.of(Mapping.Type.valueOf(type), mapping);
        }
    }

    private static class MappingsSerializer extends JsonSerializer<Mappings> {
        @Override
        public void serialize(Mappings mappings, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeStartArray();
            for (Mapping mapping : mappings) {
                jsonGenerator.writeObject(mapping);
            }
            jsonGenerator.writeEndArray();
        }
    }

    private static class MappingsDeserializer extends JsonDeserializer<Mappings> {
        @Override
        public Mappings deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            ObjectMapper mapper = (ObjectMapper) jsonParser.getCodec();
            List<Mapping> items = mapper.readValue(jsonParser, new TypeReference<>() {});
            return MappingsImpl.fromMappings(items.stream());
        }
    }

    private final SimpleModule simpleModule;

    public MappingsModule() {
        simpleModule = new SimpleModule()
            .addSerializer(MappingSource.class, new MappingSourceSerializer())
            .addDeserializer(MappingSource.class, new MappingSourceDeserializer())
            .addKeySerializer(MappingSource.class, new MappingSourceKeySerializer())
            .addKeyDeserializer(MappingSource.class, new MappingSourceKeyDeserializer())
            .addSerializer(Mapping.class, new MappingSerializer())
            .addDeserializer(Mapping.class, new MappingDeserializer())
            .addSerializer(Mappings.class, new MappingsSerializer())
            .addDeserializer(Mappings.class, new MappingsDeserializer());
    }

    @Override
    public Module asJacksonModule() {
        return simpleModule;
    }
}