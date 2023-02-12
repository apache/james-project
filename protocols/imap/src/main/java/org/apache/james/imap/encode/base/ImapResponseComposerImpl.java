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

package org.apache.james.imap.encode.base;

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.io.IOException;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.display.ModifiedUtf7;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.UidRange;
import org.apache.james.imap.encode.ImapResponseComposer;
import org.apache.james.imap.encode.ImapResponseWriter;
import org.apache.james.imap.message.Literal;
import org.apache.james.imap.utils.FastByteArrayOutputStream;

/**
 * Class providing methods to send response messages from the server to the
 * client.
 */
public class ImapResponseComposerImpl implements ImapConstants, ImapResponseComposer {

    public static final byte[] FLAGS = "FLAGS".getBytes(US_ASCII);

    private static final int LOWER_CASE_OFFSET = 'a' - 'A';
    public static final int DEFAULT_BUFFER_SIZE = 256;
    private static final byte[] SEEN = "\\Seen".getBytes(US_ASCII);
    private static final byte[] RECENT = "\\Recent".getBytes(US_ASCII);
    private static final byte[] FLAGGED = "\\Flagged".getBytes(US_ASCII);
    private static final byte[] DRAFT = "\\Draft".getBytes(US_ASCII);
    private static final byte[] DELETED = "\\Deleted".getBytes(US_ASCII);
    private static final byte[] ANSWERED = "\\Answered".getBytes(US_ASCII);
    private static final int FLUSH_BUFFER_SIZE = Optional.ofNullable(System.getProperty("james.imap.flush.buffer.size"))
        .map(Integer::parseInt)
        .orElse(8192);
    private static final byte[] CONTINUATION_BYTES = "+\r\n".getBytes(US_ASCII);

    private final ImapResponseWriter writer;

    private final FastByteArrayOutputStream buffer;

    private boolean skipNextSpace;

    public ImapResponseComposerImpl(ImapResponseWriter writer, int bufferSize) {
        skipNextSpace = false;
        this.writer = writer;
        this.buffer = new FastByteArrayOutputStream(bufferSize);
    }
    
    public ImapResponseComposerImpl(ImapResponseWriter writer) {
        this(writer, DEFAULT_BUFFER_SIZE);
    }

    @Override
    public ImapResponseComposer untaggedNoResponse(String displayMessage, String responseCode) throws IOException {
        untagged();
        message(NO);
        responseCode(responseCode);
        message(displayMessage);
        end();
        return this;
    }

    @Override
    public ImapResponseComposer continuationResponse(String message) throws IOException {
        buffer.write(CONTINUATION);
        buffer.write(SP);
        writeASCII(message);
        end();
        return this;
    }

    @Override
    public ImapResponseComposer continuationResponse() throws IOException {
        flush();
        writer.write(CONTINUATION_BYTES);
        return this;
    }

    @Override
    public ImapResponseComposer untaggedResponse(String message) throws IOException {
        untagged();
        message(message);
        end();
        return this;
    }

   
    @Override
    public ImapResponseComposer untagged() throws IOException {
        buffer.write(UNTAGGED);
        return this;
    }

    @Override
    public ImapResponseComposer message(String message) throws IOException {
        if (message != null) {
            // TODO: consider message normalisation
            // TODO: CR/NFs in message must be replaced
            // TODO: probably best done in the writer
            space();
            writeASCII(message);

        }
        return this;
    }

    private void responseCode(String responseCode) throws IOException {
        if (responseCode != null && !"".equals(responseCode)) {
            buffer.write(BYTE_OPEN_SQUARE_BRACKET);
            writeASCII(responseCode);
            buffer.write(BYTE_CLOSE_SQUARE_BRACKET);
        }
    }

    @Override
    public ImapResponseComposer end() throws IOException {
        buffer.write(LINE_END_BYTES);
        if (buffer.size() > FLUSH_BUFFER_SIZE) {
            flush();
        }
        return this;
    }

    public void flush() throws IOException {
        if (buffer.size() > 0) {
            writer.write(buffer.toByteArray());
            buffer.reset();
        }
    }

    @Override
    public ImapResponseComposer tag(Tag tag) throws IOException {
        writeASCII(tag.asString());
        return this;
    }

    @Override
    public ImapResponseComposer closeParen() throws IOException {
        closeBracket(BYTE_CLOSING_PARENTHESIS);
        return this;
    }

    @Override
    public ImapResponseComposer openParen() throws IOException {
        openBracket(BYTE_OPENING_PARENTHESIS);
        return this;
    }



    @Override
    public ImapResponseComposer flags(Flags flags) throws IOException {
        message(FLAGS);
        openParen();
        if (flags.contains(Flags.Flag.ANSWERED)) {
            message(ANSWERED);
        }
        if (flags.contains(Flags.Flag.DELETED)) {
            message(DELETED);
        }
        if (flags.contains(Flags.Flag.DRAFT)) {
            message(DRAFT);
        }
        if (flags.contains(Flags.Flag.FLAGGED)) {
            message(FLAGGED);
        }
        if (flags.contains(Flags.Flag.RECENT)) {
            message(RECENT);
        }
        if (flags.contains(Flags.Flag.SEEN)) {
            message(SEEN);
        }
        
        String[] userFlags = flags.getUserFlags();
        for (String userFlag : userFlags) {
            message(userFlag);
        }
        closeParen();
        return this;
    }

    @Override
    public ImapResponseComposer nil() throws IOException {
        message(NIL);
        return this;
    }

    @Override
    public ImapResponseComposer message(byte[] message) throws IOException {
        space();
        buffer.write(message);
        return this;
    }

    @Override
    public ImapResponseComposer quoteUpperCaseAscii(String message) throws IOException {
        if (message == null) {
            nil();
        } else {
            upperCaseAscii(message, true);
        }
        return this;
    }

    private void writeASCII(String string) throws IOException {
        buffer.write(string.getBytes(US_ASCII));
    }

    @Override
    public ImapResponseComposer message(long number) throws IOException {
        space();
        writeASCII(Long.toString(number));
        return this;
    }
    
    @Override
    public ImapResponseComposer mailbox(String mailboxName) throws IOException {
        quote(ModifiedUtf7.encodeModifiedUTF7(mailboxName));
        return this;
    }

    @Override
    public ImapResponseComposer commandName(ImapCommand command) throws IOException {
        return message(command.getNameAsBytes());
    }

    @Override
    public ImapResponseComposer quote(String message) throws IOException {
        space();
        final int length = message.length();
       
        buffer.write(BYTE_DQUOTE);
        for (int i = 0; i < length; i++) {
            char character = message.charAt(i);
            if (character == ImapConstants.BACK_SLASH || character == DQUOTE) {
                buffer.write(BYTE_BACK_SLASH);
            }
            // 7-bit ASCII only
            if (character >= 128) {
                buffer.write(BYTE_QUESTION);
            } else {
                buffer.write((byte) character);
            }
        }
        buffer.write(BYTE_DQUOTE);
        return this;
    }

    @Override
    public ImapResponseComposer quote(char message) throws IOException {
        space();
        buffer.write(BYTE_DQUOTE);
        buffer.write(message);
        buffer.write(BYTE_DQUOTE);
        return this;
    }

    private void closeBracket(byte bracket) throws IOException {
        buffer.write(bracket);
        clearSkipNextSpace();
    }

    private void openBracket(byte bracket) throws IOException {
        space();
        buffer.write(bracket);
        skipNextSpace();
    }

    private void clearSkipNextSpace() {
        skipNextSpace = false;
    }

    @Override
    public ImapResponseComposer skipNextSpace() {
        skipNextSpace = true;
        return this;
    }

    private void space() throws IOException {
        if (skipNextSpace) {
            skipNextSpace = false;
        } else {
            buffer.write(SP);
        }
    }

    @Override
    public ImapResponseComposer literal(Literal literal) throws IOException {
        space();
        buffer.write(BYTE_OPEN_BRACE);
        final long size = literal.size();
        writeASCII(Long.toString(size));
        buffer.write(BYTE_CLOSE_BRACE);
        end();
        if (size > 0) {
            writer.write(literal);
        }
        return this;
    }

    @Override
    public ImapResponseComposer closeSquareBracket() throws IOException {
        closeBracket(BYTE_CLOSE_SQUARE_BRACKET);
        return this;
    }

    @Override
    public ImapResponseComposer openSquareBracket() throws IOException {
        openBracket(BYTE_OPEN_SQUARE_BRACKET);
        return this;
    }

    private void upperCaseAscii(String message, boolean quote) throws IOException {
        space();
        final int length = message.length();
        if (quote) {
            buffer.write(BYTE_DQUOTE);
        }
        for (int i = 0; i < length; i++) {
            final char next = message.charAt(i);
            if (next >= 'a' && next <= 'z') {
                buffer.write((byte) (next - LOWER_CASE_OFFSET));
            } else {
                buffer.write((byte) (next));
            }
        }
        if (quote) {
            buffer.write(BYTE_DQUOTE);
        }
    }

    @Override
    public ImapResponseComposer sequenceSet(UidRange[] ranges) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ranges.length; i++) {
            UidRange range = ranges[i];
            sb.append(range.getFormattedString());
            if (i + 1 < ranges.length) {
                sb.append(",");
            }
        }
        return message(sb.toString());
    }

    @Override
    public ImapResponseComposer sequenceSet(IdRange[] ranges) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ranges.length; i++) {
            IdRange range = ranges[i];
            sb.append(range.getFormattedString());
            if (i + 1 < ranges.length) {
                sb.append(",");
            }
        }
        return message(sb.toString());
    }

}
