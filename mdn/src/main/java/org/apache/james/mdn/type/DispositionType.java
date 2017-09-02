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

package org.apache.james.mdn.type;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Interface <code>DispositionType</code> marks a type encapsulating
 * disposition type information as defined by RFC 8098.
 *
 * https://tools.ietf.org/html/rfc8098#section-3.2.6.2
 */
public enum DispositionType {
    Deleted("deleted"),
    Dispatched("dispatched"),
    Displayed("displayed"),
    Processed("processed");

    public static Optional<DispositionType> fromString(String value) {
        return Stream.of(values())
            .filter(sendingMode -> sendingMode.getValue().equalsIgnoreCase(value))
            .findFirst();
    }

    private final String value;

    DispositionType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
