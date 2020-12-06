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

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;

import org.apache.james.mpt.api.Continuation;

public class ByteBufferOutputStream extends OutputStream {

    private final ByteBuffer buffer = ByteBuffer.allocate(160384);
    private final Continuation continuation;
    private boolean matchPlus = false;
    private boolean matchCR = false;
    private boolean matchLF = false;

    public ByteBufferOutputStream(Continuation continuation) {
        this.continuation = continuation;
    }

    public void write(String message) throws IOException {
        US_ASCII.newEncoder().encode(CharBuffer.wrap(message), buffer, true);
    }

    @Override
    public void write(int b) throws IOException {
        buffer.put((byte) b);
        if (b == '\n' && matchPlus && matchCR && matchLF) {
            matchPlus = false;
            matchCR = false;
            matchLF = false;
            continuation.doContinue();
        } else if (b == '\n') {
            matchLF = true;
            matchPlus = false;
            matchCR = false;
        } else if (b == '+' && matchLF) {
            matchPlus = true;
            matchCR = false;
        } else if (b == '\r' && matchPlus && matchLF) {
            matchCR = true;
        } else {
            matchPlus = false;
            matchCR = false;
            matchLF = false;
        }
    }

    public String nextLine() throws Exception {
        buffer.flip();
        byte last = 0;
        while (buffer.hasRemaining()) {
            byte next = buffer.get();
            if (last == '\r' && next == '\n') {
                break;
            }
            last = next;
        }
        final ByteBuffer readOnlyBuffer = buffer.asReadOnlyBuffer();
        readOnlyBuffer.flip();
        int limit = readOnlyBuffer.limit() - 2;
        if (limit < 0) {
            limit = 0;
        }
        readOnlyBuffer.limit(limit);
        String result = US_ASCII.decode(readOnlyBuffer).toString();
        buffer.compact();
        return result;
    }
}
