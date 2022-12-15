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

package org.apache.james.backends.opensearch.json;

import java.lang.reflect.Field;

import javax.annotation.Nullable;

import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.JsonpSerializer;

import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;

public abstract class JsonpMapperBase implements JsonpMapper {

    /** Get a serializer when none of the builtin ones are applicable */
    protected abstract <T> JsonpDeserializer<T> getDefaultDeserializer(Class<T> clazz);

    @Override
    public <T> T deserialize(JsonParser parser, Class<T> clazz) {
        JsonpDeserializer<T> deserializer = findDeserializer(clazz);
        if (deserializer != null) {
            return deserializer.deserialize(parser, this);
        }

        return getDefaultDeserializer(clazz).deserialize(parser, this);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> JsonpDeserializer<T> findDeserializer(Class<T> clazz) {
        JsonpDeserializable annotation = clazz.getAnnotation(JsonpDeserializable.class);
        if (annotation != null) {
            try {
                Field field = clazz.getDeclaredField(annotation.field());
                return (JsonpDeserializer<T>)field.get(null);
            } catch (Exception e) {
                throw new RuntimeException("No deserializer found in '" + clazz.getName() + "." + annotation.field() + "'");
            }
        }

        return null;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> JsonpSerializer<T> findSerializer(T value) {
        Class<?> valueClass = value.getClass();
        if (JsonpSerializable.class.isAssignableFrom(valueClass)) {
            return (JsonpSerializer<T>) JsonpSerializableSerializer.INSTANCE;
        }

        if (JsonValue.class.isAssignableFrom(valueClass)) {
            return (JsonpSerializer<T>) JsonpValueSerializer.INSTANCE;
        }

        return null;
    }

    protected static class JsonpSerializableSerializer<T extends JsonpSerializable> implements JsonpSerializer<T> {
        @Override
        public void serialize(T value, JsonGenerator generator, JsonpMapper mapper) {
            value.serialize(generator, mapper);
        }

        protected static final JsonpSerializer<?> INSTANCE = new JsonpSerializableSerializer<>();

    }

    protected static class JsonpValueSerializer implements JsonpSerializer<JsonValue> {
        @Override
        public void serialize(JsonValue value, JsonGenerator generator, JsonpMapper mapper) {
            generator.write(value);
        }

        protected static final JsonpSerializer<?> INSTANCE = new JsonpValueSerializer();

    }

}
