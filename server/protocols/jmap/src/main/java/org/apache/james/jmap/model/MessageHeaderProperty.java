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
package org.apache.james.jmap.model;

import java.util.Locale;
import java.util.Objects;

public class MessageHeaderProperty implements Property {

    public static final String HEADER_PROPERTY_PREFIX = "headers.";

    public static MessageHeaderProperty from(MessageProperty messageProperty) {
        return new MessageHeaderProperty(HEADER_PROPERTY_PREFIX + messageProperty.asFieldName().toLowerCase(Locale.US));
    }

    public static MessageHeaderProperty fromField(String field) {
        return new MessageHeaderProperty(HEADER_PROPERTY_PREFIX + field.toLowerCase(Locale.US));
    }

    public static MessageHeaderProperty valueOf(String property) {
        return new MessageHeaderProperty(property.toLowerCase(Locale.US));
    }

    private String property;

    private MessageHeaderProperty(String property) {
        this.property = property;
    }

    @Override
    public String asFieldName() {
        return property;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MessageHeaderProperty) {
            MessageHeaderProperty other = (MessageHeaderProperty) obj;
            return Objects.equals(this.property, other.property);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(property);
    }

    @Override
    public String toString() {
        return Objects.toString(property);
    }
}
