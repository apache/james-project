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

package org.apache.james.mdn.fields;

import java.util.Objects;

import com.google.common.base.Preconditions;

public class AddressType {
    public static final AddressType DNS = new AddressType("dns");
    public static final AddressType RFC_822 = new AddressType("rfc822");
    public static final AddressType UNKNOWN = new AddressType("unknown");

    private final String type;

    public AddressType(String type) {
        Preconditions.checkNotNull(type);
        Preconditions.checkArgument(!type.contains("\n"), "Address type can not be multiline");
        String trimmedType = type.trim();
        Preconditions.checkArgument(!trimmedType.isEmpty(), "Address type can not be empty");

        this.type = trimmedType;
    }

    public String getType() {
        return type;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof AddressType) {
            AddressType that = (AddressType) o;

            return Objects.equals(this.type, that.type);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(type);
    }
}
