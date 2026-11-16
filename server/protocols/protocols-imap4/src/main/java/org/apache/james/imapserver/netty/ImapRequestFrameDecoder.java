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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapDecoder;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.lifecycle.api.Disposable.LeakAware;
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
    static final String CONTINUATION_REQUESTS_SENT = "CONTINUATION_REQUESTS_SENT";
    private static final boolean RETRY = true;
    private static final String TRAILING_EOL_SIZE = "TRAILING_EOL_SIZE";
    private static final String SINK = "SINK";
    private static final int REJECTED_LINE_TAIL_SIZE = 32;
    private static final Pattern NON_SYNCHRONIZING_LITERAL_AT_END_OF_LINE = Pattern.compile("~?\\{(\\d+)\\+}\r?\n$");
    private static final String SUBSCRIPTION = "SUBSCRIPTION";
    public static final int UNAUTHENTICATE_LITERAL_MAX_SIZE = Optional.ofNullable(System.getProperty("james.imap.unauthenticated.literal.max.size"))
        .map(Integer::parseInt)
        .orElse(8192);

    private final ImapDecoder decoder;
    private final int inMemorySizeLimit;
    private final int literalSizeLimit;
    private final Deque<ChannelInboundHandlerAdapter> behaviourOverrides = new ConcurrentLinkedDeque<>();
    private final int maxFrameLength;
    private final AtomicBoolean framingEnabled = new AtomicBoolean(true);
    private ArrayList<byte[]> pending = new ArrayList<>();
    private boolean discardUntilEndOfLine = false;
    // Content of the literals of a rejected command, still to be skipped (RFC 7888 section 4)
    private long literalBytesToDiscard = 0;
    // Last bytes of the rejected line being discarded, to spot a literal announced at its end
    private byte[] rejectedLineTail = new byte[0];
    private final AtomicBoolean awaitingFileBackedLiteral = new AtomicBoolean(false);

    public ImapRequestFrameDecoder(ImapDecoder decoder, int inMemorySizeLimit, int literalSizeLimit, int maxFrameLength) {
        this.decoder = decoder;
        this.inMemorySizeLimit = inMemorySizeLimit;
        this.literalSizeLimit = literalSizeLimit;
        this.maxFrameLength = maxFrameLength;
    }

    private int literalSizeLimit(ImapSession session) {
        if (session == null) {
            return UNAUTHENTICATE_LITERAL_MAX_SIZE;
        }
        return switch (session.getState()) {
            case NON_AUTHENTICATED, LOGOUT -> UNAUTHENTICATE_LITERAL_MAX_SIZE;
            case AUTHENTICATED, SELECTED -> literalSizeLimit;
        };
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().attr(FRAME_DECODE_ATTACHMENT_ATTRIBUTE_KEY).set(new ConcurrentHashMap<>());
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

        if (literalBytesToDiscard > 0) {
            discardLiteralOfRejectedCommand(ctx, in);
            return;
        }

        if (discardUntilEndOfLine) {
            discardRestOfRejectedLine(ctx, in);
            return;
        }

        if (awaitingFileBackedLiteral.get()) {
            // What follows a literal spooled to a file waits for that command to be decoded,
            // so that commands keep their order. See resumeAfterFileBackedLiteral.
            return;
        }

        int readerIndex = in.readerIndex();

        Map<String, Object> attachment = ctx.channel().attr(FRAME_DECODE_ATTACHMENT_ATTRIBUTE_KEY).get();

        ParseAttempt attempt = obtainReader(ctx, in, attachment, readerIndex);
        if (attempt == null) {
            return;
        }

        int readableBytes = in.readableBytes();
        Optional<ImapMessage> message = parseImapMessage(ctx, in, attachment, attempt, readerIndex);
        if (message.isPresent() && in.readableBytes() == readableBytes) {
            // The command ended within `pending`, before reaching `in`. Netty forbids decoding a
            // message without reading anything, so hand it over directly, then decode what follows:
            // the rest of `pending` first, then `in`.
            ctx.fireChannelRead(message.get());
            if (!ctx.isRemoved()) {
                decode(ctx, in, out);
            }
            return;
        }
        message.ifPresent(out::add);
    }

    /**
     * One parse round: {@code reader} reads from {@code buffer}, whose first {@code pendingSize}
     * bytes come from {@link #pending} and whose tail is a view of Netty's cumulation.
     * {@code buffer} is null when the reader is backed by a file rather than by memory.
     */
    private record ParseAttempt(ImapRequestLineReader reader, int size, ByteBuf buffer, int pendingSize) {
    }

    private Optional<ImapMessage> parseImapMessage(ChannelHandlerContext ctx, ByteBuf in, Map<String, Object> attachment, ParseAttempt attempt, int readerIndex) throws IOException {
        ImapSession session = ctx.channel().attr(IMAP_SESSION_ATTRIBUTE_KEY).get();

        // check if the session was removed before to prevent a harmless NPE. See JAMES-1312
        // Also check if the session was logged out if so there is not need to try to decode it. See JAMES-1341
        if (session != null && session.getState() != ImapSessionState.LOGOUT) {
            try {

                ImapMessage message = decoder.decode(attempt.reader(), session);

                // if size is != -1 the case was a literal. if thats the case we
                // should not consume the line
                // See JAMES-1199
                // A rejected command is the exception: whatever is left of its line is not a
                // command of its own, wherever the parse stopped.
                if (attempt.size() == -1 || message instanceof StatusResponse) {
                    consumeRestOfLine(session, attempt);
                }
                if (message instanceof StatusResponse && attempt.buffer() != null) {
                    // The client sends the literals of a command whatever its outcome: they are not commands
                    Optional<ImapRequestLineReader.LiteralHeader> unreadLiteral = attempt.reader().unreadLiteral()
                        .filter(ImapRequestLineReader.LiteralHeader::isSentByTheClient);
                    if (unreadLiteral.isPresent()) {
                        if (!unreadLiteral.get().sizeAccepted()) {
                            // Rather than reading a literal bigger than accepted, answer and close the connection
                            rejectAndClose(ctx, (StatusResponse) message);
                            attachment.clear();
                            commitConsumedBytes(in, attempt);
                            return Optional.empty();
                        }
                        discardLiteral(session, unreadLiteral.get().size());
                    }
                }

                if (literalBytesToDiscard > 0) {
                    // Literal content is not split into lines
                    disableFraming(ctx);
                } else {
                    enableFraming(ctx);
                }

                attachment.clear();
                commitConsumedBytes(in, attempt);
                return Optional.of(message);
            } catch (NettyImapRequestLineReader.NotEnoughDataException e) {
                // this exception was thrown because we don't have enough data yet
                if (attempt.reader() instanceof AbstractNettyImapRequestLineReader nettyReader) {
                    attachment.put(CONTINUATION_REQUESTS_SENT, nettyReader.continuationRequests());
                }
                requestMoreData(ctx, in, attachment, e, readerIndex);
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

    private void consumeRestOfLine(ImapSession session, ParseAttempt attempt) throws IOException {
        ByteBuf buffer = attempt.buffer();
        if (buffer == null || endOfLineReached(buffer)) {
            return;
        }
        int restOfLineStart = buffer.readerIndex();
        try {
            attempt.reader().consumeLine();
        } catch (NettyImapRequestLineReader.NotEnoughDataException e) {
            // The end of the line is still to come: drop it when it arrives, so that the commands
            // decoded do not depend on how the stream was split into segments.
            discardUntilEndOfLine = true;
        } catch (DecodingException e) {
            // The reader refuses to go further (eg the line length limit is exceeded): skip what is left
            // of the line without it, so that the outcome does not depend on what was already received.
            skipToEndOfLine(buffer);
        }
        appendToRejectedLineTail(buffer, restOfLineStart, buffer.readerIndex());
        if (!discardUntilEndOfLine) {
            onRejectedLineEnd(session);
        }
    }

    private void appendToRejectedLineTail(ByteBuf buffer, int from, int to) {
        int length = Math.min(to - from, REJECTED_LINE_TAIL_SIZE);
        byte[] appended = new byte[length];
        buffer.getBytes(to - length, appended);
        int kept = Math.min(rejectedLineTail.length, REJECTED_LINE_TAIL_SIZE - length);
        byte[] tail = new byte[kept + length];
        System.arraycopy(rejectedLineTail, rejectedLineTail.length - kept, tail, 0, kept);
        System.arraycopy(appended, 0, tail, kept, length);
        rejectedLineTail = tail;
    }

    /**
     * The skipped part of a rejected line can announce a non synchronizing literal (eg the rejected
     * command failed to parse an argument before it): the client sends its content without waiting.
     */
    private void onRejectedLineEnd(ImapSession session) throws IOException {
        Matcher matcher = NON_SYNCHRONIZING_LITERAL_AT_END_OF_LINE.matcher(new String(rejectedLineTail, StandardCharsets.US_ASCII));
        rejectedLineTail = new byte[0];
        if (matcher.find()) {
            long size;
            try {
                size = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                throw tooBigNonSynchronizingLiteral("Non synchronizing literal of " + matcher.group(1) + " bytes exceeds the literal size limit");
            }
            discardLiteral(session, size);
        }
    }

    private void rejectAndClose(ChannelHandlerContext ctx, StatusResponse response) {
        // The connection is going to be closed: nothing that follows is to be decoded
        literalBytesToDiscard = Long.MAX_VALUE;
        String command = Optional.ofNullable(response.getCommand())
            .map(imapCommand -> imapCommand.getName() + " ")
            .orElse("");
        String line = response.getTag().asString() + " BAD " + command + response.getTextKey().getDefaultValue() + "\r\n";
        ctx.channel().writeAndFlush(Unpooled.wrappedBuffer(line.getBytes(StandardCharsets.US_ASCII)))
            .addListener(ChannelFutureListener.CLOSE);
    }

    private IOException tooBigNonSynchronizingLiteral(String message) {
        // The connection is going to be closed: nothing that follows is to be decoded
        literalBytesToDiscard = Long.MAX_VALUE;
        return new IOException(message);
    }

    private void discardLiteral(ImapSession session, long size) throws IOException {
        int limit = literalSizeLimit(session);
        if (size > Integer.MAX_VALUE || (limit > 0 && size > limit)) {
            // Rather than reading it, close the connection
            throw tooBigNonSynchronizingLiteral("Non synchronizing literal of " + size + " bytes exceeds the literal size limit");
        }
        literalBytesToDiscard = size;
        if (size == 0) {
            // What follows the literal is the rest of the rejected command line
            discardUntilEndOfLine = true;
        }
    }

    private void discardLiteralOfRejectedCommand(ChannelHandlerContext ctx, ByteBuf in) {
        while (literalBytesToDiscard > 0 && !pending.isEmpty()) {
            byte[] first = pending.get(0);
            if (first.length <= literalBytesToDiscard) {
                pending.remove(0);
                literalBytesToDiscard -= first.length;
            } else {
                pending.set(0, Arrays.copyOfRange(first, (int) literalBytesToDiscard, first.length));
                literalBytesToDiscard = 0;
            }
        }
        int skipped = (int) Math.min(in.readableBytes(), literalBytesToDiscard);
        in.skipBytes(skipped);
        literalBytesToDiscard -= skipped;
        if (literalBytesToDiscard == 0) {
            // What follows the literal is the rest of the rejected command line
            discardUntilEndOfLine = true;
            enableFraming(ctx);
        }
    }

    private void skipToEndOfLine(ByteBuf buffer) {
        if (endOfLineReached(buffer)) {
            return;
        }
        int endOfLine = buffer.bytesBefore((byte) '\n');
        if (endOfLine < 0) {
            buffer.skipBytes(buffer.readableBytes());
            discardUntilEndOfLine = true;
        } else {
            buffer.skipBytes(endOfLine + 1);
        }
    }

    private boolean endOfLineReached(ByteBuf buffer) {
        return buffer.readerIndex() > 0 && buffer.getByte(buffer.readerIndex() - 1) == '\n';
    }

    private void discardRestOfRejectedLine(ChannelHandlerContext ctx, ByteBuf in) throws IOException {
        int start = in.readerIndex();
        int endOfLine = in.bytesBefore((byte) '\n');
        if (endOfLine < 0) {
            in.skipBytes(in.readableBytes());
            appendToRejectedLineTail(in, start, in.readerIndex());
        } else {
            in.skipBytes(endOfLine + 1);
            discardUntilEndOfLine = false;
            appendToRejectedLineTail(in, start, in.readerIndex());
            onRejectedLineEnd(ctx.channel().attr(IMAP_SESSION_ATTRIBUTE_KEY).get());
            if (literalBytesToDiscard > 0) {
                disableFraming(ctx);
            }
        }
    }

    /**
     * The command may not have used everything it was offered: the tail can already be the next
     * pipelined command. Advance Netty's cumulation past what was actually consumed only, so the
     * remainder is decoded as a command of its own instead of being dropped (see #3144).
     *
     * The command can also end within {@link #pending}: what it did not use from there is kept,
     * to be decoded before `in`.
     */
    private void commitConsumedBytes(ByteBuf in, ParseAttempt attempt) {
        if (in != null && attempt.buffer() != null) {
            int consumed = attempt.buffer().readerIndex();
            in.skipBytes(Math.max(0, consumed - attempt.pendingSize()));
            pending.clear();
            if (consumed < attempt.pendingSize()) {
                byte[] leftover = new byte[attempt.pendingSize() - consumed];
                attempt.buffer().getBytes(consumed, leftover);
                pending.add(leftover);
            }
        } else {
            pending.clear();
        }
    }

    private void requestMoreData(ChannelHandlerContext ctx, ByteBuf in, Map<String, Object> attachment, NettyImapRequestLineReader.NotEnoughDataException notEnoughData, int readerIndex) {
        int neededData = notEnoughData.getNeededSize();
        attachment.put(TRAILING_EOL_SIZE, notEnoughData.getTrailingEolSize());
        // Everything we could offer was not enough, so the rest has to come from the network:
        // keep all of `in` and retry the whole parse once more bytes arrive.
        in.readerIndex(readerIndex);
        byte[] bytes = new byte[in.readableBytes()];
        in.readBytes(bytes);
        pending.add(bytes);

        // NEEDED_DATA keeps its historical meaning: how many bytes must still arrive from the
        // network, beyond everything now held in `pending`. The reader that threw counted from
        // the start of `pending`, so rebase to that delta - uploadToAFile()'s completion check
        // relies on it. UNKNOWN_SIZE is a sentinel, not a count: pass it through untouched.
        if (neededData == NettyImapRequestLineReader.NotEnoughDataException.UNKNOWN_SIZE) {
            attachment.put(NEEDED_DATA, neededData);
        } else {
            attachment.put(NEEDED_DATA, neededData - pending.stream().mapToInt(b -> b.length).sum());
        }

        // SwitchableDelimiterBasedFrameDecoder added further to JAMES-1436.
        disableFraming(ctx);
    }

    private ParseAttempt obtainReader(ChannelHandlerContext ctx, ByteBuf in, Map<String, Object> attachment, int readerIndex) throws IOException {
        // check if we failed before and if we already know how much data we
        // need to sucess next run
        final Object rawSize = attachment.get(NEEDED_DATA);
        boolean retry = rawSize != null;
        int size = retry ? (Integer) rawSize : NettyImapRequestLineReader.NotEnoughDataException.UNKNOWN_SIZE;

        // check if we have a inMemorySize limit and if so if the expected size will fit into it
        if (size != NettyImapRequestLineReader.NotEnoughDataException.UNKNOWN_SIZE
            && inMemorySizeLimit > 0 && inMemorySizeLimit < size) {

            ImapSession session = ctx.channel().attr(IMAP_SESSION_ATTRIBUTE_KEY).get();
            int literalSizeLimit = literalSizeLimit(session);
            if (literalSizeLimit > 0 && size > literalSizeLimit) {
                throw new IOException("Attempt to write a too big chunk into a file. " + size + " and limit " + literalSizeLimit);
            }

            // ok seems like it will not fit in the memory limit so we
            // need to store it in a temporary file
            uploadToAFile(ctx, in, attachment, size, readerIndex);
            return null;
        }

        int pendingSize = pending.stream().mapToInt(bytes -> bytes.length).sum();
        ByteBuf buffer = pendingFollowedBy(in);
        ImapSession session = ctx.channel().attr(IMAP_SESSION_ATTRIBUTE_KEY).get();
        int continuationRequestsSent = (Integer) attachment.getOrDefault(CONTINUATION_REQUESTS_SENT, 0);
        ImapRequestLineReader reader = new NettyImapRequestLineReader(ctx.channel(), buffer, continuationRequestsSent, 0, literalSizeLimit(session), maxFrameLength);
        return new ParseAttempt(reader, size, buffer, pendingSize);
    }

    /**
     * What we kept from earlier rounds, followed by a *view* of what Netty holds now. Reading it
     * leaves `in` untouched, so a parse that fails costs nothing and a parse that succeeds can
     * hand back whatever it did not use - see {@link #commitConsumedBytes(ByteBuf, ParseAttempt)}.
     */
    private ByteBuf pendingFollowedBy(ByteBuf in) {
        ByteBuf[] parts = new ByteBuf[pending.size() + 1];
        for (int i = 0; i < pending.size(); i++) {
            parts[i] = Unpooled.wrappedBuffer(pending.get(i));
        }
        parts[pending.size()] = in.slice(in.readerIndex(), in.readableBytes());
        return Unpooled.wrappedBuffer(parts);
    }

    private void uploadToAFile(ChannelHandlerContext ctx, ByteBuf in, Map<String, Object> attachment, int size, int readerIndex) throws IOException {
        Pair<Sinks.Many<byte[]>, AtomicInteger> sink;

        // check if we have created a temporary file already or if
        // we need to create a new one
        if (attachment.containsKey(SINK)) {
            sink = (Pair<Sinks.Many<byte[]>, AtomicInteger>) attachment.get(SINK);
        } else {
            sink = Pair.of(Sinks.many().unicast().onBackpressureBuffer(), new AtomicInteger(0));
            attachment.put(SINK, sink);

            FileChunkConsumer fileChunkConsumer = new FileChunkConsumer();
            Disposable subscribe = sink.getLeft().asFlux()
                .publishOn(Schedulers.boundedElastic())
                .subscribe(fileChunkConsumer,
                    e -> {
                        fileChunkConsumer.discard();
                        ctx.fireExceptionCaught(e);
                        resumeAfterFileBackedLiteral(ctx);
                    },
                    () -> {
                        fileChunkConsumer.finalizeDataTransfer();
                        ImapRequestLineReader reader = new NettyStreamImapRequestLineReader(ctx.channel(), fileChunkConsumer.getFile(), RETRY, maxFrameLength);

                        // Remove ongoing subscription: now on lifecycle of the message will be managed by ImapChannelUpstreamHandler.
                        // Not doing this causes IDLEd IMAP connections to clear IMAP append literal while they are processed.
                        Object removed = attachment.remove(SUBSCRIPTION);
                        try {
                            parseImapMessage(ctx, null, attachment, new ParseAttempt(reader, size, null, 0), readerIndex)
                                .ifPresent(ctx::fireChannelRead);
                        } catch (Exception e) {
                            if (removed instanceof Disposable) {
                                ((Disposable) removed).dispose();
                            }
                            ctx.fireExceptionCaught(e);
                        }
                        resumeAfterFileBackedLiteral(ctx);
                    });
            attachment.put(SUBSCRIPTION, (Disposable) () -> {
                // Clear the file if the connection is reset while buffering the litteral.
                subscribe.dispose();
                fileChunkConsumer.discard();
            });

            pending.forEach(bytes -> sink.getLeft().emitNext(bytes, FAIL_FAST));
            pending.clear();
        }
        int readableBytes = in.readableBytes();
        byte[] bytes = new byte[readableBytes];
        // Only consume what belongs to the literal and its line: the rest stays in Netty's
        // cumulation, to be decoded once this command is.
        in.getBytes(in.readerIndex(), bytes);
        int received = sink.getRight().get();
        // size counts the CRLF ending the line, but it may as well be a bare LF: it can not be counted on
        int literalSize = size - (Integer) attachment.getOrDefault(TRAILING_EOL_SIZE, 0);
        int literalBytes = Math.min(readableBytes, Math.max(0, literalSize - received));
        boolean literalSpooled = received + literalBytes >= literalSize;
        int accepted = literalSpooled ? endOfLiteralLine(bytes, literalBytes) : literalBytes;
        in.skipBytes(accepted);
        sink.getLeft().emitNext(accepted == readableBytes ? bytes : Arrays.copyOf(bytes, accepted), FAIL_FAST);
        sink.getRight().addAndGet(accepted);
        boolean lineEnded = accepted > literalBytes && bytes[accepted - 1] == '\n';
        boolean endOfLineReached = accepted < readableBytes || lineEnded;
        if (literalSpooled && endOfLineReached) {
            if (!lineEnded) {
                // Malformed literal line, the command will be rejected: drop the rest of that line.
                discardUntilEndOfLine = true;
            }
            awaitingFileBackedLiteral.set(true);
            sink.getLeft().tryEmitComplete();
        }
    }

    /**
     * Decode the bytes received after a file-backed literal once its command was handed over,
     * from the event loop and after it, so that the commands keep their order.
     */
    private void resumeAfterFileBackedLiteral(ChannelHandlerContext ctx) {
        ctx.executor().execute(() -> {
            awaitingFileBackedLiteral.set(false);
            if (!ctx.isRemoved() && ctx.channel().isActive()) {
                try {
                    channelRead(ctx, Unpooled.EMPTY_BUFFER);
                } catch (Exception e) {
                    ctx.fireExceptionCaught(e);
                }
            }
        });
    }

    /**
     * Where the end of the line following a literal stops in {@code bytes}, starting at {@code length}.
     * {@link ImapRequestLineReader#eol()} tolerates spaces before the end of line, and a bare LF: keep
     * spooling them rather than cutting the line short, but nothing after, as this is the next command.
     */
    private static int endOfLiteralLine(byte[] bytes, int literalEnd) {
        int length = literalEnd;
        while (length < bytes.length && (bytes[length] == ' ' || bytes[length] == '\r')) {
            length++;
        }
        if (length < bytes.length && bytes[length] == '\n') {
            length++;
        }
        return length;
    }

    public static class FileHolderInner extends LeakAware.Resource {
        public static FileHolderInner create() throws IOException {
            return new FileHolderInner(Files.createTempFile("imap-literal", ".tmp").toFile());
        }

        private final File file;

        private FileHolderInner(File file) {
            super(() -> FileUtils.deleteQuietly(file));
            this.file = file;
        }

        public File getFile() {
            return file;
        }
    }

    public static class FileHolder extends LeakAware<FileHolderInner> {
        public static FileHolder create() throws IOException {
            return new FileHolder(FileHolderInner.create());
        }

        private final FileHolderInner file;

        private FileHolder(FileHolderInner file) {
            super(file);
            this.file = file;
        }

        public File getFile() {
            return file.file;
        }

    }

    static class FileChunkConsumer implements Consumer<byte[]> {
        private final AtomicBoolean initialized = new AtomicBoolean(false);
        private OutputStream outputStream;
        private FileHolder file;

        public FileHolder getFile() {
            return file;
        }

        @Override
        public void accept(byte[] next) {
            if (!initialized.get()) {
                initialize();
            }

            writeChunk(next);
        }

        private void initialize() {
            try {
                file = FileHolder.create();
                outputStream = new FileOutputStream(file.getFile(), true);
                initialized.set(true);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void writeChunk(byte[] next) {
            try {
                outputStream.write(next);
            } catch (Exception e) {
                try {
                    outputStream.close();
                } catch (IOException ignored) {
                    //ignore exception during close
                }
                throw new RuntimeException(e);
            }
        }


        private void finalizeDataTransfer() {
            try {
                outputStream.flush();
                outputStream.close();
            } catch (IOException ignored) {
                //ignore exception during close
            }
        }

        void discard() {
            Mono.fromRunnable(Throwing.runnable(() -> {
                if (outputStream != null) {
                    outputStream.close();
                }
                if (file != null) {
                    file.dispose();
                }
            })).subscribeOn(Schedulers.boundedElastic())
                .subscribe();
        }
    }

    public void disableFraming(ChannelHandlerContext ctx) {
        if (framingEnabled.getAndSet(false)) {
            ctx.channel().pipeline().replace(FRAMER, FRAMER, new ChannelInboundHandlerAdapter() {

            });
        }
    }

    public void enableFraming(ChannelHandlerContext ctx) {
        if (!framingEnabled.getAndSet(true)) {
            ctx.channel().pipeline().replace(FRAMER, FRAMER,
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
