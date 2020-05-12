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

package org.apache.mailet;

import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.james.mailbox.model.MessageIdDto;
import org.apache.james.util.streams.Iterators;
import org.nustaq.serialization.FSTConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.FloatNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/** 
 * Controlled Json serialization/deserialization
 * 
 * @since Mailet API v3.2
 */
@SuppressWarnings("EqualsHashCode")
public interface Serializer<T> {
    JsonNode serialize(T object);

    Optional<T> deserialize(JsonNode json);

    String getName();

    class Registry {

        private static ImmutableMap<String, Serializer<?>> serializers;

        static {
            serializers = Stream
                .<Serializer<?>>of(
                    BOOLEAN_SERIALIZER,
                    STRING_SERIALIZER,
                    INT_SERIALIZER,
                    LONG_SERIALIZER,
                    FLOAT_SERIALIZER,
                    DOUBLE_SERIALIZER,
                    MESSAGE_ID_DTO_SERIALIZER,
                    new Serializer.ArbitrarySerializableSerializer<>(),
                    URL_SERIALIZER,
                    new CollectionSerializer<>(),
                    new MapSerializer<>(),
                    new FSTSerializer(),
                    new OptionalSerializer<>())
                .collect(ImmutableMap.toImmutableMap(Serializer::getName, Function.identity()));
        }

        static Optional<Serializer<?>> find(String name) {
            return Optional.ofNullable(serializers.get(name));
        }
    }

    class BooleanSerializer implements Serializer<Boolean> {
        @Override
        public JsonNode serialize(Boolean object) {
            return BooleanNode.valueOf(object);
        }

        @Override
        public Optional<Boolean> deserialize(JsonNode json) {
            if (json instanceof BooleanNode) {
                return Optional.of(json.asBoolean());
            } else {
                return Optional.empty();
            }
        }

        @Override
        public String getName() {
            return "BooleanSerializer";
        }

        @Override
        public boolean equals(Object other) {
            return this.getClass() == other.getClass();
        }
    }

    Serializer<Boolean> BOOLEAN_SERIALIZER = new BooleanSerializer();

    class StringSerializer implements Serializer<String> {
        @Override
        public JsonNode serialize(String object) {
            return TextNode.valueOf(object);
        }

        @Override
        public Optional<String> deserialize(JsonNode json) {
            if (json instanceof TextNode) {
                return Optional.of(json.asText());
            } else {
                return Optional.empty();
            }
        }

        @Override
        public String getName() {
            return "StringSerializer";
        }

        @Override
        public boolean equals(Object other) {
            return this.getClass() == other.getClass();
        }
    }

    Serializer<String> STRING_SERIALIZER = new StringSerializer();

    class IntSerializer implements Serializer<Integer> {
        @Override
        public JsonNode serialize(Integer object) {
            return IntNode.valueOf(object);
        }

        @Override
        public Optional<Integer> deserialize(JsonNode json) {
            if (json instanceof IntNode) {
                return Optional.of(json.asInt());
            } else {
                return Optional.empty();
            }
        }

        @Override
        public String getName() {
            return "IntSerializer";
        }

        @Override
        public boolean equals(Object other) {
            return this.getClass() == other.getClass();
        }
    }

    Serializer<Integer> INT_SERIALIZER = new IntSerializer();

    class LongSerializer implements Serializer<Long> {
        @Override
        public JsonNode serialize(Long object) {
            return LongNode.valueOf(object);
        }

        @Override
        public Optional<Long> deserialize(JsonNode json) {
            if (json instanceof LongNode) {
                return Optional.of(json.asLong());
            } else if (json instanceof IntNode) {
                return Optional.of(Long.valueOf(json.asInt()));
            } else {
                return Optional.empty();
            }
        }

        @Override
        public String getName() {
            return "LongSerializer";
        }

        @Override
        public boolean equals(Object other) {
            return this.getClass() == other.getClass();
        }
    }

    Serializer<Long> LONG_SERIALIZER = new LongSerializer();

    class FloatSerializer implements Serializer<Float> {
        @Override
        public JsonNode serialize(Float object) {
            return FloatNode.valueOf(object);
        }

        @Override
        public Optional<Float> deserialize(JsonNode json) {
            if (json instanceof FloatNode) {
                return Optional.of(json.floatValue());
            } else if (json instanceof DoubleNode) {
                return Optional.of(json.floatValue());
            } else {
                return Optional.empty();
            }
        }

        @Override
        public String getName() {
            return "FloatSerializer";
        }

        @Override
        public boolean equals(Object other) {
            return this.getClass() == other.getClass();
        }
    }

    Serializer<Float> FLOAT_SERIALIZER = new FloatSerializer();

    class DoubleSerializer implements Serializer<Double> {
        @Override
        public JsonNode serialize(Double object) {
            return DoubleNode.valueOf(object);
        }

        @Override
        public Optional<Double> deserialize(JsonNode json) {
            if (json instanceof DoubleNode || json instanceof FloatNode) {
                return Optional.of(json.asDouble());
            } else {
                return Optional.empty();
            }
        }

        @Override
        public String getName() {
            return "DoubleSerializer";
        }

        @Override
        public boolean equals(Object other) {
            return this.getClass() == other.getClass();
        }
    }

    Serializer<Double> DOUBLE_SERIALIZER = new DoubleSerializer();

    class MessageIdDtoSerializer implements Serializer<MessageIdDto> {

        @Override
        public JsonNode serialize(MessageIdDto serializable) {
            return STRING_SERIALIZER
                    .serialize(serializable.asString());
        }

        @Override
        public Optional<MessageIdDto> deserialize(JsonNode json) {
            return STRING_SERIALIZER
                    .deserialize(json)
                    .map(MessageIdDto::new);
        }

        @Override
        public String getName() {
            return "MessageIdDtoSerializer";
        }

        @Override
        public boolean equals(Object other) {
            return this.getClass() == other.getClass();
        }
    }

    Serializer<MessageIdDto> MESSAGE_ID_DTO_SERIALIZER = new MessageIdDtoSerializer();

    class ArbitrarySerializableSerializer<T extends ArbitrarySerializable<T>> implements Serializer<T> {
        private static final Logger LOGGER = LoggerFactory.getLogger(ArbitrarySerializableSerializer.class);

        @Override
        public JsonNode serialize(T serializable) {
            ArbitrarySerializable.Serializable<T> serialized = serializable.serialize();
            ObjectNode serializedJson = JsonNodeFactory.instance.objectNode();
            serializedJson.put("deserializer", serialized.getDeserializer().getName());
            serializedJson.replace("value", serialized.getValue().toJson());
            return serializedJson;
        }

        @Override
        public Optional<T> deserialize(JsonNode json) {
            return Optional.of(json)
                    .filter(ObjectNode.class::isInstance)
                    .map(ObjectNode.class::cast)
                    .flatMap(this::instantiate);
        }

        public Optional<T> instantiate(ObjectNode fields) {
            return Optional.ofNullable(fields.get("deserializer"))
                .flatMap(serializer ->
                    Optional.ofNullable(fields.get("value"))
                        .flatMap(value -> deserialize(serializer.asText(), AttributeValue.fromJson(value))));
        }

        @SuppressWarnings("unchecked")
        private Optional<T> deserialize(String serializer, AttributeValue<?> value) {
            try {
                Class<?> deserializerClass = Class.forName(serializer);
                if (ArbitrarySerializable.Deserializer.class.isAssignableFrom(deserializerClass)) {
                    ArbitrarySerializable.Deserializer<T> deserializer = (ArbitrarySerializable.Deserializer<T>) deserializerClass.newInstance();
                    return deserializer.deserialize(new ArbitrarySerializable.Serializable<>(value, (Class<ArbitrarySerializable.Deserializer<T>>) deserializerClass));
                }
            } catch (Exception e) {
                LOGGER.error("Error while deserializing using serializer {} and value {}", serializer, value, e);
            }

            return Optional.empty();
        }

        @Override
        public String getName() {
            return "ArbitrarySerializableSerializer";
        }

        @Override
        public boolean equals(Object other) {
            return this.getClass() == other.getClass();
        }
    }

    class UrlSerializer implements Serializer<URL> {
        @Override
        public JsonNode serialize(URL object) {
            return STRING_SERIALIZER.serialize(object.toString());
        }

        @Override
        public Optional<URL> deserialize(JsonNode json) {
            return STRING_SERIALIZER.deserialize(json).flatMap(url -> {
                try {
                    return Optional.of(new URL(url));
                } catch (MalformedURLException e) {
                    return Optional.empty();
                }
            });
        }

        @Override
        public String getName() {
            return "UrlSerializer";
        }

        @Override
        public boolean equals(Object other) {
            return this.getClass() == other.getClass();
        }
    }

    Serializer<URL> URL_SERIALIZER = new UrlSerializer();

    class CollectionSerializer<U> implements Serializer<Collection<AttributeValue<U>>> {
        @Override
        public JsonNode serialize(Collection<AttributeValue<U>> object) {
            List<JsonNode> jsons = object.stream()
                .map(AttributeValue::toJson)
                .collect(ImmutableList.toImmutableList());
            return new ArrayNode(JsonNodeFactory.instance, jsons);
        }

        @SuppressWarnings("unchecked")
        @Override
        public Optional<Collection<AttributeValue<U>>> deserialize(JsonNode json) {
            if (json instanceof ArrayNode) {
                return Optional.of(Iterators.toStream(json.elements())
                        .map(value -> (AttributeValue<U>) AttributeValue.fromJson(value))
                        .collect(ImmutableList.toImmutableList()));
            } else {
                return Optional.empty();
            }
        }

        @Override
        public String getName() {
            return "CollectionSerializer";
        }

        @Override
        public boolean equals(Object other) {
            return this.getClass() == other.getClass();
        }
    }

    class MapSerializer<U> implements Serializer<Map<String, AttributeValue<U>>> {
        @Override
        public JsonNode serialize(Map<String, AttributeValue<U>> object) {
            Map<String, JsonNode> jsonMap = object.entrySet().stream()
                .collect(ImmutableMap.toImmutableMap(Entry::getKey, entry -> entry.getValue().toJson()));
            return new ObjectNode(JsonNodeFactory.instance, jsonMap);
        }

        @SuppressWarnings("unchecked")
        @Override
        public Optional<Map<String, AttributeValue<U>>> deserialize(JsonNode json) {
            if (json instanceof ObjectNode) {
                return Optional.of(Iterators.toStream(json.fields())
                        .collect(ImmutableMap.toImmutableMap(
                            Map.Entry::getKey,
                            entry -> (AttributeValue<U>) AttributeValue.fromJson(entry.getValue())
                        )));
            } else {
                return Optional.empty();
            }
        }

        @Override
        public String getName() {
            return "MapSerializer";
        }

        @Override
        public boolean equals(Object other) {
            return this.getClass() == other.getClass();
        }
    }

    class OptionalSerializer<U> implements Serializer<Optional<AttributeValue<U>>> {
        @Override
        public JsonNode serialize(Optional<AttributeValue<U>> object) {
            return object.map(AttributeValue::toJson)
                .orElse(NullNode.getInstance());
        }

        @SuppressWarnings("unchecked")
        @Override
        public Optional<Optional<AttributeValue<U>>> deserialize(JsonNode json) {
            if (json instanceof ObjectNode) {
                AttributeValue<U> value = (AttributeValue<U>) AttributeValue.fromJson(json);
                return Optional.of(Optional.of(value));
            } else if (json instanceof NullNode) {
                return Optional.of(Optional.empty());
            } else {
                return Optional.empty();
            }
        }

        @Override
        public String getName() {
            return "OptionalSerializer";
        }

        @Override
        public boolean equals(Object other) {
            return this.getClass() == other.getClass();
        }
    }

    class FSTSerializer implements Serializer<Serializable> {
        static final FSTConfiguration CONFIGURATION = FSTConfiguration.createJsonConfiguration();

        @Override
        public JsonNode serialize(Serializable object) {
            String json = CONFIGURATION.asJsonString(object);
            try {
                return new ObjectMapper().reader().readTree(json);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public Optional<Serializable> deserialize(JsonNode json) {
            try {
                return Optional.of((Serializable) CONFIGURATION.asObject(new ObjectMapper().writer().writeValueAsBytes(json)));
            } catch (JsonProcessingException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public String getName() {
            return "FSTSerializer";
        }

        @Override
        public boolean equals(Object other) {
            return this.getClass() == other.getClass();
        }
    }

}
