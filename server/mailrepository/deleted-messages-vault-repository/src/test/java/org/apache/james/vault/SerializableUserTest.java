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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.core.User;
import org.apache.mailet.AttributeValue;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

class SerializableUserTest {
    private static final User USER = User.fromUsername("bob@apache.org");

    @Test
    void serializeBackAndForthShouldPreserveUser() {
        JsonNode serializableUser = AttributeValue.of(new SerializableUser(USER)).toJson();

        User user = AttributeValue.fromJson(serializableUser)
            .valueAs(SerializableUser.class)
            .get()
            .getValue();

        assertThat(user).isEqualTo(USER);
    }

    @Test
    void constructorShouldThrowOnNull() {
        assertThatThrownBy(() -> new SerializableUser(null))
            .isInstanceOf(NullPointerException.class);
    }

}