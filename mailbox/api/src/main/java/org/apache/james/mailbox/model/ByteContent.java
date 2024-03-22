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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;

public final class ByteContent implements Content {

    private final byte[] contents;

    private final long size;

    public ByteContent(byte[] contents) {
        this.contents = contents;
        size = contents.length;
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(contents);
    }

    @Override
    public Publisher<ByteBuffer> reactiveBytes() {
        return Flux.just(contents)
            .map(ByteBuffer::wrap);
    }

    @Override
    public Optional<byte[][]> asBytesSequence() {
        byte[][] answer = new byte[1][];
        answer[0] = contents;
        return Optional.of(answer);
    }
}
