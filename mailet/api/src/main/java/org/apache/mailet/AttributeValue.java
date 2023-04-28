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
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.mailbox.model.MessageIdDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

/** 
 * Strong typing for attribute value, which represents the value of an attribute stored in a mail.
 * 
 * @since Mailet API v3.2
 */
public class AttributeValue<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AttributeValue.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static AttributeValue<Boolean> of(Boolean value) {
        Preconditions.checkNotNull(value, "value should not be null");
        return new AttributeValue<>(value, Serializer.BOOLEAN_SERIALIZER);
    }

    public static AttributeValue<String> of(String value) {
        Preconditions.checkNotNull(value, "value should not be null");
        return new AttributeValue<>(value, Serializer.STRING_SERIALIZER);
    }

    public static AttributeValue<byte[]> of(byte[] value) {
        Preconditions.checkNotNull(value, "value should not be null");
        return new AttributeValue<>(value, Serializer.BYTES_SERIALIZER);
    }

    public static AttributeValue<Integer> of(Integer value) {
        Preconditions.checkNotNull(value, "value should not be null");
        return new AttributeValue<>(value, Serializer.INT_SERIALIZER);
    }

    public static AttributeValue<Long> of(Long value) {
        Preconditions.checkNotNull(value, "value should not be null");
        return new AttributeValue<>(value, Serializer.LONG_SERIALIZER);
    }

    public static AttributeValue<Float> of(Float value) {
        Preconditions.checkNotNull(value, "value should not be null");
        return new AttributeValue<>(value, Serializer.FLOAT_SERIALIZER);
    }

    public static AttributeValue<ZonedDateTime> of(ZonedDateTime value) {
        Preconditions.checkNotNull(value, "value should not be null");
        return new AttributeValue<>(value, Serializer.DATE_SERIALIZER);
    }

    public static AttributeValue<Double> of(Double value) {
        Preconditions.checkNotNull(value, "value should not be null");
        return new AttributeValue<>(value, Serializer.DOUBLE_SERIALIZER);
    }

    public static AttributeValue<MessageIdDto> of(MessageIdDto value) {
        Preconditions.checkNotNull(value, "value should not be null");
        return new AttributeValue<>(value, Serializer.MESSAGE_ID_DTO_SERIALIZER);
    }

    public static <T extends ArbitrarySerializable<T>> AttributeValue<T> of(T value) {
        Preconditions.checkNotNull(value, "value should not be null");
        return new AttributeValue<>(value, new Serializer.ArbitrarySerializableSerializer<>());
    }

    public static AttributeValue<URL> of(URL value) {
        Preconditions.checkNotNull(value, "value should not be null");
        return new AttributeValue<>(value, Serializer.URL_SERIALIZER);
    }

    public static <T> AttributeValue<Optional<AttributeValue<T>>> of(Optional<AttributeValue<T>> value) {
        Preconditions.checkNotNull(value, "value should not be null");
        Preconditions.checkArgument(value.map(v -> v instanceof AttributeValue).orElse(true),
            "value should be of type Optional<AttributeValue<T>> and was not");
        return new AttributeValue<>(value, new Serializer.OptionalSerializer<>());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static AttributeValue<Collection<AttributeValue<?>>> of(Collection<AttributeValue<?>> value) {
        Preconditions.checkNotNull(value, "value should not be null");
        Preconditions.checkArgument(value.stream().allMatch(entry -> entry instanceof AttributeValue),
            "Expecting Collection<AttributeValue<?>: invalid typing.");
        return new AttributeValue<>(value, new Serializer.CollectionSerializer());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static AttributeValue<Map<String, AttributeValue<?>>> of(Map<String, AttributeValue<?>> value) {
        Preconditions.checkNotNull(value, "value should not be null");
        Preconditions.checkArgument(value.entrySet().stream().allMatch(entry -> (entry.getKey() instanceof String) && (entry.getValue() instanceof AttributeValue)),
            "Expecting Map<String, AttributeValue<?>>: invalid typing.");
        return new AttributeValue<>(value, new Serializer.MapSerializer());
    }

    public static AttributeValue<Object> ofUnserializable(Object any) {
        Preconditions.checkNotNull(any, "value should not be null");
        return new AttributeValue<>(any, new Serializer.NoSerializer());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static AttributeValue<?> ofAny(Object value) {
        Preconditions.checkNotNull(value, "value should not be null");
        if (value instanceof Boolean) {
            return of((Boolean) value);
        }
        if (value instanceof String) {
            return of((String) value);
        }
        if (value instanceof byte[]) {
            return of((byte[]) value);
        }
        if (value instanceof Integer) {
            return of((Integer) value);
        }
        if (value instanceof Long) {
            return of((Long) value);
        }
        if (value instanceof Float) {
            return of((Float) value);
        }
        if (value instanceof Double) {
            return of((Double) value);
        }
        if (value instanceof ZonedDateTime) {
            return of((ZonedDateTime) value);
        }
        if (value instanceof Collection<?>) {
            return of(((Collection<AttributeValue<?>>) value));
        }
        if (value instanceof Map<?,?>) {
            return of(((Map<String, AttributeValue<?>>) value));
        }
        if (value instanceof MessageIdDto) {
            return of((MessageIdDto) value);
        }
        if (value instanceof ArbitrarySerializable) {
            return of((ArbitrarySerializable) value);
        }
        if (value instanceof URL) {
            return of((URL) value);
        }
        if (value instanceof Optional) {
            return of((Optional) value);
        }
        throw new IllegalArgumentException(value.getClass().toString() + " should at least be Serializable");
    }

    public static AttributeValue<?> fromJsonString(String json) throws IOException {
        JsonNode tree = OBJECT_MAPPER.readTree(json);
        return fromJson(tree);
    }

    public static AttributeValue<?> fromJson(JsonNode input) {
        return Optional.ofNullable(input)
                .filter(ObjectNode.class::isInstance)
                .map(ObjectNode.class::cast)
                .flatMap(AttributeValue::deserialize)
                .map(AttributeValue::ofAny)
                .orElseThrow(() -> new IllegalStateException("unable to deserialize " + input.toString()));
    }

    public static Optional<?> deserialize(ObjectNode fields) {
        Preconditions.checkNotNull(fields);
        Optional<JsonNode> maybeSerializer = Optional.ofNullable(fields.get("serializer"));
        Optional<JsonNode> maybeValue = Optional.ofNullable(fields.get("value"));

        return maybeSerializer.flatMap(serializer -> maybeValue
            .flatMap(value -> findSerializerAndDeserialize(serializer, value)));
    }

    public static Optional<?> findSerializerAndDeserialize(JsonNode serializer, JsonNode value) {
        return Serializer.Registry.find(serializer.asText())
                .flatMap(s -> s.deserialize(value));
    }

    private final T value;
    private final Serializer<T> serializer;

    private AttributeValue(T value, Serializer<T> serializer) {
        this.value = value;
        this.serializer = serializer;
    }

    public T value() {
        return value;
    }

    public <U> Optional<U> valueAs(Class<U> type) {
        return asAttributeValueOf(type).map(AttributeValue::value);
    }

    public <U> Optional<AttributeValue<U>> asAttributeValueOf(Class<U> type) {
        if (type.isInstance(value)) {
            return Optional.of((AttributeValue<U>) this);
        } else {
            return Optional.empty();
        }
    }
    
    public <U> Optional<AttributeValue<Map<String, AttributeValue<U>>>> asMapAttributeValueOf(Class<U> type) {
        if (value instanceof Map) {
            Map<String, AttributeValue<?>> aMap = (Map<String, AttributeValue<?>>) value;
            Map<String, AttributeValue<U>> castedMap = aMap.entrySet()
                .stream()
                .flatMap(entry -> entry.getValue().asAttributeValueOf(type).stream().map(castedValue -> Pair.of(entry.getKey(), castedValue)))
                .collect(ImmutableMap.toImmutableMap(Pair::getKey, Pair::getValue));
            return Optional.of(new AttributeValue<>(castedMap, new Serializer.MapSerializer()));
        } else {
            return Optional.empty();
        }
    }

    public AttributeValue<T> duplicate() {
        return new AttributeValue<>(serializer.duplicate(value), serializer);
    }

    public Optional<JsonNode> toJson() {
        return serializer.serialize(value)
            .map(value -> {
                ObjectNode serialized = JsonNodeFactory.instance.objectNode();
                serialized.put("serializer", serializer.getName());
                serialized.replace("value", value);
                return serialized;
            });
    }

    public T getValue() {
        return value;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof AttributeValue) {
            AttributeValue<?> that = (AttributeValue<?>) o;

            return Objects.equals(this.value, that.value)
                && Objects.equals(this.serializer, that.serializer);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(value, serializer);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("value", value)
            .add("serializer", serializer.getName())
            .toString();
    }
}
