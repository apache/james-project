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

/**
 * 
 */
package org.apache.james.mailbox.model;

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * A header.
 */
public final class Header implements Content {
    private final String name;
    private final String value;
    private final long size;

    public Header(String name, String value) {
        this.name = name;
        this.value = value;
        this.size = name.length() + value.length() + 2;
    }

    /**
     * Gets the name of this header.
     *
     * @return name of this header
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the (unparsed) value of this header.
     *
     * @return value of this header
     */
    public String getValue() {
        return value;
    }

    @Override
    public long size() {
        return size;
    }

    public String toString() {
        return "[HEADER " + name + ": " + value + "]";
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream((name + ": " + value).getBytes(US_ASCII));
    }
}