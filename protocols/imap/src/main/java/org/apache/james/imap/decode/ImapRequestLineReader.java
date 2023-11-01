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

package org.apache.james.imap.decode;

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.display.ModifiedUtf7;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.PartialRange;
import org.apache.james.imap.api.message.UidRange;
import org.apache.james.imap.api.message.request.DayMonthYear;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SearchResUtil;
import org.apache.james.imap.message.Literal;
import org.apache.james.mailbox.MessageUid;

import com.google.common.base.CharMatcher;

/**
 * Wraps the client input reader with a bunch of convenience methods, allowing
 * lookahead=1 on the underlying character stream. TODO need to look at encoding
 */
public abstract class ImapRequestLineReader {
    /**
     * Provides the ability to ensure characters are part of a permitted set.
     */
    public interface CharacterValidator {
        /**
         * Validates the supplied character.
         *
         * @param chr
         *            The character to validate.
         * @return <code>true</code> if chr is valid, <code>false</code> if not.
         */
        boolean isValid(char chr);
    }

    /**
     * Verifies subsequent characters match a specified string
     */
    public static class StringMatcherCharacterValidator implements CharacterValidator {
        public static StringMatcherCharacterValidator ignoreCase(String expectedString) {
            return new StringMatcherCharacterValidator(expectedString);
        }

        static boolean asciiEqualsIgnoringCase(Character c1, Character c2) {
            return Character.toUpperCase(c1) == Character.toUpperCase(c2);
        }

        private final String expectedString;
        private int position = 0;

        private StringMatcherCharacterValidator(String expectedString) {
            this.expectedString = expectedString;
        }

        /**
         * Verifies whether the next character is valid or not.
         *
         * This call will mutate StringValidator internal state, making it progress to following character validation.
         */
        @Override
        public boolean isValid(char chr) {
            if (position >= expectedString.length()) {
                return false;
            } else {
                return asciiEqualsIgnoringCase(chr, expectedString.charAt(position++));
            }
        }
    }

    public static class NoopCharValidator implements CharacterValidator {
        public static CharacterValidator INSTANCE = new NoopCharValidator();

        @Override
        public boolean isValid(char chr) {
            return true;
        }
    }

    public static class AtomCharValidator implements CharacterValidator {
        public static CharacterValidator INSTANCE = new AtomCharValidator();

        @Override
        public boolean isValid(char chr) {
            return (isCHAR(chr) && !isAtomSpecial(chr) && !isListWildcard(chr) && !isQuotedSpecial(chr));
        }

        private boolean isAtomSpecial(char chr) {
            return (chr == '(' || chr == ')' || chr == '{' || chr == ' ' || chr == Character.CONTROL);
        }
    }

    public static class TagCharValidator extends AtomCharValidator {
        public static CharacterValidator INSTANCE = new TagCharValidator();

        @Override
        public boolean isValid(char chr) {
            if (chr == '+') {
                return false;
            }
            return super.isValid(chr);
        }
    }

    public static class MessageSetCharValidator implements CharacterValidator {
        public static CharacterValidator INSTANCE = new MessageSetCharValidator();

        @Override
        public boolean isValid(char chr) {
            return (isDigit(chr) || chr == ':' || chr == '*' || chr == ',');
        }

        private boolean isDigit(char chr) {
            return '0' <= chr && chr <= '9';
        }
    }

    public static class PartialRangeCharValidator implements CharacterValidator {
        public static CharacterValidator INSTANCE = new PartialRangeCharValidator();

        @Override
        public boolean isValid(char chr) {
            return (isDigit(chr) || chr == ':' || chr == '-');
        }

        private boolean isDigit(char chr) {
            return '0' <= chr && chr <= '9';
        }
    }

    /**
     * Decodes contents of a quoted string. Charset aware. One shot, not thread
     * safe.
     */
    private static class QuotedStringDecoder {
        /** Decoder suitable for charset */
        private final CharsetDecoder decoder;

        /** byte buffer will be filled then flushed to character buffer */
        private final ByteBuffer buffer;

        /** character buffer may be dynamically resized */
        CharBuffer charBuffer;

        public QuotedStringDecoder(Charset charset) {
            decoder = charset.newDecoder();
            buffer = ByteBuffer.allocate(QUOTED_BUFFER_INITIAL_CAPACITY);
            charBuffer = CharBuffer.allocate(QUOTED_BUFFER_INITIAL_CAPACITY);
        }

        public String decode(ImapRequestLineReader request) throws DecodingException {
            try {
                decoder.reset();
                char next = request.nextChar();
                while (next != '"') {
                    // fill up byte buffer before decoding
                    if (!buffer.hasRemaining()) {
                        decodeByteBufferToCharacterBuffer(false);
                    }
                    if (next == '\\') {
                        request.consume();
                        next = request.nextChar();
                        if (!isQuotedSpecial(next)) {
                            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Invalid escaped character in quote: '" + next + "'");
                        }
                    }
                    // TODO: nextChar does not report accurate chars so safe to
                    // cast to byte
                    buffer.put((byte) next);
                    request.consume();
                    next = request.nextChar();
                }
                completeDecoding();
                return charBuffer.toString();

            } catch (IllegalStateException e) {
                throw new DecodingException(HumanReadableText.BAD_IO_ENCODING, "Bad character encoding", e);
            }
        }

        private void completeDecoding() throws DecodingException {
            decodeByteBufferToCharacterBuffer(true);
            flush();
            charBuffer.flip();
        }

        private void flush() throws DecodingException {
            final CoderResult coderResult = decoder.flush(charBuffer);
            if (coderResult.isOverflow()) {
                upsizeCharBuffer();
                flush();
            } else if (coderResult.isError()) {
                throw new DecodingException(HumanReadableText.BAD_IO_ENCODING, "Bad character encoding");
            }
        }

        /**
         * Decodes contents of the byte buffer to the character buffer. The
         * character buffer will be replaced by a larger one if required.
         *
         * @param endOfInput
         *            is the input ended
         */
        private CoderResult decodeByteBufferToCharacterBuffer(boolean endOfInput) throws DecodingException {
            buffer.flip();
            return decodeMoreBytesToCharacterBuffer(endOfInput);
        }

        private CoderResult decodeMoreBytesToCharacterBuffer(boolean endOfInput) throws DecodingException {
            final CoderResult coderResult = decoder.decode(buffer, charBuffer, endOfInput);
            if (coderResult.isOverflow()) {
                upsizeCharBuffer();
                return decodeMoreBytesToCharacterBuffer(endOfInput);
            } else if (coderResult.isError()) {
                throw new DecodingException(HumanReadableText.BAD_IO_ENCODING, "Bad character encoding");
            } else if (coderResult.isUnderflow()) {
                buffer.clear();
            }
            return coderResult;
        }

        /**
         * Increases the size of the character buffer.
         */
        private void upsizeCharBuffer() {
            final int oldCapacity = charBuffer.capacity();
            CharBuffer oldBuffer = charBuffer;
            charBuffer = CharBuffer.allocate(oldCapacity + QUOTED_BUFFER_INITIAL_CAPACITY);
            oldBuffer.flip();
            charBuffer.put(oldBuffer);
        }
    }

    private static final int QUOTED_BUFFER_INITIAL_CAPACITY = 64;

    public static int cap(char next) {
        return next > 'Z' ? next ^ 32 : next;
    }

    public static boolean isCHAR(char chr) {
        return (chr >= 0x01 && chr <= 0x7f);
    }

    public static boolean isListWildcard(char chr) {
        return (chr == '*' || chr == '%');
    }

    public static boolean isQuotedSpecial(char chr) {
        return (chr == '"' || chr == '\\');
    }

    protected char nextChar; // unknown
    protected boolean nextSeen = false;
    private final StringBuilder stringBuilder = new StringBuilder();

    /**
     * Reads the next character in the current line. This method will continue
     * to return the same character until the {@link #consume()} method is
     * called.
     *
     * @return The next character TODO: character encoding is variable and
     *         cannot be determine at the token level; this char is not accurate
     *         reported; should be an octet
     * @throws DecodingException
     *             If the end-of-stream is reached.
     */
    public abstract char nextChar() throws DecodingException;

    /**
     * Reads and consumes a number of characters from the underlying reader,
     * filling the char array provided. TODO: remove unnecessary copying of
     * bits; line reader should maintain an internal ByteBuffer;
     *
     * @param size
     *            count of characters to read and consume
     * @param extraCRLF
     *            <code>true</code> if extra CRLF is wanted, <code>false</code> else
     * @throws DecodingException
     *             If a char can't be read into each array element.
     */
    public abstract Literal read(int size, boolean extraCRLF) throws IOException;

    /**
     * Sends a server command continuation request '+' back to the client,
     * requesting more data to be sent.
     */
    protected abstract void commandContinuationRequest() throws DecodingException;

    /**
     * Reads the next regular, non-space character in the current line. Spaces
     * are skipped over, but end-of-line characters will cause a
     * {@link DecodingException} to be thrown. This method will continue to
     * return the same character until the {@link #consume()} method is called.
     * 
     * @return The next non-space character.
     * @throws DecodingException
     *             If the end-of-line or end-of-stream is reached.
     */
    public char nextWordChar() throws DecodingException {
        char next = nextChar();
        while (next == ' ') {
            consume();
            next = nextChar();
        }

        if (next == '\r' || next == '\n') {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Missing argument.");
        }

        return next;
    }

    public Optional<Character> nextWordCharLenient() throws DecodingException {
        char next = nextChar();
        while (next == ' ') {
            consume();
            next = nextChar();
        }

        if (next == '\r' || next == '\n') {
            return Optional.empty();
        }

        return Optional.of(next);
    }

    /**
     * Moves the request line reader to end of the line, checking that no
     * non-space character are found.
     * 
     * @throws DecodingException
     *             If more non-space tokens are found in this line, or the
     *             end-of-file is reached.
     */
    public void eol() throws DecodingException {
        char next = nextChar();

        // Ignore trailing spaces.
        while (next == ' ') {
            consume();
            next = nextChar();
        }

        // handle DOS and unix end-of-lines
        if (next == '\r') {
            consume();
            next = nextChar();
        }

        // Check if we found extra characters.
        if (next != '\n') {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Expected end-of-line, found '" + (char) next + "'.");
        }
    }

    /**
     * Consumes the current character in the reader, so that subsequent calls to
     * the request will provide a new character. This method does *not* read the
     * new character, or check if such a character exists. If no current
     * character has been seen, the method moves to the next character, consumes
     * it, and moves on to the subsequent one.
     * 
     * @throws DecodingException
     *             if a the current character can't be obtained (eg we're at
     *             end-of-file).
     */
    public char consume() throws DecodingException {
        char current = nextChar();
        nextSeen = false;
        nextChar = 0;
        return current;
    }

    /**
     * Consume the rest of the line
     */
    public void consumeLine() throws DecodingException {
        char next = nextChar();
        while (next != '\n') {
            consume();
            next = nextChar();
        }
        consume();
    }

    /**
     * Reads an argument of type "atom" from the request.
     */
    public String atom() throws DecodingException {
        return consumeWord(AtomCharValidator.INSTANCE, true);
    }

    /**
     * Reads a command "tag" from the request.
     */
    public Tag tag() throws DecodingException {
        return new Tag(consumeWord(TagCharValidator.INSTANCE));
    }

    /**
     * Reads an argument of type "astring" from the request.
     */
    public String astring() throws DecodingException {
        return astring(null);
    }

    /**
     * Reads an argument of type "astring" from the request.
     */
    public String astring(Charset charset) throws DecodingException {
        char next = nextWordChar();
        switch (next) {
        case '"':
            return consumeQuoted(charset);
        case '{':
            return consumeLiteral(charset);
        default:
            return atom();
        }
    }

    /**
     * Reads an argument of type "nstring" from the request.
     */
    public String nstring() throws DecodingException {
        char next = nextWordChar();
        switch (next) {
        case '"':
            return consumeQuoted();
        case '{':
            return consumeLiteral(null);
        default:
            String value = atom();
            if ("NIL".equals(value)) {
                return null;
            } else {
                throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Invalid nstring value: valid values are '\"...\"', '{12} CRLF *CHAR8', and 'NIL'.");
            }
        }
    }

    /**
     * 
     * Reads the mailbox name via {@link #mailboxUTF7()} but also decode it via {@link ModifiedUtf7#decodeModifiedUTF7(String)}
     * 
     * If you really want to get the modified UTF7 version you should use {@link #mailboxUTF7()}
     * 
     * @return decodedMailbox
     * 
     */
    public String mailbox() throws DecodingException {
       return ModifiedUtf7.decodeModifiedUTF7(mailboxUTF7());
    }

    /**
     * Reads a "mailbox" argument from the request. Not implemented *exactly* as
     * per spec, since a quoted or literal "inbox" still yeilds "INBOX" (ie
     * still case-insensitive if quoted or literal). I think this makes sense.
     * 
     * mailbox ::= "INBOX" / astring ;; INBOX is case-insensitive. All case
     * variants of ;; INBOX (e.g. "iNbOx") MUST be interpreted as INBOX ;; not
     * as an astring.
     * 
     * Be aware that mailbox names are encoded via a modified UTF7. For more information RFC3501
     */
    public String mailboxUTF7() throws DecodingException {
        String mailbox = astring();
        if (mailbox.equalsIgnoreCase(ImapConstants.INBOX_NAME)) {
            return ImapConstants.INBOX_NAME;
        } else {
            return mailbox;
        }
    }
    
    /**
     * Reads one <code>date</code> argument from the request.
     * 
     * @return <code>DayMonthYear</code>, not null
     */
    public DayMonthYear date() throws DecodingException {

        final char one = consume();
        final char two = consume();
        final int day;
        if (two == '-') {
            day = DecoderUtils.decodeFixedDay(' ', one);
        } else {
            day = DecoderUtils.decodeFixedDay(one, two);
            nextIsDash();
        }

        final char monthFirstChar = consume();
        final char monthSecondChar = consume();
        final char monthThirdChar = consume();
        final int month = DecoderUtils.decodeMonth(monthFirstChar, monthSecondChar, monthThirdChar) + 1;
        nextIsDash();
        final char milleniumChar = consume();
        final char centuryChar = consume();
        final char decadeChar = consume();
        final char yearChar = consume();
        final int year = DecoderUtils.decodeYear(milleniumChar, centuryChar, decadeChar, yearChar);
        return new DayMonthYear(day, month, year);
    }

    private void nextIsDash() throws DecodingException {
        final char next = consume();
        if (next != '-') {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Expected dash but was " + next);
        }
    }

    /**
     * Reads a "date-time" argument from the request.
     */
    public LocalDateTime dateTime() throws DecodingException {
        char next = nextWordChar();
        String dateString;
        if (next == '"') {
            dateString = consumeQuoted();
        } else {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "DateTime values must be quoted.");
        }

        return DecoderUtils.decodeDateTime(dateString);
    }

    /**
     * Reads the next "word from the request, comprising all characters up to
     * the next SPACE. Characters are tested by the supplied CharacterValidator,
     * and an exception is thrown if invalid characters are encountered.
     */
    public String consumeWord(CharacterValidator validator) throws DecodingException {
        return consumeWord(validator, false);
    }

    public String consumeWord(CharacterValidator validator, boolean stripParen) throws DecodingException {
        stringBuilder.setLength(0);
        char next = nextWordChar();
        while (!isWhitespace(next) && (stripParen == false || next != ')')) {
            if (validator.isValid(next)) {
                if (stripParen == false || next != '(') {
                    stringBuilder.append(next);
                }
                consume();
            } else {
                throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Invalid character: '" + next + "'");
            }
            next = nextChar();
        }
        return stringBuilder.toString();
    }

    public String readUntil(CharMatcher terminator) throws DecodingException {
        stringBuilder.setLength(0);
        char next = nextChar();
        while (!terminator.matches(next)) {
            stringBuilder.append(next);
            consume();
            next = nextChar();
        }
        return stringBuilder.toString();
    }
    
    private static boolean isWhitespace(char next) {
        return (next == ' ' || next == '\n' || next == '\r' || next == '\t');
    }

    /**
     * Reads an argument of type "literal" from the request, in the format: "{"
     * charCount "}" CRLF *CHAR8 Note before calling, the request should be
     * positioned so that nextChar is '{'. Leading whitespace is not skipped in
     * this method.
     * 
     * @param charset
     *            , or null for <code>US-ASCII</code>
     */
    public String consumeLiteral(Charset charset) throws DecodingException {
        if (charset == null) {
            return consumeLiteral(US_ASCII);
        } else {
            try {
                ImmutablePair<Integer, Literal> literal = consumeLiteral(false);
                try (InputStream in = literal.right.getInputStream()) {
                    Integer size = literal.left;
                    byte[] data = IOUtils.readFully(in, size);
                    ByteBuffer buffer = ByteBuffer.wrap(data);
                    return decode(charset, buffer);
                } catch (IOException e) {
                    throw new DecodingException(HumanReadableText.BAD_IO_ENCODING, "Bad character encoding", e);
                } finally {
                    if (literal.right instanceof Closeable) {
                        try {
                            ((Closeable) literal.right).close();
                        } catch (IOException e) {
                            // silent
                        }
                    }
                }
            } catch (IOException e) {
                throw new DecodingException(HumanReadableText.SOCKET_IO_FAILURE, "Could not read literal", e);
            }
        }
    }

    /**
     * @return the literal data and its expected size
     */
    public ImmutablePair<Integer, Literal> consumeLiteral(boolean extraCRLF) throws IOException {
        // The 1st character must be '{'
        consumeChar('{');

        stringBuilder.setLength(0);
        char next = nextChar();
        while (next != '}' && next != '+') {
            stringBuilder.append(next);
            consume();
            next = nextChar();
        }

        // If the number is *not* suffixed with a '+', we *are* using a
        // synchronized literal, and we need to send command continuation
        // request before reading data.
        boolean synchronizedLiteral = true;
        // '+' indicates a non-synchronized literal (no command continuation
        // request)
        if (next == '+') {
            synchronizedLiteral = false;
            consumeChar('+');
        }

        // Consume the '}' and the newline
        consumeChar('}');
        consumeCRLF();

        if (synchronizedLiteral) {
            commandContinuationRequest();
        }

        try {
            int size = Integer.parseInt(stringBuilder.toString());
            if (size < 0) {
                throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Expected a valid positive number as literal size");
            }
            return ImmutablePair.of(size, read(size, extraCRLF));
        } catch (NumberFormatException e) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Expected a valid positive number as literal size");
        }
    }

    private String decode(Charset charset, ByteBuffer buffer) throws DecodingException {
        try {
            return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(buffer).toString();

        } catch (IllegalStateException | CharacterCodingException e) {
            throw new DecodingException(HumanReadableText.BAD_IO_ENCODING, "Bad character encoding", e);
        }
    }

    /**
     * Consumes a CRLF from the request. TODO: This is too liberal, the spec
     * insists on \r\n for new lines.
     */
    private void consumeCRLF() throws DecodingException {
        char next = nextChar();
        if (next != '\n') {
            consumeChar('\r');
        }
        consumeChar('\n');
    }

    /**
     * Consumes the next character in the request, checking that it matches the
     * expected one. This method should be used when the
     */
    public void consumeChar(char expected) throws DecodingException {
        char consumed = consume();
        if (consumed != expected) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Expected:'" + expected + "' found:'" + consumed + "'");
        }
    }

    /**
     * Reads a quoted string value from the request.
     */
    public String consumeQuoted() throws DecodingException {
        return consumeQuoted(null);
    }

    /**
     * Reads a quoted string value from the request.
     */
    protected String consumeQuoted(Charset charset) throws DecodingException {
        if (charset == null) {
            return consumeQuoted(US_ASCII);
        } else {
            // The 1st character must be '"'
            consumeChar('"');
            final QuotedStringDecoder decoder = new QuotedStringDecoder(charset);
            final String result = decoder.decode(this);
            consumeChar('"');
            return result;
        }
    }

    /**
     * Reads a "flags-list" argument from the request.
     */
    public Flags flagList() throws DecodingException {
        Flags flags = new Flags();
        nextWordChar();
        consumeChar('(');
        while (nextChar() != ')') {
            String nextWord = consumeWord(NoopCharValidator.INSTANCE, true);
            if (nextWord.isEmpty()) {
                // Throw to avoid an infinite loop...
                throw new DecodingException(HumanReadableText.FAILED, "Empty word encountered");
            }
            DecoderUtils.setFlag(nextWord, flags);
            nextWordChar();
        }
        consumeChar(')');

        return flags;
    }

    /**
     * Reads a "flag" argument from the request.
     */
    public Flags flag() throws DecodingException {
        Flags flags = new Flags();
        nextWordChar();

        String nextFlag = consumeWord(NoopCharValidator.INSTANCE);
        DecoderUtils.setFlag(nextFlag, flags);
        return flags;
    }

    /**
     * Calls {@link #number(boolean)} with argument of false
     * 
     * @return number
     */
    public long number() throws DecodingException {
        return number(false);
    }

    /**
     * Reads an argument of type "number" from the request
     * 
     * @param stopOnParen true if it should stop to parse on the first closing paren
     * @return number
     */
    public long number(boolean stopOnParen) throws DecodingException {
        return readDigits(0, 0, true, stopOnParen);
    }
    
    private long readDigits(int add, long total, boolean first, boolean stopOnParen
            ) throws DecodingException {
        final char next;
        if (first) {
            next = nextWordChar();
        } else {
            consume();
            next = nextChar();
        }
        final long currentTotal = (10 * total) + add;
        switch (next) {
        case '0':
            return readDigits(0, currentTotal, false, stopOnParen);
        case '1':
            return readDigits(1, currentTotal, false, stopOnParen);
        case '2':
            return readDigits(2, currentTotal, false, stopOnParen);
        case '3':
            return readDigits(3, currentTotal, false, stopOnParen);
        case '4':
            return readDigits(4, currentTotal, false, stopOnParen);
        case '5':
            return readDigits(5, currentTotal, false, stopOnParen);
        case '6':
            return readDigits(6, currentTotal, false, stopOnParen);
        case '7':
            return readDigits(7, currentTotal, false, stopOnParen);
        case '8':
            return readDigits(8, currentTotal, false, stopOnParen);
        case '9':
            return readDigits(9, currentTotal, false, stopOnParen);
        case '.':
        case ' ':
        case '>':
        case '\r':
        case '\n':
        case '\t':
            return currentTotal;
        case ')':
            if (stopOnParen) {
                return currentTotal;
            } else {
                throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Expected a digit but was " + next);
            }
        default:
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Expected a digit but was " + next);
        }
    }

    /**
     * Reads an argument of type "nznumber" (a non-zero number) (NOTE this isn't
     * strictly as per the spec, since the spec disallows numbers such as "0123"
     * as nzNumbers (although it's ok as a "number". I think the spec is a bit
     * shonky.)
     */
    public long nzNumber() throws DecodingException {
        long number = number();
        if (number == 0) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Zero value not permitted.");
        }
        return number;
    }

    /**
     * Reads a "message set" argument, and parses into an IdSet.
     */
    public IdRange[] parseIdRange() throws DecodingException {
        return parseIdRange(null);
    }

    /**
     * Reads a "message set" argument, and parses into an IdSet. This also support the use of $ as sequence-set as stated in SEARCHRES RFC5182 
     */
    public IdRange[] parseIdRange(ImapSession session) throws DecodingException {
        if (session != null) {
            char c = nextWordChar();
            // Special handling for SEARCHRES extension. See RFC5182
            if (c == '$') {
                consume();
                return SearchResUtil.getSavedSequenceSet(session);
            }
        }

        // Don't fail to parse id ranges which are enclosed by "(..)"
        // See IMAP-283
        String nextWord = consumeWord(MessageSetCharValidator.INSTANCE, true);

        int commaPos = nextWord.indexOf(',');
        if (commaPos == -1) {
            return new IdRange[] { parseRange(nextWord) };
        }

        ArrayList<IdRange> rangeList = new ArrayList<>();
        int pos = 0;
        while (commaPos != -1) {
            String range = nextWord.substring(pos, commaPos);
            IdRange set = parseRange(range);
            rangeList.add(set);

            pos = commaPos + 1;
            commaPos = nextWord.indexOf(',', pos);
        }
        String range = nextWord.substring(pos);
        rangeList.add(parseRange(range));

        // merge the ranges to minimize the needed queries.
        // See IMAP-211
        List<IdRange> merged = IdRange.mergeRanges(rangeList);
        return merged.toArray(IdRange[]::new);
    }

    /**
     * Reads a "message set" argument, and parses into an IdSet. This also support the use of $ as sequence-set as stated in SEARCHRES RFC5182 
     */
    public UidRange[] parseUidRange() throws DecodingException {
        // Don't fail to parse id ranges which are enclosed by "(..)"
        // See IMAP-283
        String nextWord = consumeWord(MessageSetCharValidator.INSTANCE, true);

        int commaPos = nextWord.indexOf(',');
        if (commaPos == -1) {
            return new UidRange[] { parseUidRange(nextWord) };
        }

        ArrayList<UidRange> rangeList = new ArrayList<>();
        int pos = 0;
        while (commaPos != -1) {
            String range = nextWord.substring(pos, commaPos);
            UidRange set = parseUidRange(range);
            rangeList.add(set);

            pos = commaPos + 1;
            commaPos = nextWord.indexOf(',', pos);
        }
        String range = nextWord.substring(pos);
        rangeList.add(parseUidRange(range));

        // merge the ranges to minimize the needed queries.
        // See IMAP-211
        List<UidRange> merged = UidRange.mergeRanges(rangeList);
        return merged.toArray(UidRange[]::new);
    }

    public PartialRange parsePartialRange() throws DecodingException {
        String nextWord = consumeWord(PartialRangeCharValidator.INSTANCE, true);

        if (!nextWord.contains(":")) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "PartialRange should contain ':'");
        }

        String lowValString = nextWord.substring(0, nextWord.indexOf(':'));
        String upValString = nextWord.substring(nextWord.indexOf(':') + 1);

        return new PartialRange(Long.parseLong(lowValString),
            Long.parseLong(upValString));
    }
    
    /**
     * Reads the first non-space character in the current line. This method will continue
     * to resume if meet space character until meet the non-space character.
     *
     * @return The next first non-space character
     * @throws DecodingException
     *             If the end-of-stream is reached.
     */
    public char nextNonSpaceChar() throws DecodingException {
        char next = nextChar();
        while (next == ' ') {
            consume();
            next = nextChar();
        }
        return next;
    }
    
    /**
     * Parse a range which use a ":" as delimiter
     *
     * @return idRange
     */
    private IdRange parseRange(String range) throws DecodingException {
        int pos = range.indexOf(':');
        try {
            if (pos == -1) {

                // Check if its a single "*" and so should return last message
                // in mailbox. See IMAP-289
                if (range.length() == 1 && range.charAt(0) == '*') {
                    return new IdRange(Long.MAX_VALUE, Long.MAX_VALUE);
                } else {
                    long value = parseUnsignedInteger(range);
                    return new IdRange(value);
                }
            } else {
                // Make sure we detect the low and high value
                // See https://issues.apache.org/jira/browse/IMAP-212
                long val1 = parseUnsignedInteger(range.substring(0, pos));
                long val2 = parseUnsignedInteger(range.substring(pos + 1));

                // handle "*:*" ranges. See IMAP-289
                if (val1 == Long.MAX_VALUE && val2 == Long.MAX_VALUE) {
                    return new IdRange(Long.MAX_VALUE, Long.MAX_VALUE);
                } else if (val1 <= val2) {
                    return new IdRange(val1, val2);
                } else if (val1 == Long.MAX_VALUE) {
                    // *:<num> message range must be converted to <num>:*
                    // See IMAP-290
                    return new IdRange(val2, Long.MAX_VALUE);
                } else {
                    return new IdRange(val2, val1);
                }
            }
        } catch (NumberFormatException e) {
            throw new DecodingException(HumanReadableText.INVALID_MESSAGESET, "Invalid message set.", e);
        }
    }

    /**
     * Parse a range which use a ":" as delimiter
     */
    private UidRange parseUidRange(String range) throws DecodingException {
        int pos = range.indexOf(':');
        try {
            if (pos == -1) {

                // Check if its a single "*" and so should return last message
                // in mailbox. See IMAP-289
                if (range.length() == 1 && range.charAt(0) == '*') {
                    return new UidRange(MessageUid.MAX_VALUE);
                } else {
                    long value = parseUnsignedInteger(range);
                    return new UidRange(MessageUid.of(value));
                }
            } else {
                // Make sure we detect the low and high value
                // See https://issues.apache.org/jira/browse/IMAP-212
                long val1 = parseUnsignedInteger(range.substring(0, pos));
                long val2 = parseUnsignedInteger(range.substring(pos + 1));

                // handle "*:*" ranges. See IMAP-289
                if (val1 == Long.MAX_VALUE && val2 == Long.MAX_VALUE) {
                    return new UidRange(MessageUid.MAX_VALUE);
                } else if (val1 <= val2) {
                    return new UidRange(MessageUid.of(val1), MessageUid.of(val2));
                } else if (val1 == Long.MAX_VALUE) {
                    // *:<num> message range must be converted to <num>:*
                    // See IMAP-290
                    return new UidRange(MessageUid.of(val2), MessageUid.MAX_VALUE);
                } else {
                    return new UidRange(MessageUid.of(val2), MessageUid.of(val1));
                }
            }
        } catch (NumberFormatException e) {
            throw new DecodingException(HumanReadableText.INVALID_MESSAGESET, "Invalid message set.", e);
        }
    }
    
    private long parseUnsignedInteger(String value) throws DecodingException {
        if (value.length() == 1 && value.charAt(0) == '*') {
            return Long.MAX_VALUE;
        } else {
            long number = Long.parseLong(value);
            if (number < ImapConstants.MIN_NZ_NUMBER || number > ImapConstants.MAX_NZ_NUMBER) {
                throw new DecodingException(HumanReadableText.INVALID_MESSAGESET, "Invalid message set. Numbers must be unsigned 32-bit Integers");
            }
            return number;

        }
    }

}
