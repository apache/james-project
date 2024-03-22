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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.util.ReactorUtils;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * IMAP needs to know the size of the content before it starts to write it out.
 * This interface allows direct writing whilst exposing total size.
 */
public interface Content {
    int BUFFER_SIZE = 16384;

    /**
     * Return the content as {@link InputStream}
     */
    InputStream getInputStream() throws IOException;

    default Optional<byte[][]> asBytesSequence() {
        return Optional.empty();
    }

    /**
     * Size (in octets) of the content.
     * 
     * @return number of octets to be written
     */
    long size() throws MailboxException;

    default Publisher<ByteBuffer> reactiveBytes() {
        try {
            return ReactorUtils.toChunks(getInputStream(), BUFFER_SIZE)
                .subscribeOn(Schedulers.boundedElastic());
        } catch (IOException e) {
            return Flux.error(e);
        }
    }
}