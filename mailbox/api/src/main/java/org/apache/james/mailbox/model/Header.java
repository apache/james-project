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

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.US_ASCII;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;

/**
 * A header.
 */
public final class Header implements Content {
    private static final byte[] NAME_DELIMITER = ": ".getBytes(US_ASCII);

    private final String name;
    private final String value;
    private final byte[] valueBytes;
    private final long size;

    /**
     * @param value decoded byte per byte (ISO-8859-1), as mime4j does
     */
    public Header(String name, String value) {
        this(name, value, value.getBytes(ISO_8859_1));
    }

    /**
     * @param valueBytes the value as stored in the message: raw 8-bit or UTF-8 (RFC 6532) header values are written
     *                   back unchanged
     */
    public Header(String name, String value, byte[] valueBytes) {
        this.name = name;
        this.value = value;
        this.valueBytes = valueBytes;
        this.size = name.length() + valueBytes.length + 2;
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

    /**
     * @return the bytes of the value, as stored in the message
     */
    public byte[] getValueBytes() {
        return valueBytes.clone();
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
        return new ByteArrayInputStream(asBytes());
    }

    @Override
    public Publisher<ByteBuffer> reactiveBytes() {
        return Flux.just(asBytes())
            .map(ByteBuffer::wrap);
    }

    private byte[] asBytes() {
        byte[] nameBytes = name.getBytes(US_ASCII);
        byte[] result = new byte[nameBytes.length + NAME_DELIMITER.length + valueBytes.length];
        System.arraycopy(nameBytes, 0, result, 0, nameBytes.length);
        System.arraycopy(NAME_DELIMITER, 0, result, nameBytes.length, NAME_DELIMITER.length);
        System.arraycopy(valueBytes, 0, result, nameBytes.length + NAME_DELIMITER.length, valueBytes.length);
        return result;
    }
}
