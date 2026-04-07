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

package org.apache.james.events;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

public sealed interface SerializationResult permits SerializationResult.Success, SerializationResult.SuccessBytes, SerializationResult.Failure {
    static SerializationResult of(Optional<String> maybeJson, String throwingMessage) {
        return maybeJson.<SerializationResult>map(Success::new)
            .orElse(new Failure(throwingMessage));
    }

    static SerializationResult ofBytes(Optional<byte[]> maybeJsonBytes, String throwingMessage) {
        return maybeJsonBytes.<SerializationResult>map(SerializationResult.SuccessBytes::new)
            .orElse(new SerializationResult.Failure(throwingMessage));
    }

    String json();

    byte[] jsonBytes();

    default boolean isSuccess() {
        return this instanceof Success;
    }

    record Success(String json) implements SerializationResult {
        @Override
        public String json() {
            return json;
        }

        @Override
        public byte[] jsonBytes() {
            return json.getBytes(StandardCharsets.UTF_8);
        }
    }

    record SuccessBytes(byte[] jsonBytes) implements SerializationResult {
        @Override
        public String json() {
            return new String(jsonBytes, StandardCharsets.UTF_8);
        }

        @Override
        public byte[] jsonBytes() {
            return jsonBytes;
        }
    }

    record Failure(String throwingMessage) implements SerializationResult {
        @Override
        public String json() {
            throw new RuntimeException(throwingMessage);
        }

        @Override
        public byte[] jsonBytes() {
            throw new RuntimeException(throwingMessage);
        }
    }
}
