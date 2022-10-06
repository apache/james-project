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
package org.apache.james.mailbox.store.streaming;

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.Header;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Abstract base class for {@link Content} implementations which hold the headers and 
 * the body a email
 */
public class FullByteContent implements Content {
    private static final String NAME_DELIMITER = ": ";
    private static final byte[] NAME_DELIMITER_BYTES = NAME_DELIMITER.getBytes(US_ASCII);
    private static final String END_OF_LINE = "\r\n";
    private static final byte[] END_OF_LINE_BYTES = END_OF_LINE.getBytes(US_ASCII);

    private final List<Header> headers;
    private final byte[] body;
    private final long size;
    
    public FullByteContent(byte[] body, List<Header> headers) {
        this.headers = headers;
        this.body = body;
        this.size = computeSize();
    }
    
    protected long computeSize() {
        long result = body.length;
        result += 2;
        for (Header header : headers) {
            if (header != null) {
                result += header.size();
                result += 2;
            }
        }
        return result;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        UnsynchronizedByteArrayOutputStream out = new UnsynchronizedByteArrayOutputStream((int) size);
        for (Header header : headers) {
            if (header != null) {
                out.write(header.getName().getBytes(StandardCharsets.US_ASCII));
                out.write(NAME_DELIMITER_BYTES);
                out.write(header.getValue().getBytes(StandardCharsets.US_ASCII));
                out.write(END_OF_LINE_BYTES);
            }
        }
        out.write(END_OF_LINE_BYTES);
        return new SequenceInputStream(out.toInputStream(), new ByteArrayInputStream(body));
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public Publisher<ByteBuffer> reactiveBytes() {
        Flux<ByteBuffer> headerContent = Mono.fromCallable(() -> {
                StringBuilder sb = new StringBuilder();
                for (Header header : headers) {
                    sb.append(header.getName()).append(NAME_DELIMITER).append(header.getValue()).append(END_OF_LINE);
                }
                sb.append(END_OF_LINE);
                return sb.toString().getBytes(US_ASCII);
            })
            .flatMapMany(bytes -> Flux.just(bytes).map(ByteBuffer::wrap));

        Flux<ByteBuffer> bodyContent = Flux.just(body).map(ByteBuffer::wrap);

        return Flux.concat(headerContent, bodyContent);
    }
}
