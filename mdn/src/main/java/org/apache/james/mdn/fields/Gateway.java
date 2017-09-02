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

/**
 * MDN-Gateway field as specified in https://tools.ietf.org/html/rfc8098#section-3.2.2
 */
public class Gateway implements Field {
    public static final String FIELD_NAME = "MDN-Gateway";

    private final AddressType nameType;
    private final Text name;

    public Gateway(AddressType nameType, Text name) {
        Preconditions.checkNotNull(nameType);
        Preconditions.checkNotNull(name);

        this.nameType = nameType;
        this.name = name;
    }

    public Gateway(Text name) {
        this(AddressType.DNS, name);
    }

    @Override
    public String formattedValue() {
        return FIELD_NAME + ": " + nameType.getType() + ";" + name.formatted();
    }

    public AddressType getNameType() {
        return nameType;
    }

    public Text getName() {
        return name;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Gateway) {
            Gateway gateway = (Gateway) o;

            return Objects.equals(nameType, gateway.nameType)
                && Objects.equals(name, gateway.name);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(nameType, name);
    }

    @Override
    public String toString() {
        return formattedValue();
    }
}
