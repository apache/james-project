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

import java.util.Optional;

import org.apache.james.core.User;
import org.apache.mailet.ArbitrarySerializable;
import org.apache.mailet.AttributeValue;

import com.google.common.base.Preconditions;

public class SerializableUser implements ArbitrarySerializable<SerializableUser> {
    public static class Factory implements ArbitrarySerializable.Deserializer<SerializableUser> {
        @Override
        public Optional<SerializableUser> deserialize(Serializable<SerializableUser> serializable) {
            return Optional.of(serializable.getValue().value())
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(User::fromUsername)
                .map(SerializableUser::new);
        }
    }

    static AttributeValue<SerializableUser> toAttributeValue(User user) {
        return AttributeValue.of(new SerializableUser(user));
    }

    private final User value;

    SerializableUser(User value) {
        Preconditions.checkNotNull(value);

        this.value = value;
    }

    @Override
    public Serializable<SerializableUser> serialize() {
        return new Serializable<>(AttributeValue.of(value.asString()), Factory.class);
    }

    public User getValue() {
        return value;
    }
}
