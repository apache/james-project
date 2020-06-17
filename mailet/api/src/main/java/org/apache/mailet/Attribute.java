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

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

/** 
 * Attribute attached to an email.
 *
 * Attributes hold information to be shared during Mail processing, across mailets.
 * 
 * @since Mailet API v3.2
 */
public class Attribute {
    public static Attribute convertToAttribute(String name, Object value) {
        AttributeName attributeName = AttributeName.of(name);
        AttributeValue<?> attributeValue = AttributeValue.ofAny(value);

        return new Attribute(attributeName, attributeValue);
    }

    private final AttributeName name;
    private final AttributeValue<?> value;

    public Attribute(AttributeName name, AttributeValue<?> value) {
        Preconditions.checkNotNull(name, "`name` should not be null");
        Preconditions.checkNotNull(value, "`value` should not be null");

        this.name = name;
        this.value = value;
    }

    public AttributeName getName() {
        return name;
    }

    public AttributeValue<?> getValue() {
        return value;
    }

    public Attribute duplicate() {
        return new Attribute(name, value.duplicate());
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Attribute) {
            Attribute that = (Attribute) o;

            return Objects.equals(this.name, that.name)
                && Objects.equals(this.value, that.value);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(name, value);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("name", name)
            .add("value", value)
            .toString();
    }
}
