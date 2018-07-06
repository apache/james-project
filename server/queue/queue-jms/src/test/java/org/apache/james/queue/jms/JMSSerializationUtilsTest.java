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
package org.apache.james.queue.jms;

import static org.apache.james.queue.jms.JMSSerializationUtils.deserialize;
import static org.apache.james.queue.jms.JMSSerializationUtils.serialize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.SerializationException;
import org.junit.jupiter.api.Test;

class JMSSerializationUtilsTest {
    /**
     * Serializes and deserializes the provided object.
     *
     * @param obj The object that needs to be serialized.
     * @param <T> The type of the provided object.
     *
     * @return The provided object.
     */
    private static <T extends Serializable> T roundtrip(T obj) {
        return Optional.ofNullable(obj)
                .map(JMSSerializationUtils::serialize)
                .<T>map(JMSSerializationUtils::deserialize)
                .orElseThrow(() -> new IllegalArgumentException("Cannot serialize/deserialize: " + obj));
    }

    @Test
    void trySerializeShouldReturnString() {
        SerializableStringHolder value = new SerializableStringHolder("value");

        String serializedIntegerString = "rO0ABXNyAE1vcmcuYXBhY2hlLmphbWVzLnF1ZXVlLmptcy5KTVNTZXJpYWxp" +
                "emF0aW9uVXRpbHNUZXN0JFNlcmlhbGl6YWJsZVN0cmluZ0hvbGRlcsy4/DEA" +
                "8nRZAgABTAAFdmFsdWV0ABJMamF2YS9sYW5nL1N0cmluZzt4cHQABXZhbHVl";

        String actual = serialize(value);

        assertThat(actual).isEqualTo(serializedIntegerString);
    }

    @Test
    void roundTripShouldReturnEqualObject() {
        SerializableStringHolder expected = new SerializableStringHolder("value");

        assertThat(roundtrip(expected)).isEqualTo(expected);
    }

    @Test
    void deserializeShouldThrowWhenNotBase64StringProvided() {
        assertThatExceptionOfType(SerializationException.class)
                .isThrownBy(() -> deserialize("abc"));
    }

    @Test
    void deserializeShouldThrowWhenNotSerializedBytesAreEncodedInBase64() {
        assertThatExceptionOfType(SerializationException.class)
                .isThrownBy(() -> deserialize(Base64.encodeBase64String("abc".getBytes(StandardCharsets.UTF_8))));
    }

    private static class SerializableStringHolder implements Serializable {
        private final String value;

        SerializableStringHolder(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof SerializableStringHolder)) {
                return false;
            }
            SerializableStringHolder that = (SerializableStringHolder) o;
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }
}
