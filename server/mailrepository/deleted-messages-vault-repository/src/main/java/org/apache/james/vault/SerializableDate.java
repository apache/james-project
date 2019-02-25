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

package org.apache.james.vault;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.apache.mailet.ArbitrarySerializable;
import org.apache.mailet.AttributeValue;

import com.google.common.base.Preconditions;

public class SerializableDate implements ArbitrarySerializable<SerializableDate> {
    public static class Factory implements ArbitrarySerializable.Deserializer<SerializableDate> {
        @Override
        public Optional<SerializableDate> deserialize(Serializable<SerializableDate> serializable) {
            return Optional.of(serializable.getValue().value())
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(ZonedDateTime::parse)
                .map(SerializableDate::new);
        }
    }

    static AttributeValue<SerializableDate> toAttributeValue(ZonedDateTime date) {
        return AttributeValue.of(new SerializableDate(date));
    }

    private final ZonedDateTime value;

    SerializableDate(ZonedDateTime value) {
        Preconditions.checkNotNull(value);

        this.value = value;
    }

    @Override
    public Serializable<SerializableDate> serialize() {
        return new Serializable<>(AttributeValue.of(value.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)), SerializableDate.Factory.class);
    }

    public ZonedDateTime getValue() {
        return value;
    }
}
