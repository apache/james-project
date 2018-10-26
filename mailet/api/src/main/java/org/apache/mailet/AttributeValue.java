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

import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** 
 * Strong typing for attribute value, which represents the value of an attribute stored in a mail.
 * 
 * @since Mailet API v3.2
 */
public class AttributeValue<T> {

    private final T value;
    private final Serializer<T> serializer;

    private AttributeValue(T value, Serializer<T> serializer) {
        this.value = value;
        this.serializer = serializer;
    }

    public T value() {
        return value;
    }

    public JsonNode toJson() {
        ObjectNode serialized = JsonNodeFactory.instance.objectNode();
        serialized.put("serializer", serializer.getName());
        serialized.replace("value", serializer.serialize(value));
        return serialized;
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
}
