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
import com.google.common.base.Strings;

/** 
 * Strong typing for attribute name, which represents the name of an attribute stored in a mail.
 * 
 * @since Mailet API v3.2
 */
public class AttributeName {

    public static final AttributeName FORWARDED_MAIL_ADDRESSES_ATTRIBUTE_NAME = AttributeName.of("forwarded.mail.addresses");

    private final String name;

    public static AttributeName of(String name) {
        Preconditions.checkNotNull(name, "`name` is compulsory");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "`name` should not be empty");

        return new AttributeName(name);
    }

    private AttributeName(String name) {
        this.name = name;
    }

    public Attribute withValue(AttributeValue<?> value) {
        return new Attribute(this, value);
    }

    public String asString() {
        return name;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof AttributeName) {
            AttributeName that = (AttributeName) o;

            return Objects.equals(this.name, that.name);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("name", name)
            .toString();
    }
}
