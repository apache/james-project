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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.store.mail.model.Message;
import org.reactivestreams.Publisher;

/**
 * {@link Content} which is stored in a {@link InputStream}
 */
public final class InputStreamContent implements Content {

    private final Message m;
    private final Type type;

    public enum Type {
        FULL,
        BODY
    }
    
    public InputStreamContent(Message m, Type type) throws IOException {
        this.m = m;
        this.type = type;
    }
    
    @Override
    public long size() {
        switch (type) {
        case FULL:
            return m.getFullContentOctets();

        default:
            return m.getBodyOctets();
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        // wrap the streams in a BoundedInputStream to make sure it really match with the stored size.
        switch (type) {
        case FULL:
            return m.getFullContent();
        default:
            return m.getBodyContent();
        }
    }

    @Override
    public Optional<byte[][]> asBytesSequence() {
        switch (type) {
            case FULL:
                return m.getFullBytes();
            default:
                return m.getBodyBytes();
        }
    }

    @Override
    public Publisher<ByteBuffer> reactiveBytes() {
        switch (type) {
            case FULL:
                return m.getFullContentReactive();
            default:
                return m.getBodyContentReactive();
        }
    }
}
