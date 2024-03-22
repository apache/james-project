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

package org.apache.james.imap.message;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;

public class BytesBackedLiteral implements Literal {
    public static BytesBackedLiteral copy(InputStream stream) throws IOException {
        UnsynchronizedByteArrayOutputStream out = new UnsynchronizedByteArrayOutputStream();
        stream.transferTo(out);
        return of(out.toByteArray());
    }

    public static BytesBackedLiteral copy(InputStream stream, int size) throws IOException {
        byte[] buffer = IOUtils.toByteArray(stream, size);
        if (stream.read() != -1) {
            throw new IOException("Got a stream of the wrong size...");
        }
        return of(buffer);
    }

    public static BytesBackedLiteral of(byte[] bytes) {
        return new BytesBackedLiteral(bytes);
    }

    private final byte[] content;

    private BytesBackedLiteral(byte[] content) {
        this.content = content;
    }

    @Override
    public long size() {
        return content.length;
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }

    @Override
    public Optional<byte[][]> asBytesSequence() {
        byte[][] answer = new byte[1][];
        answer[0] = content;
        return Optional.of(answer);
    }
}
