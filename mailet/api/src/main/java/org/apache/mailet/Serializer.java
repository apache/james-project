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

import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.mailbox.model.MessageIdDto;
import org.apache.james.util.streams.Iterators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
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
 * Controlled Json serialization/deserialization.
 * 
 * @since Mailet API v3.2
 */
@SuppressWarnings("EqualsHashCode")
public interface Serializer<T> {
    Optional<JsonNode> serialize(T object);

    Optional<T> deserialize(JsonNode json);

    String getName();

    T duplicate(T value);

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
                    DATE_SERIALIZER,
                    MESSAGE_ID_DTO_SERIALIZER,
                    BYTES_SERIALIZER,
                    new Serializer.ArbitrarySerializableSerializer<>(),
                    URL_SERIALIZER,
                    new CollectionSerializer<>(),
                    new MapSerializer<>(),
                    // To be dropped in 3.9.0
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
        public Optional<JsonNode> serialize(Boolean object) {
            return Optional.of(BooleanNode.valueOf(object));
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
        public Boolean duplicate(Boolean value) {
            return value;
        }

        @Override
        public String getName() {
            return "BooleanSerializer";
        }

        @Override
        public boolean equals(Object other) {
            return this.getClass() == other.getClass();
        }

        @Override
        public int hashCode() {
            return Objects.hash(getClass());
        }
    }

    Serializer<Boolean> BOOLEAN_SERIALIZER = new BooleanSerializer();

    class StringSerializer implements Serializer<String> {
        @Override
        public Optional<JsonNode> serialize(String object) {
            return Optional.of(TextNode.valueOf(object));
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
        public String duplicate(String value) {
            return value;
        }

        @Override
        public String getName() {
            return "StringSerializer";
        }

        @Override
        public boolean equals(Object other) {
            return this.getClass() == other.getClass();
        }

        @Override
        public int hashCode() {
            return Objects.hash(getClass());
        }
    }

    Serializer<String> STRING_SERIALIZER = new StringSerializer();

    class IntSerializer implements Serializer<Integer> {
        @Override
        public Optional<JsonNode> serialize(Integer object) {
            return Optional.of(IntNode.valueOf(object));
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
        public Integer duplicate(Integer value) {
            return value;
        }

        @Override
        public String getName() {
            return "IntSerializer";
        }

        @Override
        public boolean equals(Object other) {
            return this.getClass() == other.getClass();
        }

        @Override
        public int hashCode() {
            return Objects.hash(getClass());
        }
    }

    Serializer<Integer> INT_SERIALIZER = new IntSerializer();

    class LongSerializer implements Serializer<Long> {
        @Override
        public Optional<JsonNode> serialize(Long object) {
            return Optional.of(LongNode.valueOf(object));
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
        public Long duplicate(Long value) {
            return value;
        }

        @Override
        public String getName() {
            return "LongSerializer";
        }

        @Override
        public boolean equals(Object other) {
            return this.getClass() == other.getClass();
        }

        @Override
        public int hashCode() {
            return Objects.hash(getClass());
        }
    }

    Serializer<Long> LONG_SERIALIZER = new LongSerializer();

    class FloatSerializer implements Serializer<Float> {
        @Override
        public Optional<JsonNode> serialize(Float object) {
            return Optional.of(FloatNode.valueOf(object));
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
        public Float duplicate(Float value) {
            return value;
        }

        @Override
        public String getName() {
            return "FloatSerializer";
        }

        @Override
        public boolean equals(Object other) {
            return this.getClass() == other.getClass();
        }

        @Override
        public int hashCode() {
            return Objects.hash(getClass());
        }
    }

    Serializer<Float> FLOAT_SERIALIZER = new FloatSerializer();

    class DoubleSerializer implements Serializer<Double> {
        @Override
        public Optional<JsonNode> serialize(Double object) {
            return Optional.of(DoubleNode.valueOf(object));
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
        public Double duplicate(Double value) {
            return value;
        }

        @Override
        public String getName() {
            return "DoubleSerializer";
        }

        @Override
        public boolean equals(Object other) {
            return this.getClass() == other.getClass();
        }

        @Override
        public int hashCode() {
            return Objects.hash(getClass());
        }
    }

    Serializer<Double> DOUBLE_SERIALIZER = new DoubleSerializer();

    class DateSerializer implements Serializer<ZonedDateTime> {
        @Override
        public Optional<JsonNode> serialize(ZonedDateTime object) {
            String serialized = object.format(ISO_DATE_TIME);
            return Optional.of(TextNode.valueOf(serialized));
        }

        @Override
        public Optional<ZonedDateTime> deserialize(JsonNode json) {
            if (json instanceof TextNode) {
                String serialized = json.asText();
                return Optional.of(ZonedDateTime.parse(serialized, ISO_DATE_TIME));
            } else {
                return Optional.empty();
            }
        }

        @Override
        public ZonedDateTime duplicate(ZonedDateTime value) {
            return ZonedDateTime.ofInstant(value.toInstant(), value.getZone());
        }

        @Override
        public String getName() {
            return "DateSerializer";
        }

        @Override
        public boolean equals(Object other) {
            return this.getClass() == other.getClass();
        }

        @Override
        public int hashCode() {
            return Objects.hash(getClass());
        }
    }

    Serializer<ZonedDateTime> DATE_SERIALIZER = new DateSerializer();

    class MessageIdDtoSerializer implements Serializer<MessageIdDto> {

        @Override
        public Optional<JsonNode> serialize(MessageIdDto serializable) {
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
        public MessageIdDto duplicate(MessageIdDto value) {
            return value;
        }

        @Override
        public String getName() {
            return "MessageIdDtoSerializer";
        }

        @Override
        public boolean equals(Object other) {
            return this.getClass() == other.getClass();
        }

        @Override
        public int hashCode() {
            return Objects.hash(getClass());
        }
    }

    Serializer<MessageIdDto> MESSAGE_ID_DTO_SERIALIZER = new MessageIdDtoSerializer();

    class ArbitrarySerializableSerializer<T extends ArbitrarySerializable<T>> implements Serializer<T> {
        private static final Logger LOGGER = LoggerFactory.getLogger(ArbitrarySerializableSerializer.class);

        @Override
        public Optional<JsonNode> serialize(T serializable) {
            ArbitrarySerializable.Serializable<T> serialized = serializable.serialize();
            ObjectNode serializedJson = JsonNodeFactory.instance.objectNode();
            serializedJson.put("deserializer", serialized.getDeserializer().getName());
            serializedJson.replace("value", serialized.getValue().toJson().get());
            return Optional.of(serializedJson);
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
                    ArbitrarySerializable.Deserializer<T> deserializer = (ArbitrarySerializable.Deserializer<T>) deserializerClass.getDeclaredConstructor().newInstance();
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

        @Override
        public int hashCode() {
            return Objects.hash(getClass());
        }

        @Override
        public T duplicate(T value) {
            return deserialize(serialize(value).get()).get();
        }
    }

    class UrlSerializer implements Serializer<URL> {
        @Override
        public Optional<JsonNode> serialize(URL object) {
            return STRING_SERIALIZER.serialize(object.toString());
        }

        @Override
        public Optional<URL> deserialize(JsonNode json) {
            return STRING_SERIALIZER.deserialize(json).flatMap(url -> {
                try {
                    return Optional.of(new URI(url).toURL());
                } catch (MalformedURLException | URISyntaxException e) {
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

        @Override
        public int hashCode() {
            return Objects.hash(getClass());
        }

        @Override
        public URL duplicate(URL value) {
            return value;
        }
    }

    Serializer<URL> URL_SERIALIZER = new UrlSerializer();

    class CollectionSerializer<U> implements Serializer<Collection<AttributeValue<U>>> {
        @Override
        public Optional<JsonNode> serialize(Collection<AttributeValue<U>> object) {
            List<JsonNode> jsons = object.stream()
                .map(AttributeValue::toJson)
                .flatMap(Optional::stream)
                .collect(ImmutableList.toImmutableList());
            return Optional.of(new ArrayNode(JsonNodeFactory.instance, jsons));
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
        public Collection<AttributeValue<U>> duplicate(Collection<AttributeValue<U>> value) {
            return value.stream()
                .map(AttributeValue::duplicate)
                .collect(ImmutableList.toImmutableList());
        }

        @Override
        public String getName() {
            return "CollectionSerializer";
        }

        @Override
        public boolean equals(Object other) {
            return this.getClass() == other.getClass();
        }

        @Override
        public int hashCode() {
            return Objects.hash(getClass());
        }
    }

    class MapSerializer<U> implements Serializer<Map<String, AttributeValue<U>>> {
        @Override
        public Optional<JsonNode> serialize(Map<String, AttributeValue<U>> object) {
            Map<String, JsonNode> jsonMap = object.entrySet().stream()
                .flatMap(entry -> entry.getValue().toJson().map(value -> Pair.of(entry.getKey(), value)).stream())
                .collect(ImmutableMap.toImmutableMap(Pair::getKey, Pair::getValue));
            return Optional.of(new ObjectNode(JsonNodeFactory.instance, jsonMap));
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
        public Map<String, AttributeValue<U>> duplicate(Map<String, AttributeValue<U>> value) {
            return value.entrySet()
                .stream()
                .map(entry -> Pair.of(entry.getKey(), entry.getValue().duplicate()))
                .collect(ImmutableMap.toImmutableMap(Pair::getKey, Pair::getValue));
        }

        @Override
        public String getName() {
            return "MapSerializer";
        }

        @Override
        public boolean equals(Object other) {
            return this.getClass() == other.getClass();
        }

        @Override
        public int hashCode() {
            return Objects.hash(getClass());
        }
    }

    class OptionalSerializer<U> implements Serializer<Optional<AttributeValue<U>>> {
        @Override
        public Optional<JsonNode> serialize(Optional<AttributeValue<U>> object) {
            return object.map(AttributeValue::toJson)
                .orElse(Optional.of(NullNode.getInstance()));
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
        public Optional<AttributeValue<U>> duplicate(Optional<AttributeValue<U>> value) {
            return value.map(AttributeValue::duplicate);
        }

        @Override
        public String getName() {
            return "OptionalSerializer";
        }

        @Override
        public boolean equals(Object other) {
            return this.getClass() == other.getClass();
        }

        @Override
        public int hashCode() {
            return Objects.hash(getClass());
        }
    }

    class FSTSerializer implements Serializer<Serializable> {
        @Override
        public Optional<JsonNode> serialize(Serializable object) {
            return Optional.empty();
        }

        @Override
        public Optional<Serializable> deserialize(JsonNode json) {
            return Optional.empty();
        }

        @Override
        public String getName() {
            return "NoSerializer";
        }

        @Override
        public Serializable duplicate(Serializable value) {
            throw new NotImplementedException();
        }
    }

    Serializer<byte[]> BYTES_SERIALIZER = new BytesSerializer();

    class BytesSerializer implements Serializer<byte[]> {

        @Override
        public Optional<JsonNode> serialize(byte[] object) {
            return STRING_SERIALIZER.serialize(Base64.getEncoder().encodeToString(object));
        }

        @Override
        public Optional<byte[]> deserialize(JsonNode json) {
            return STRING_SERIALIZER.deserialize(json).map(Base64.getDecoder()::decode);
        }

        @Override
        public String getName() {
            return "BytesSerializer";
        }

        @Override
        public byte[] duplicate(byte[] value) {
            // Assume byte arrays never to be mutated
            return value;
        }

        @Override
        public boolean equals(Object other) {
            return this.getClass() == other.getClass();
        }

        @Override
        public int hashCode() {
            return Objects.hash(getClass());
        }
    }

    class NoSerializer implements Serializer<Object> {
        @Override
        public Optional<JsonNode> serialize(Object object) {
            return Optional.empty();
        }

        @Override
        public Optional<Object> deserialize(JsonNode json) {
            return Optional.empty();
        }

        @Override
        public String getName() {
            return "NoSerializer";
        }

        @Override
        public Object duplicate(Object value) {
            return value;
        }
    }

}
