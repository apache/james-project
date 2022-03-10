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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.ImapDecoder;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.protocols.netty.LineHandlerAware;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.ByteToMessageDecoder;


/**
 * {@link ByteToMessageDecoder} which will decode via and {@link ImapDecoder} instance
 */
public class ImapRequestFrameDecoder extends ByteToMessageDecoder implements NettyConstants, LineHandlerAware {
    @VisibleForTesting
    static final String NEEDED_DATA = "NEEDED_DATA";
    private static final String STORED_DATA = "STORED_DATA";
    private static final String WRITTEN_DATA = "WRITTEN_DATA";
    private static final String OUTPUT_STREAM = "OUTPUT_STREAM";

    private final ImapDecoder decoder;
    private final int inMemorySizeLimit;
    private final int literalSizeLimit;
    private final Deque<ChannelInboundHandlerAdapter> behaviourOverrides = new ConcurrentLinkedDeque<>();
    private final int maxFrameLength;

    public ImapRequestFrameDecoder(ImapDecoder decoder, int inMemorySizeLimit, int literalSizeLimit, int maxFrameLength) {
        this.decoder = decoder;
        this.inMemorySizeLimit = inMemorySizeLimit;
        this.literalSizeLimit = literalSizeLimit;
        this.maxFrameLength = maxFrameLength;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().attr(FRAME_DECODE_ATTACHMENT_ATTRIBUTE_KEY).set(new HashMap<>());
        super.channelActive(ctx);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        ChannelInboundHandlerAdapter override = Iterables.getFirst(behaviourOverrides, null);
        if (override != null) {
            override.channelRead(ctx, in);
            return;
        }

        in.markReaderIndex();
        boolean retry = false;

        ImapRequestLineReader reader;
        // check if we failed before and if we already know how much data we
        // need to sucess next run
        Map<String, Object> attachment = ctx.channel().attr(FRAME_DECODE_ATTACHMENT_ATTRIBUTE_KEY).get();
        int size = -1;
        if (attachment.containsKey(NEEDED_DATA)) {
            retry = true;
            size = (Integer) attachment.get(NEEDED_DATA);
            // now see if the buffer hold enough data to process.
            if (size != NettyImapRequestLineReader.NotEnoughDataException.UNKNOWN_SIZE && size > in.readableBytes()) {

                // check if we have a inMemorySize limit and if so if the
                // expected size will fit into it
                if (inMemorySizeLimit > 0 && inMemorySizeLimit < size) {

                    // ok seems like it will not fit in the memory limit so we
                    // need to store it in a temporary file
                    final File f;
                    int written;

                    OutputStream outputStream;
                    // check if we have created a temporary file already or if
                    // we need to create a new one
                    if (attachment.containsKey(STORED_DATA)) {
                        f = (File) attachment.get(STORED_DATA);
                        written = (Integer) attachment.get(WRITTEN_DATA);
                        outputStream = (OutputStream) attachment.get(OUTPUT_STREAM);
                    } else {
                        f = File.createTempFile("imap-literal", ".tmp");
                        attachment.put(STORED_DATA, f);
                        written = 0;
                        attachment.put(WRITTEN_DATA, written);
                        outputStream = new FileOutputStream(f, true);
                        attachment.put(OUTPUT_STREAM, outputStream);

                    }


                    try {
                        int amount = Math.min(in.readableBytes(), size - written);
                        in.readBytes(outputStream, amount);
                        written += amount;
                    } catch (Exception e) {
                        try {
                            outputStream.close();
                        } catch (IOException ignored) {
                            //ignore exception during close
                        }
                        throw e;
                    }
                    // Check if all needed data was streamed to the file.
                    if (written == size) {
                        try {
                            outputStream.close();
                        } catch (IOException ignored) {
                            //ignore exception during close
                        }

                        reader = new NettyStreamImapRequestLineReader(ctx.channel(), f, retry);
                    } else {
                        attachment.put(WRITTEN_DATA, written);
                        return;
                    }

                } else {
                    in.resetReaderIndex();
                    return;
                }

            } else {

                reader = new NettyImapRequestLineReader(ctx.channel(), in, retry, literalSizeLimit);
            }
        } else {
            reader = new NettyImapRequestLineReader(ctx.channel(), in, retry, literalSizeLimit);
        }

        ImapSession session = ctx.channel().attr(IMAP_SESSION_ATTRIBUTE_KEY).get();

        // check if the session was removed before to prevent a harmless NPE. See JAMES-1312
        // Also check if the session was logged out if so there is not need to try to decode it. See JAMES-1341
        if (session != null && session.getState() != ImapSessionState.LOGOUT) {
            try {

                ImapMessage message = decoder.decode(reader, session);

                // if size is != -1 the case was a literal. if thats the case we
                // should not consume the line
                // See JAMES-1199
                if (size == -1) {
                    reader.consumeLine();
                }
                
                enableFraming(ctx);
                
                attachment.clear();
                out.add(message);
            } catch (NettyImapRequestLineReader.NotEnoughDataException e) {
                // this exception was thrown because we don't have enough data
                // yet
                int neededData = e.getNeededSize();
                // store the needed data size for later usage
                attachment.put(NEEDED_DATA, neededData);

                // SwitchableDelimiterBasedFrameDecoder added further to JAMES-1436.
                disableFraming(ctx);
                if (in.readableBytes() > 0) {
                    ByteBuf spareBytes = in.retainedDuplicate();
                    internalBuffer().clear();
                    ctx.fireChannelRead(spareBytes);
                }
                in.resetReaderIndex();
            }
        } else {
            // The session was null so may be the case because the channel was already closed but there were still bytes in the buffer.
            // We now try to disconnect the client if still connected
            if (ctx.channel().isActive()) {
                ctx.channel().writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
        }
    }

    public void disableFraming(ChannelHandlerContext ctx) {
        ctx.channel().config().setAutoRead(false);
        ctx.channel().eventLoop().execute(() -> ctx.channel().pipeline().remove(FRAMER));
        ctx.channel().config().setAutoRead(true);
    }

    public void enableFraming(ChannelHandlerContext ctx) {
        if (ctx.channel().pipeline().get(FRAMER) == null) {
            ctx.channel().config().setAutoRead(false);
            ctx.channel().eventLoop().execute(() ->
                ctx.channel().pipeline().addBefore(REQUEST_DECODER, FRAMER,
                        new SwitchableLineBasedFrameDecoder(ctx.channel().pipeline(), maxFrameLength, false)));
            ctx.channel().config().setAutoRead(true);
        }
    }

    @Override
    public void pushLineHandler(ChannelInboundHandlerAdapter lineHandlerUpstreamHandler) {
        behaviourOverrides.addFirst(lineHandlerUpstreamHandler);
    }

    @Override
    public void popLineHandler() {
        if (!behaviourOverrides.isEmpty()) {
            behaviourOverrides.removeFirst();
        }
    }
}
