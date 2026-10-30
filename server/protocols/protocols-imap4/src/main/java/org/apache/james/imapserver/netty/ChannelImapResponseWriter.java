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

import static org.apache.james.imapserver.netty.ImapChannelUpstreamHandler.retrieveUsername;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;

import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.encode.ImapResponseWriter;
import org.apache.james.imap.message.Literal;
import org.apache.james.imap.message.SequencedLiteral;
import org.apache.james.util.MDCStructuredLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.compression.ZlibEncoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedNioFile;
import io.netty.handler.stream.ChunkedStream;
import io.netty.util.ReferenceCountUtil;

/**
 * {@link ImapResponseWriter} implementation which writes the data to a
 * {@link Channel}
 */
public class ChannelImapResponseWriter implements ImapResponseWriter {
    @FunctionalInterface
    interface FlushCallback {
        void run() throws IOException;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ChannelImapResponseWriter.class);
    private final Channel channel;
    private final boolean zeroCopy;
    private FlushCallback flushCallback;
    private ImapSession imapSession = null;

    public ChannelImapResponseWriter(Channel channel, ImapSession imapSession) {
        this(channel);
        this.imapSession = imapSession;
    }

    public ChannelImapResponseWriter(Channel channel) {
        this(channel, true);
    }

    public ChannelImapResponseWriter(Channel channel, boolean zeroCopy) {
        this.channel = channel;
        this.zeroCopy = zeroCopy;
        this.flushCallback = () -> {

        };
    }

    public void setFlushCallback(FlushCallback flushCallback) {
        this.flushCallback = flushCallback;
    }

    @Override
    public void write(byte[] buffer) {
        if (LOGGER.isTraceEnabled()) {
            if (imapSession != null) {
                new MDCStructuredLogger(LOGGER)
                        .field("username", retrieveUsername(imapSession))
                        .log(logger -> logger.trace("Writing IMAP response: {}", new String(buffer)));
            }
        }
        if (channel.isActive()) {
            writeInOrder(List.of(Unpooled.wrappedBuffer(buffer)));
        }
    }

    @Override
    public void write(Literal literal) throws IOException {
        flushCallback.run();
        if (channel.isActive()) {
            // A SequencedLiteral's parts are written in one pass and flushed once, each keeping its best representation.
            List<Object> messages = new ArrayList<>();
            if (literal instanceof SequencedLiteral sequencedLiteral) {
                for (Literal part : sequencedLiteral.parts()) {
                    messages.add(asMessage(part));
                }
            } else {
                messages.add(asMessage(literal));
            }
            writeInOrder(messages);
        }
    }

    /**
     * Netty keeps the order of the writes issued by a given thread only: a write issued outside of the
     * event loop is queued as a task, that a write issued later from the event loop overtakes. The tagged
     * response of a command completing on the event loop could then reach the client before the untagged
     * responses written while processing it.
     *
     * Outside of the event loop, wait for the write to have entered the pipeline, so that nothing written
     * afterwards, whatever the thread and the path, can overtake it.
     */
    private void writeInOrder(List<Object> messages) {
        EventLoop eventLoop = channel.eventLoop();
        if (eventLoop.inEventLoop()) {
            writeAndFlush(messages);
            return;
        }
        try {
            eventLoop.submit(() -> writeAndFlush(messages)).syncUninterruptibly();
        } catch (RejectedExecutionException e) {
            messages.forEach(ReferenceCountUtil::release);
        }
    }

    private void writeAndFlush(List<Object> messages) {
        messages.forEach(channel::write);
        channel.flush();
    }

    private Object asMessage(Literal literal) throws IOException {
        Optional<byte[][]> bytesSequence = literal.asBytesSequence();
        if (bytesSequence.isPresent()) {
            return Unpooled.wrappedBuffer(bytesSequence.get());
        }
        InputStream in = literal.getInputStream();
        if (in instanceof FileInputStream) {
            FileChannel fc = ((FileInputStream) in).getChannel();
            // Zero-copy is only possible if no SSL/TLS  and no COMPRESS is in place
            //
            // See JAMES-1305 and JAMES-1306
            ChannelPipeline cp = channel.pipeline();
            if (zeroCopy && cp.get(SslHandler.class) == null && cp.get(ZlibEncoder.class) == null) {
                return new DefaultFileRegion(fc, fc.position(), literal.size());
            }
            return new ChunkedNioFile(fc, 8192);
        }
        return new ChunkedStream(in);
    }
    
    public void flush() throws IOException {
        flushCallback.run();
        channel.flush();
    }

}
