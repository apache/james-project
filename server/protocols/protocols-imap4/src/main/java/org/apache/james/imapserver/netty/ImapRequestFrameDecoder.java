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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.ImapDecoder;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.handler.codec.frame.FrameDecoder;

import com.google.common.annotations.VisibleForTesting;

/**
 * {@link FrameDecoder} which will decode via and {@link ImapDecoder} instance
 */
public class ImapRequestFrameDecoder extends FrameDecoder implements NettyConstants {

    private final ImapDecoder decoder;
    private final int inMemorySizeLimit;
    private final int literalSizeLimit;
    @VisibleForTesting
    static final String NEEDED_DATA = "NEEDED_DATA";
    private static final String STORED_DATA = "STORED_DATA";
    private static final String WRITTEN_DATA = "WRITTEN_DATA";
    private static final String OUTPUT_STREAM = "OUTPUT_STREAM";

    public ImapRequestFrameDecoder(ImapDecoder decoder, int inMemorySizeLimit, int literalSizeLimit) {
        this.decoder = decoder;
        this.inMemorySizeLimit = inMemorySizeLimit;
        this.literalSizeLimit = literalSizeLimit;
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ctx.setAttachment(new HashMap<String, Object>());
        super.channelOpen(ctx, e);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer) throws Exception {
        buffer.markReaderIndex();
        boolean retry = false;

        ImapRequestLineReader reader;
        // check if we failed before and if we already know how much data we
        // need to sucess next run
        Map<String, Object> attachment = (Map<String, Object>) ctx.getAttachment();
        int size = -1;
        if (attachment.containsKey(NEEDED_DATA)) {
            retry = true;
            size = (Integer) attachment.get(NEEDED_DATA);
            // now see if the buffer hold enough data to process.
            if (size != NettyImapRequestLineReader.NotEnoughDataException.UNKNOWN_SIZE && size > buffer.readableBytes()) {

                // check if we have a inMemorySize limit and if so if the
                // expected size will fit into it
                if (inMemorySizeLimit > 0 && inMemorySizeLimit < size) {

                    // ok seems like it will not fit in the memory limit so we
                    // need to store it in a temporary file
                    final File f;
                    int written;

                    OutputStream out;
                    // check if we have created a temporary file already or if
                    // we need to create a new one
                    if (attachment.containsKey(STORED_DATA)) {
                        f = (File) attachment.get(STORED_DATA);
                        written = (Integer) attachment.get(WRITTEN_DATA);
                        out = (OutputStream) attachment.get(OUTPUT_STREAM);
                    } else {
                        f = File.createTempFile("imap-literal", ".tmp");
                        attachment.put(STORED_DATA, f);
                        written = 0;
                        attachment.put(WRITTEN_DATA, written);
                        out = new FileOutputStream(f, true);
                        attachment.put(OUTPUT_STREAM, out);

                    }


                    try {
                        int amount = Math.min(buffer.readableBytes(), size - written);
                        buffer.readBytes(out, amount);
                        written += amount;
                    } catch (Exception e) {
                        try {
                            out.close();
                        } catch (IOException ignored) {
                            //ignore exception during close
                        }
                        throw e;
                    }
                    // Check if all needed data was streamed to the file.
                    if (written == size) {
                        try {
                            out.close();
                        } catch (IOException ignored) {
                            //ignore exception during close
                        }

                        reader = new NettyStreamImapRequestLineReader(channel, new FileInputStream(f) {
                            /**
                             * Delete the File on close too
                             */
                            @Override
                            public void close() throws IOException {
                                try {
                                    super.close();
                                } finally {
                                    f.delete();
                                }
                            }

                        }, retry);
                    } else {
                        attachment.put(WRITTEN_DATA, written);
                        return null;
                    }

                } else {
                    buffer.resetReaderIndex();
                    return null;
                }

            } else {

                reader = new NettyImapRequestLineReader(channel, buffer, retry, literalSizeLimit);
            }
        } else {
            reader = new NettyImapRequestLineReader(channel, buffer, retry, literalSizeLimit);
        }

        ImapSession session = (ImapSession) attributes.get(channel);

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
                
                // Code portion commented further to JAMES-1436.
                // TODO Remove if no negative feedback on JAMES-1436.
                //ChannelHandler handler = (ChannelHandler) attachment.remove(FRAMER);
                //if (handler != null) {
                //    channel.getPipeline().addFirst(FRAMER, handler);
                //}
                
                ((SwitchableLineBasedFrameDecoder) channel.getPipeline().get(FRAMER)).enableFraming();
                
                attachment.clear();
                return message;
            } catch (NettyImapRequestLineReader.NotEnoughDataException e) {
                // this exception was thrown because we don't have enough data
                // yet
                int neededData = e.getNeededSize();
                // store the needed data size for later usage
                attachment.put(NEEDED_DATA, neededData);
                
                final ChannelPipeline pipeline = channel.getPipeline();
                final ChannelHandlerContext framerContext = pipeline.getContext(FRAMER);
                
                // Code portion commented further to JAMES-1436.
                // TODO Remove if no negative feedback on JAMES-1436.
                //ChannelHandler handler = channel.getPipeline().remove(FRAMER);
                //attachment.put(FRAMER, handler);

                // SwitchableDelimiterBasedFrameDecoder added further to JAMES-1436.
                final SwitchableLineBasedFrameDecoder framer = (SwitchableLineBasedFrameDecoder) pipeline.get(FRAMER);
                framer.disableFraming(framerContext);
                
                buffer.resetReaderIndex();
                return null;
            } finally {
                if (reader instanceof Closeable) {
                    try {
                        ((Closeable) reader).close();
                    } catch (IOException ignored) {
                        // Nothing to do
                    }
                }
            }
        } else {
            // The session was null so may be the case because the channel was already closed but there were still bytes in the buffer.
            // We now try to disconnect the client if still connected
            if (channel.isConnected()) {
                channel.write(ChannelBuffers.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
            return null;
        }
    }

    @Override
    protected synchronized ChannelBuffer newCumulationBuffer(ChannelHandlerContext ctx, int minimumCapacity) {
        Map<String, Object> attachment = (Map<String, Object>) ctx.getAttachment();
        Object sizeAsObject = attachment.get(NEEDED_DATA);
        if (sizeAsObject != null) {
            @SuppressWarnings("unchecked")
            int size = (Integer) sizeAsObject;

            if (size > 0) {
                int sanitizedInMemorySizeLimit = Math.max(0, inMemorySizeLimit);
                int sanitizedSize = Math.min(sanitizedInMemorySizeLimit, size);

                return ChannelBuffers.dynamicBuffer(sanitizedSize, ctx.getChannel().getConfig().getBufferFactory());
            }
        }
        return super.newCumulationBuffer(ctx, minimumCapacity);
    }

}
