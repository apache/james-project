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

import java.io.IOException;
import java.nio.charset.Charset;

import javax.mail.Flags;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.display.CharsetUtil;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.encode.ImapResponseComposer;
import org.apache.james.imap.encode.ImapResponseWriter;
import org.apache.james.imap.message.response.Literal;
import org.apache.james.protocols.imap.utils.FastByteArrayOutputStream;

/**
 * Class providing methods to send response messages from the server to the
 * client.
 */
public class ImapResponseComposerImpl implements ImapConstants, ImapResponseComposer {

    public static final String FLAGS = "FLAGS";

    public static final String FAILED = "failed.";
    private static final int LOWER_CASE_OFFSET = 'a' - 'A';
    public final static int DEFAULT_BUFFER_SIZE = 2048;
    
    
    private final ImapResponseWriter writer;

    private final FastByteArrayOutputStream buffer;

    private final Charset usAscii;

    private boolean skipNextSpace;

    public ImapResponseComposerImpl(final ImapResponseWriter writer, int bufferSize) {
        skipNextSpace = false;
        usAscii = Charset.forName("US-ASCII");
        this.writer = writer;
        this.buffer = new FastByteArrayOutputStream(bufferSize);
    }
    
    public ImapResponseComposerImpl(final ImapResponseWriter writer) {
        this(writer, DEFAULT_BUFFER_SIZE);
    }

    /**
     * @see
     * org.apache.james.imap.encode.ImapResponseComposer#untaggedNoResponse
     * (java.lang.String, java.lang.String)
     */
    public ImapResponseComposer untaggedNoResponse(String displayMessage, String responseCode) throws IOException {
        untagged();
        message(NO);
        responseCode(responseCode);
        message(displayMessage);
        end();
        return this;
    }

    /**
     * @see
     * org.apache.james.imap.encode.ImapResponseComposer#continuationResponse
     * (java.lang.String)
     */
    public ImapResponseComposer continuationResponse(String message) throws IOException {
        writeASCII(CONTINUATION + SP + message);
        end();
        return this;
    }



    /**
     * @see
     * org.apache.james.imap.encode.ImapResponseComposer#commandResponse(org.apache.james.imap.api.ImapCommand,
     * java.lang.String)
     */
    public ImapResponseComposer commandResponse(ImapCommand command, String message) throws IOException {
        untagged();
        commandName(command.getName());
        message(message);
        end();
        return this;
    }

    /**
     * @see
     * org.apache.james.imap.encode.ImapResponseComposer#taggedResponse(java.lang.String, java.lang.String)
     */
    public ImapResponseComposer taggedResponse(String message, String tag) throws IOException {
        tag(tag);
        message(message);
        end();
        return this;
    }

    /**
     * @see
     * org.apache.james.imap.encode.ImapResponseComposer#untaggedResponse(java.lang.String)
     */
    public ImapResponseComposer untaggedResponse(String message) throws IOException {
        untagged();
        message(message);
        end();
        return this;
    }

   
    /**
     * @see org.apache.james.imap.encode.ImapResponseComposer#untagged()
     */
    public ImapResponseComposer untagged() throws IOException {
        writeASCII(UNTAGGED);
        return this;
    }

    /**
     * @see
     * org.apache.james.imap.encode.ImapResponseComposer#message(java.lang.String)
     */
    public ImapResponseComposer message(final String message) throws IOException {
        if (message != null) {
            // TODO: consider message normalisation
            // TODO: CR/NFs in message must be replaced
            // TODO: probably best done in the writer
            space();
            writeASCII(message);

        }
        return this;
    }

    private void responseCode(final String responseCode) throws IOException {
        if (responseCode != null && !"".equals(responseCode)) {
            writeASCII(" [");
            writeASCII(responseCode);
            buffer.write(BYTE_CLOSE_SQUARE_BRACKET);
        }
    }

    /**
     * @see org.apache.james.imap.encode.ImapResponseComposer#end()
     */
    public ImapResponseComposer end() throws IOException {
        buffer.write(LINE_END.getBytes());
        writer.write(buffer.toByteArray());
        buffer.reset();
        return this;
    }

    /**
     * @see
     * org.apache.james.imap.encode.ImapResponseComposer#tag(java.lang.String)
     */
    public ImapResponseComposer tag(String tag) throws IOException {
        writeASCII(tag);
        return this;
    }

    /**
     * @see org.apache.james.imap.encode.ImapResponseComposer#closeParen()
     */
    public ImapResponseComposer closeParen() throws IOException {
        closeBracket(BYTE_CLOSING_PARENTHESIS);
        return this;
    }

    /**
     * @see org.apache.james.imap.encode.ImapResponseComposer#openParen()
     */
    public ImapResponseComposer openParen() throws IOException {
        openBracket(BYTE_OPENING_PARENTHESIS);
        return this;
    }



    /**
     * @see
     * org.apache.james.imap.encode.ImapResponseComposer#flags(javax.mail.Flags)
     */
    public ImapResponseComposer flags(Flags flags) throws IOException {
        message(FLAGS);
        openParen();
        if (flags.contains(Flags.Flag.ANSWERED)) {
            message("\\Answered");
        }
        if (flags.contains(Flags.Flag.DELETED)) {
            message("\\Deleted");
        }
        if (flags.contains(Flags.Flag.DRAFT)) {
            message("\\Draft");
        }
        if (flags.contains(Flags.Flag.FLAGGED)) {
            message("\\Flagged");
        }
        if (flags.contains(Flags.Flag.RECENT)) {
            message("\\Recent");
        }
        if (flags.contains(Flags.Flag.SEEN)) {
            message("\\Seen");
        }
        
        String[] userFlags = flags.getUserFlags();
        for (int i = 0; i < userFlags.length; i++) {
            message(userFlags[i]);
        }
        closeParen();
        return this;
    }

    /**
     * @see org.apache.james.imap.encode.ImapResponseComposer#nil()
     */
    public ImapResponseComposer nil() throws IOException {
        message(NIL);
        return this;
    }

    /**
     * @see
     * org.apache.james.imap.encode.ImapResponseComposer#upperCaseAscii(java.lang.String)
     */
    public ImapResponseComposer upperCaseAscii(String message) throws IOException {
        if (message == null) {
            nil();
        } else {
            upperCaseAscii(message, false);
        }
        return this;
    }

    /**
     * @see
     * org.apache.james.imap.encode.ImapResponseComposer#quoteUpperCaseAscii
     * (java.lang.String)
     */
    public ImapResponseComposer quoteUpperCaseAscii(String message) throws IOException {
        if (message == null) {
            nil();
        } else {
            upperCaseAscii(message, true);
        }
        return this;
    }


    private void writeASCII(final String string) throws IOException {
        buffer.write(string.getBytes(usAscii));
    }

    /**
     * @see org.apache.james.imap.encode.ImapResponseComposer#message(long)
     */
    public ImapResponseComposer message(long number) throws IOException {
        space();
        writeASCII(Long.toString(number));
        return this;
    }
    
    /**
     * @see org.apache.james.imap.encode.ImapResponseComposer#mailbox(java.lang.String)
     */
    public ImapResponseComposer mailbox(final String mailboxName) throws IOException {
        quote(CharsetUtil.encodeModifiedUTF7(mailboxName));
        return this;
    }

    /**
     * @see
     * org.apache.james.imap.encode.ImapResponseComposer#commandName(java.lang.String)
     */
    public ImapResponseComposer commandName(String commandName) throws IOException {
        space();
        writeASCII(commandName);
        return this;
    }

    /**
     * @see
     * org.apache.james.imap.encode.ImapResponseComposer#quote(java.lang.String)
     */
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
            if (character > 128) {
                buffer.write(BYTE_QUESTION);
            } else {
                buffer.write((byte) character);
            }
        }
        buffer.write(BYTE_DQUOTE);
        return this;
    }


    private void closeBracket(final byte bracket) throws IOException {
        buffer.write(bracket);
        clearSkipNextSpace();
    }

    private void openBracket(final byte bracket) throws IOException {
        space();
        buffer.write(bracket);
        skipNextSpace();
    }

    private void clearSkipNextSpace() {
        skipNextSpace = false;
    }

    /**
     * @see org.apache.james.imap.encode.ImapResponseComposer#skipNextSpace()
     */
    public ImapResponseComposer skipNextSpace() {
        skipNextSpace = true;
        return this;
    }

    private void space() throws IOException {
        if (skipNextSpace) {
            skipNextSpace = false;
        } else {
            buffer.write(SP.getBytes());
        }
    }

    /**
     * @see
     * org.apache.james.imap.encode.ImapResponseComposer#literal(org.apache.james.imap.message.response.Literal)
     */
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

    public ImapResponseComposer closeSquareBracket() throws IOException {
        closeBracket(BYTE_CLOSE_SQUARE_BRACKET);
        return this;
    }

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

    /**
     * @see org.apache.james.imap.encode.ImapResponseComposer#sequenceSet(org.apache.james.imap.api.message.IdRange[])
     */
    public ImapResponseComposer sequenceSet(IdRange[] ranges) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0 ; i< ranges.length; i++) {
            IdRange range = ranges[i];
            sb.append(range.getFormattedString());
            if (i + 1 < ranges.length) {
                sb.append(",");
            }
        }
        return message(sb.toString());
    }


}
