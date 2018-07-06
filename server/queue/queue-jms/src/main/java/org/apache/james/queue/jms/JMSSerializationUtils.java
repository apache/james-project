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

import java.io.Serializable;
import java.util.Optional;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.SerializationUtils;

import com.github.fge.lambdas.Throwing;

/**
 * This class is similar to {@link SerializationUtils}. Unlike {@link SerializationUtils} this class operates with
 * {@code String}s and not byte arrays.
 * <p>
 * The main advantage of this utility is that it introduces an additional operation after serialization and before
 * deserialization. The operation consists in encoding/decoding the serialized/deserialized data in Base64, so that data
 * can be safely transmitted over the wire.
 */
public class JMSSerializationUtils {
    /**
     * Serialize the input object using standard mechanisms then encodes result using base64 encoding.
     *
     * @param obj The object that needs to be serialized.
     *
     * @return The base64 representation of {@code obj}.
     */
    public static String serialize(Serializable obj) {
        return Optional.ofNullable(obj)
                .map(SerializationUtils::serialize)
                .map(Base64::encodeBase64String)
                .orElse(null);
    }

    /**
     * Decodes the input base64 string and deserialize it.
     *
     * @param <T>    The resulting type after deserialization.
     * @param object The base64 encoded string.
     *
     * @return The deserialized object.
     */
    public static <T extends Serializable> T deserialize(String object) {
        return Optional.ofNullable(object)
                .map(Throwing.function(Base64::decodeBase64))
                .<T>map(SerializationUtils::deserialize)
                .orElse(null);
    }
}
