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

package org.apache.james.mailbox.model;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;

public final class HeaderAndBodyByteContent implements Content {

    private final byte[] headers;
    private final byte[] body;

    private final long size;

    public HeaderAndBodyByteContent(byte[] headers, byte[] body) {
        this.headers = headers;
        this.body = body;
        size = (long) headers.length + (long) body.length;
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public InputStream getInputStream() {
        return new SequenceInputStream(
            new ByteArrayInputStream(headers),
            new ByteArrayInputStream(body));
    }

    @Override
    public Optional<byte[][]> asBytesSequence() {
        byte[][] answer = new byte[2][];
        answer[0] = headers;
        answer[1] = body;
        return Optional.of(answer);
    }

    @Override
    public Publisher<ByteBuffer> reactiveBytes() {
        return Flux.concat(Flux.just(headers).map(ByteBuffer::wrap), Flux.just(body).map(ByteBuffer::wrap));
    }
}
