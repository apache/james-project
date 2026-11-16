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
package org.apache.james.imapserver.netty;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import org.apache.james.imap.decode.ImapRequestLineReader;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;

public abstract class AbstractNettyImapRequestLineReader extends ImapRequestLineReader implements Closeable {
    private static final Supplier<ByteBuf> CONTINUATION_REQUEST = () -> Unpooled.wrappedUnmodifiableBuffer(Unpooled.wrappedBuffer("+ Ok\r\n".getBytes(StandardCharsets.US_ASCII)));

    private final Channel channel;
    // Continuation requests already sent for this command by the previous parse attempts
    private final int continuationRequestsAlreadySent;
    private int continuationRequests = 0;

    public AbstractNettyImapRequestLineReader(Channel channel, boolean retry) {
        this(channel, retry ? Integer.MAX_VALUE : 0);
    }

    public AbstractNettyImapRequestLineReader(Channel channel, int continuationRequestsAlreadySent) {
        this.channel = channel;
        this.continuationRequestsAlreadySent = continuationRequestsAlreadySent;
    }

    @Override
    protected void commandContinuationRequest() {
        // A command is parsed again from its start once more data arrived: only invite the client to send
        // the synchronizing literals it was not invited to send yet (RFC 9051 section 7.5)
        continuationRequests++;
        if (continuationRequests > continuationRequestsAlreadySent) {
            channel.writeAndFlush(CONTINUATION_REQUEST.get());
        }
    }

    /**
     * @return the number of synchronizing literals the client was invited to send for this command so far
     */
    public int continuationRequests() {
        return Math.max(continuationRequests, continuationRequestsAlreadySent);
    }

    @Override
    public void close() throws IOException {

    }
}
