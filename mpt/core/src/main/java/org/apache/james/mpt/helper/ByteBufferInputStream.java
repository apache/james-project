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

package org.apache.james.mpt.helper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;


public class ByteBufferInputStream extends InputStream {
    private final ByteBuffer buffer = ByteBuffer.allocate(160384);

    private final CharsetEncoder encoder = StandardCharsets.US_ASCII.newEncoder();

    private boolean readLast = true;

    @Override
    public int read() throws IOException {
        if (!readLast) {
            readLast = true;
            buffer.flip();
        }
        int result = -1;
        if (buffer.hasRemaining()) {
            result = buffer.get();
        }
        return result;
    }

    public void nextLine(String line) {
        if (buffer.position() > 0 && readLast) {
            buffer.compact();
        }
        encoder.encode(CharBuffer.wrap(line), buffer, true);
        buffer.put((byte) '\r');
        buffer.put((byte) '\n');
        readLast = false;
    }
}
