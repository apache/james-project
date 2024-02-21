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

import static reactor.core.publisher.Sinks.EmitFailureHandler.FAIL_FAST;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapDecoder;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.protocols.netty.LineHandlerAware;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.ByteToMessageDecoder;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;


/**
 * {@link ByteToMessageDecoder} which will decode via and {@link ImapDecoder} instance
 */
public class ImapRequestFrameDecoder extends ByteToMessageDecoder implements NettyConstants, LineHandlerAware {
    @VisibleForTesting
    static final String NEEDED_DATA = "NEEDED_DATA";
    private static final boolean RETRY = true;
    private static final String SINK = "SINK";
    private static final String SUBSCRIPTION = "SUBSCRIPTION";

    private final ImapDecoder decoder;
    private final int inMemorySizeLimit;
    private final int literalSizeLimit;
    private final Deque<ChannelInboundHandlerAdapter> behaviourOverrides = new ConcurrentLinkedDeque<>();
    private final int maxFrameLength;
    private final AtomicBoolean framingEnabled = new AtomicBoolean(true);

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
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Object subscription = ctx.channel().attr(FRAME_DECODE_ATTACHMENT_ATTRIBUTE_KEY).get()
            .get(SUBSCRIPTION);
        if (subscription instanceof Disposable) {
            ((Disposable) subscription).dispose();
        }
        super.channelInactive(ctx);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        ChannelInboundHandlerAdapter override = behaviourOverrides.peekFirst();
        if (override != null) {
            override.channelRead(ctx, in);
            return;
        }

        int readerIndex = in.readerIndex();

        Map<String, Object> attachment = ctx.channel().attr(FRAME_DECODE_ATTACHMENT_ATTRIBUTE_KEY).get();

        Pair<ImapRequestLineReader, Integer> readerAndSize = obtainReader(ctx, in, attachment, readerIndex);
        if (readerAndSize == null) {
            return;
        }

        parseImapMessage(ctx, in, attachment, readerAndSize, readerIndex)
            .ifPresent(out::add);
    }

    private Optional<ImapMessage> parseImapMessage(ChannelHandlerContext ctx, ByteBuf in, Map<String, Object> attachment, Pair<ImapRequestLineReader, Integer> readerAndSize, int readerIndex) throws DecodingException {
        ImapSession session = ctx.channel().attr(IMAP_SESSION_ATTRIBUTE_KEY).get();

        // check if the session was removed before to prevent a harmless NPE. See JAMES-1312
        // Also check if the session was logged out if so there is not need to try to decode it. See JAMES-1341
        if (session != null && session.getState() != ImapSessionState.LOGOUT) {
            try {

                ImapMessage message = decoder.decode(readerAndSize.getLeft(), session);

                // if size is != -1 the case was a literal. if thats the case we
                // should not consume the line
                // See JAMES-1199
                if (readerAndSize.getRight() == -1) {
                    readerAndSize.getLeft().consumeLine();
                }

                enableFraming(ctx);

                attachment.clear();
                return Optional.of(message);
            } catch (NettyImapRequestLineReader.NotEnoughDataException e) {
                // this exception was thrown because we don't have enough data yet
                requestMoreData(ctx, in, attachment, e.getNeededSize(), readerIndex);
            }
        } else {
            // The session was null so may be the case because the channel was already closed but there were still bytes in the buffer.
            // We now try to disconnect the client if still connected
            if (ctx.channel().isActive()) {
                ctx.channel().writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
        }
        return Optional.empty();
    }

    private void requestMoreData(ChannelHandlerContext ctx, ByteBuf in, Map<String, Object> attachment, int neededData, int readerIndex) {
        // store the needed data size for later usage
        attachment.put(NEEDED_DATA, neededData);

        // SwitchableDelimiterBasedFrameDecoder added further to JAMES-1436.
        disableFraming(ctx);
        in.readerIndex(readerIndex);
    }

    private Pair<ImapRequestLineReader, Integer> obtainReader(ChannelHandlerContext ctx, ByteBuf in, Map<String, Object> attachment, int readerIndex) throws IOException {
        boolean retry = false;
        ImapRequestLineReader reader;
        // check if we failed before and if we already know how much data we
        // need to sucess next run
        int size = -1;
        final Object rawSize = attachment.get(NEEDED_DATA);
        if (rawSize != null) {
            retry = true;
            size = (Integer) rawSize;
            // now see if the buffer hold enough data to process.
            if (size != NettyImapRequestLineReader.NotEnoughDataException.UNKNOWN_SIZE && size > in.readableBytes()) {

                // check if we have a inMemorySize limit and if so if the
                // expected size will fit into it
                if (inMemorySizeLimit > 0 && inMemorySizeLimit < size) {

                    // ok seems like it will not fit in the memory limit so we
                    // need to store it in a temporary file
                    uploadToAFile(ctx, in, attachment, size, readerIndex);
                    return null;

                } else {
                    in.resetReaderIndex();
                    return null;
                }

            } else {

                reader = new NettyImapRequestLineReader(ctx.channel(), in, retry, literalSizeLimit);
            }
        } else {
            reader = new NettyImapRequestLineReader(ctx.channel(), in, retry, literalSizeLimit);
        }
        return Pair.of(reader, size);
    }

    private void uploadToAFile(ChannelHandlerContext ctx, ByteBuf in, Map<String, Object> attachment, int size, int readerIndex) throws IOException {
        Sinks.Many<byte[]> sink;

        // check if we have created a temporary file already or if
        // we need to create a new one
        if (attachment.containsKey(SINK)) {
            sink = (Sinks.Many<byte[]>) attachment.get(SINK);
        } else {
            sink = Sinks.many().unicast().onBackpressureBuffer();
            attachment.put(SINK, sink);

            FileChunkConsumer fileChunkConsumer = new FileChunkConsumer(size,
                (file, written) -> {
                    ImapRequestLineReader reader = new NettyStreamImapRequestLineReader(ctx.channel(), file, RETRY);

                    try {
                        parseImapMessage(ctx, null, attachment, Pair.of(reader, size), readerIndex)
                            .ifPresent(message -> {
                                ctx.fireChannelRead(message);
                                // Remove ongoing subscription: now on lifecycle of the message will be managed by ImapChannelUpstreamHandler.
                                // Not doing this causes IDLEd IMAP connections to clear IMAP append literal while they are processed.
                                attachment.remove(SUBSCRIPTION);
                            });
                    } catch (DecodingException e) {
                        ctx.fireExceptionCaught(e);
                    }
                });
            Disposable subscribe = sink.asFlux()
                .publishOn(Schedulers.boundedElastic())
                .subscribe(fileChunkConsumer,
                    e -> {
                        fileChunkConsumer.discard();
                        ctx.fireExceptionCaught(e);
                    },
                    () -> {

                    });
            attachment.put(SUBSCRIPTION, (Disposable) () -> {
                // Clear the file if the connection is reset while buffering the litteral.
                subscribe.dispose();
                fileChunkConsumer.discard();
            });
        }
        int readableBytes = in.readableBytes();
        byte[] bytes = new byte[readableBytes];
        in.readBytes(bytes);
        sink.emitNext(bytes, FAIL_FAST);
    }

    static class FileChunkConsumer implements Consumer<byte[]> {
        private final int size;
        private final AtomicInteger written = new AtomicInteger(0);
        private final BiConsumer<File, Integer> callback;
        private final AtomicBoolean initialized = new AtomicBoolean(false);
        private OutputStream outputStream;
        private File f;

        FileChunkConsumer(int size, BiConsumer<File, Integer> callback) {
            this.size = size;
            this.callback = callback;
        }

        @Override
        public void accept(byte[] next) {
            if (!initialized.get()) {
                initialize();
            }

            writeChunk(next);

            // Check if all needed data was streamed to the file.
            if (isComplete()) {
                finalizeDataTransfer();
            }
        }

        private void initialize() {
            try {
                f = Files.createTempFile("imap-literal", ".tmp").toFile();
                outputStream = new FileOutputStream(f, true);
                initialized.set(true);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void writeChunk(byte[] next) {
            try {
                int amount = Math.min(next.length, size - written.get());
                outputStream.write(next, 0, amount);
                written.addAndGet(amount);
            } catch (Exception e) {
                try {
                    outputStream.close();
                } catch (IOException ignored) {
                    //ignore exception during close
                }
                throw new RuntimeException(e);
            }
        }

        private boolean isComplete() {
            return written.get() == size;
        }

        private void finalizeDataTransfer() {
            try {
                outputStream.close();
            } catch (IOException ignored) {
                //ignore exception during close
            }

            callback.accept(f, written.get());
        }

        void discard() {
            Mono.fromRunnable(Throwing.runnable(() -> {
                if (outputStream != null) {
                    outputStream.close();
                }
                if (f != null) {
                    Files.delete(f.toPath());
                }
            })).subscribeOn(Schedulers.boundedElastic())
                .subscribe();
        }
    }

    public void disableFraming(ChannelHandlerContext ctx) {
        if (framingEnabled.getAndSet(false)) {
            ctx.channel().pipeline().remove(FRAMER);
        }
    }

    public void enableFraming(ChannelHandlerContext ctx) {
        if (!framingEnabled.getAndSet(true)) {
            ctx.channel().pipeline().addBefore(REQUEST_DECODER, FRAMER,
                new SwitchableLineBasedFrameDecoder(ctx.channel().pipeline(), maxFrameLength, false));
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
