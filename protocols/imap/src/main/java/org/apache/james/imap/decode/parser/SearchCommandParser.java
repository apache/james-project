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
package org.apache.james.imap.decode.parser;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.UidRange;
import org.apache.james.imap.api.message.request.DayMonthYear;
import org.apache.james.imap.api.message.request.SearchKey;
import org.apache.james.imap.api.message.request.SearchOperation;
import org.apache.james.imap.api.message.request.SearchResultOption;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponse.ResponseCode;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.message.request.SearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parse SEARCH commands
 */
public class SearchCommandParser extends AbstractUidCommandParser {
    public static class Context {
        private Optional<Charset> previousCharset = Optional.empty();

        public void setCharset(Charset charset) {
            previousCharset = Optional.of(charset);
        }

        public Charset getCharset() {
            return previousCharset.orElse(null);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchCommandParser.class);

    @Inject
    public SearchCommandParser(StatusResponseFactory statusResponseFactory) {
        super(ImapConstants.SEARCH_COMMAND, statusResponseFactory);
    }

    /**
     * Parses the request argument into a valid search term.
     * 
     * @param request
     *            <code>ImapRequestLineReader</code>, not null
     * @param context
     *            Holder for <code>Charset</code> or null if there is no charset
     * @param isFirstToken
     *            true when this is the first token read, false otherwise
     */
    protected SearchKey searchKey(ImapSession session, ImapRequestLineReader request, Context context, boolean isFirstToken) throws DecodingException, IllegalCharsetNameException, UnsupportedCharsetException {
        final char next = request.nextChar();
        
        if (next >= '0' && next <= '9' || next == '*' || next == '$') {
            return sequenceSet(session, request);
        } else if (next == '(') {
            return paren(session, request, context);
        } else {
            final int cap = consumeAndCap(request);
            switch (cap) {
            
            case 'A':
                return a(request);
            case 'B':
                return b(request, context.getCharset());
            case 'C':
                return c(session, request, isFirstToken, context);
            case 'D':
                return d(request);
            case 'E':
                return emailId(request);
            case 'F':
                return f(request, context.getCharset());
            case 'G':
                throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown search key");
            case 'H':
                return header(request, context.getCharset());
            case 'I':
                throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown search key");
            case 'J':
                throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown search key");
            case 'K':
                return keyword(request);
            case 'L':
                return larger(request);
            case 'M':
                return modseq(request);
            case 'N':
                return n(session, request, context);
            case 'O':
                return o(session, request, context);
            case 'P':
                throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown search key");
            case 'Q':
                throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown search key");
            case 'R':
                nextIsE(request);
                nextIsC(request);
                return recent(request);
            case 'S':
                return s(request, context.getCharset());
            case 'T':
                return t(request, context.getCharset());
            case 'U':
                return u(request);
            case 'Y':
                return younger(request);
            default:
                throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown search key");
            }
        }
    }

    private SearchKey modseq(ImapRequestLineReader request) throws DecodingException {        
        nextIsO(request);
        nextIsD(request);
        nextIsS(request);
        nextIsE(request);
        nextIsQ(request);
        
        try {
            return SearchKey.buildModSeq(request.number());
        } catch (DecodingException e) {
            // Just consume the [<entry-name> <entry-type-req>] and ignore it
            // See RFC4551 3.4. MODSEQ Search Criterion in SEARCH
            request.consumeQuoted();
            request.consumeWord(chr -> true);
            return SearchKey.buildModSeq(request.number());
        }
    }

    private SearchKey paren(ImapSession session, ImapRequestLineReader request, Context context) throws DecodingException {
        request.consume();
        List<SearchKey> keys = new ArrayList<>();
        addUntilParen(session, request, keys, context);
        return SearchKey.buildAnd(keys);
    }

    private void addUntilParen(ImapSession session, ImapRequestLineReader request, List<SearchKey> keys, Context context) throws DecodingException {
        final char next = request.nextWordChar();
        if (next == ')') {
            request.consume();
        } else {
            final SearchKey key = searchKey(session, request, context, false);
            keys.add(key);
            addUntilParen(session, request, keys, context);
        }
    }

    private int consumeAndCap(ImapRequestLineReader request) throws DecodingException {
        final char next = request.consume();
        return ImapRequestLineReader.cap(next);
    }
    
    private SearchKey cc(ImapRequestLineReader request, Charset charset) throws DecodingException {
        final SearchKey result;
        nextIsSpace(request);
        final String value = request.astring(charset);
        result = SearchKey.buildCc(value);
        return result;
    }

    private SearchKey c(ImapSession session, ImapRequestLineReader request, boolean isFirstToken, Context context) throws DecodingException, IllegalCharsetNameException, UnsupportedCharsetException {
        final int next = consumeAndCap(request);
        switch (next) {
        case 'C':
            return cc(request, context.getCharset());
        case 'H':
            return charset(session, request, isFirstToken, context);
        default:
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown search key");
        }
    }

    private SearchKey charset(ImapSession session, ImapRequestLineReader request, boolean isFirstToken, Context context) throws DecodingException, IllegalCharsetNameException, UnsupportedCharsetException {
        final SearchKey result;
        nextIsA(request);
        nextIsR(request);
        nextIsS(request);
        nextIsE(request);
        nextIsT(request);
        nextIsSpace(request);
        if (!isFirstToken) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown search key");
        }
        final String value = request.astring();
        final Charset charset = Charset.forName(value);
        request.nextWordChar();
        context.setCharset(charset);
        result = searchKey(session, request, context, false);
        return result;
    }

    private SearchKey u(ImapRequestLineReader request) throws DecodingException {
        final int next = consumeAndCap(request);
        switch (next) {
        case 'I':
            return uid(request);
        case 'N':
            return un(request);
        default:
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown search key");
        }
    }

    private SearchKey un(ImapRequestLineReader request) throws DecodingException {
        final int next = consumeAndCap(request);
        switch (next) {
        case 'A':
            return unanswered(request);
        case 'D':
            return und(request);
        case 'F':
            return unflagged(request);
        case 'K':
            return unkeyword(request);
        case 'S':
            return unseen(request);
        default:
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown search key");
        }
    }

    private SearchKey und(ImapRequestLineReader request) throws DecodingException {
        final int next = consumeAndCap(request);
        switch (next) {
        case 'E':
            return undeleted(request);
        case 'R':
            return undraft(request);
        default:
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown search key");
        }
    }

    private SearchKey t(ImapRequestLineReader request, Charset charset) throws DecodingException {
        final int next = consumeAndCap(request);
        switch (next) {
        case 'E':
            return text(request, charset);
        case 'O':
            return to(request, charset);
        case 'H':
            return threadId(request, charset);
        default:
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown search key");
        }
    }

    private SearchKey s(ImapRequestLineReader request, Charset charset) throws DecodingException {
        final int next = consumeAndCap(request);
        switch (next) {
        case 'E':
            return se(request);
        case 'I':
            return since(request);
        case 'M':
            return smaller(request);
        case 'U':
            return subject(request, charset);
        case 'A':
            return saved(request);
        default:
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown search key");
        }
    }

    private SearchKey se(ImapRequestLineReader request) throws DecodingException {
        final int next = consumeAndCap(request);
        switch (next) {
        case 'E':
            return seen(request);
        case 'N':
            return sen(request);
        default:
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown search key");
        }
    }

    private SearchKey sen(ImapRequestLineReader request) throws DecodingException {
        final int next = consumeAndCap(request);
        switch (next) {
        case 'T':
            return sent(request);
        default:
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown search key");
        }
    }

    private SearchKey sent(ImapRequestLineReader request) throws DecodingException {
        final int next = consumeAndCap(request);
        switch (next) {
        case 'B':
            return sentBefore(request);
        case 'O':
            return sentOn(request);
        case 'S':
            return sentSince(request);
        default:
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown search key");
        }
    }

    private SearchKey saved(ImapRequestLineReader request) throws DecodingException {
        nextIsV(request);
        nextIsE(request);
        nextIsD(request);

        final int next = consumeAndCap(request);
        switch (next) {
            case 'A':
                return saveDateSupported(request);
            case 'B':
                return savedBefore(request);
            case 'O':
                return savedOn(request);
            case 'S':
                return savedSince(request);
            default:
                throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown search key");
        }
    }

    private SearchKey o(ImapSession session, ImapRequestLineReader request, Context context) throws DecodingException {
        final int next = consumeAndCap(request);
        switch (next) {
        case 'L':
            return old(request);
        case 'N':
            return on(request);
        case 'R':
            return or(session, request, context);
        default:
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown search key");
        }
    }
    
    private SearchKey old(ImapRequestLineReader request) throws DecodingException {
        nextIsD(request);
        try {
            // Check if its OLDER keyword
            nextIsE(request);
            return older(request);
        } catch (DecodingException e) {
            return SearchKey.buildOld();
        }
    }
    
    private SearchKey n(ImapSession session, ImapRequestLineReader request, Context context) throws DecodingException {
        final int next = consumeAndCap(request);
        switch (next) {
        case 'E':
            return newOperator(request);
        case 'O':
            return not(session, request, context);
        default:
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown search key");
        }
    }

    private SearchKey f(ImapRequestLineReader request, Charset charset) throws DecodingException {
        final int next = consumeAndCap(request);
        switch (next) {
        case 'L':
            return flagged(request);
        case 'R':
            return from(request, charset);
        default:
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown search key");
        }
    }

    private SearchKey d(ImapRequestLineReader request) throws DecodingException {
        final int next = consumeAndCap(request);
        switch (next) {
        case 'E':
            return deleted(request);
        case 'R':
            return draft(request);
        default:
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown search key");
        }
    }

    private SearchKey keyword(ImapRequestLineReader request) throws DecodingException {
        final SearchKey result;
        nextIsE(request);
        nextIsY(request);
        nextIsW(request);
        nextIsO(request);
        nextIsR(request);
        nextIsD(request);
        nextIsSpace(request);
        final String value = request.atom();
        result = SearchKey.buildKeyword(value);
        return result;
    }

    private SearchKey unkeyword(ImapRequestLineReader request) throws DecodingException {
        final SearchKey result;
        nextIsE(request);
        nextIsY(request);
        nextIsW(request);
        nextIsO(request);
        nextIsR(request);
        nextIsD(request);
        nextIsSpace(request);
        final String value = request.atom();
        result = SearchKey.buildUnkeyword(value);
        return result;
    }

    private SearchKey header(ImapRequestLineReader request, Charset charset) throws DecodingException {
        final SearchKey result;
        nextIsE(request);
        nextIsA(request);
        nextIsD(request);
        nextIsE(request);
        nextIsR(request);
        nextIsSpace(request);
        final String field = request.astring(charset);
        nextIsSpace(request);
        final String value = request.astring(charset);
        result = SearchKey.buildHeader(field, value);
        return result;
    }

    private SearchKey larger(ImapRequestLineReader request) throws DecodingException {
        final SearchKey result;
        nextIsA(request);
        nextIsR(request);
        nextIsG(request);
        nextIsE(request);
        nextIsR(request);
        nextIsSpace(request);
        final long value = request.number();
        result = SearchKey.buildLarger(value);
        return result;
    }

    private SearchKey smaller(ImapRequestLineReader request) throws DecodingException {
        final SearchKey result;
        nextIsA(request);
        nextIsL(request);
        nextIsL(request);
        nextIsE(request);
        nextIsR(request);
        nextIsSpace(request);
        final long value = request.number();
        result = SearchKey.buildSmaller(value);
        return result;
    }

    private SearchKey from(ImapRequestLineReader request, Charset charset) throws DecodingException {
        final SearchKey result;
        nextIsO(request);
        nextIsM(request);
        nextIsSpace(request);
        final String value = request.astring(charset);
        result = SearchKey.buildFrom(value);
        return result;
    }

    private SearchKey flagged(ImapRequestLineReader request) throws DecodingException {
        final SearchKey result;
        nextIsA(request);
        nextIsG(request);
        nextIsG(request);
        nextIsE(request);
        nextIsD(request);
        result = SearchKey.buildFlagged();
        return result;
    }

    private SearchKey unseen(ImapRequestLineReader request) throws DecodingException {
        final SearchKey result;
        nextIsE(request);
        nextIsE(request);
        nextIsN(request);
        result = SearchKey.buildUnseen();
        return result;
    }

    private SearchKey undraft(ImapRequestLineReader request) throws DecodingException {
        final SearchKey result;
        nextIsA(request);
        nextIsF(request);
        nextIsT(request);
        result = SearchKey.buildUndraft();
        return result;
    }

    private SearchKey undeleted(ImapRequestLineReader request) throws DecodingException {
        final SearchKey result;
        nextIsL(request);
        nextIsE(request);
        nextIsT(request);
        nextIsE(request);
        nextIsD(request);
        result = SearchKey.buildUndeleted();
        return result;
    }

    private SearchKey unflagged(ImapRequestLineReader request) throws DecodingException {
        final SearchKey result;
        nextIsL(request);
        nextIsA(request);
        nextIsG(request);
        nextIsG(request);
        nextIsE(request);
        nextIsD(request);
        result = SearchKey.buildUnflagged();
        return result;
    }

    private SearchKey unanswered(ImapRequestLineReader request) throws DecodingException {
        final SearchKey result;
        nextIsN(request);
        nextIsS(request);
        nextIsW(request);
        nextIsE(request);
        nextIsR(request);
        nextIsE(request);
        nextIsD(request);
        result = SearchKey.buildUnanswered();
        return result;
    }
    
    private SearchKey younger(ImapRequestLineReader request) throws DecodingException {
        final SearchKey result;
        nextIsO(request);
        nextIsU(request);
        nextIsN(request);
        nextIsG(request);
        nextIsE(request);
        nextIsR(request);
        nextIsSpace(request);
        result = SearchKey.buildYounger(request.nzNumber());
        return result;
    }
    
    private SearchKey older(ImapRequestLineReader request) throws DecodingException {
        final SearchKey result;
        nextIsR(request);
        
        nextIsSpace(request);
        result = SearchKey.buildOlder(request.nzNumber());
        return result;
    }

    private SearchKey or(ImapSession session, ImapRequestLineReader request, Context context) throws DecodingException {
        final SearchKey result;
        nextIsSpace(request);
        final SearchKey firstKey = searchKey(session, request, context, false);
        nextIsSpace(request);
        final SearchKey secondKey = searchKey(session, request, context, false);
        result = SearchKey.buildOr(firstKey, secondKey);
        return result;
    }

    private SearchKey not(ImapSession session, ImapRequestLineReader request, Context context) throws DecodingException {
        final SearchKey result;
        nextIsT(request);
        nextIsSpace(request);
        final SearchKey nextKey = searchKey(session, request, context, false);
        result = SearchKey.buildNot(nextKey);
        return result;
    }

    private SearchKey newOperator(ImapRequestLineReader request) throws DecodingException {
        final SearchKey result;
        nextIsW(request);
        result = SearchKey.buildNew();
        return result;
    }

    private SearchKey recent(ImapRequestLineReader request) throws DecodingException {
        final SearchKey result;
        //nextIsE(request);
        //nextIsC(request);
        nextIsE(request);
        nextIsN(request);
        nextIsT(request);
        result = SearchKey.buildRecent();
        return result;
    }

    private SearchKey seen(ImapRequestLineReader request) throws DecodingException {
        final SearchKey result;
        nextIsN(request);
        result = SearchKey.buildSeen();
        return result;
    }

    private SearchKey draft(ImapRequestLineReader request) throws DecodingException {
        final SearchKey result;
        nextIsA(request);
        nextIsF(request);
        nextIsT(request);
        result = SearchKey.buildDraft();
        return result;
    }

    private SearchKey deleted(ImapRequestLineReader request) throws DecodingException {
        final SearchKey result;
        nextIsL(request);
        nextIsE(request);
        nextIsT(request);
        nextIsE(request);
        nextIsD(request);
        result = SearchKey.buildDeleted();
        return result;
    }

    private SearchKey b(ImapRequestLineReader request, Charset charset) throws DecodingException {
        final int next = consumeAndCap(request);
        switch (next) {
        case 'C':
            return bcc(request, charset);
        case 'E':
            return before(request);
        case 'O':
            return body(request, charset);
        default:
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown search key");
        }
    }

    private SearchKey body(ImapRequestLineReader request, Charset charset) throws DecodingException {
        final SearchKey result;
        nextIsD(request);
        nextIsY(request);
        nextIsSpace(request);
        final String value = request.astring(charset);
        result = SearchKey.buildBody(value);
        return result;
    }

    private SearchKey on(ImapRequestLineReader request) throws DecodingException {
        final SearchKey result;
        nextIsSpace(request);
        final DayMonthYear value = request.date();
        result = SearchKey.buildOn(value);
        return result;
    }

    private SearchKey sentBefore(ImapRequestLineReader request) throws DecodingException {
        final SearchKey result;
        nextIsE(request);
        nextIsF(request);
        nextIsO(request);
        nextIsR(request);
        nextIsE(request);
        nextIsSpace(request);
        final DayMonthYear value = request.date();
        result = SearchKey.buildSentBefore(value);
        return result;
    }

    private SearchKey savedBefore(ImapRequestLineReader request) throws DecodingException {
        nextIsE(request);
        nextIsF(request);
        nextIsO(request);
        nextIsR(request);
        nextIsE(request);
        nextIsSpace(request);
        return SearchKey.buildSavedBefore(request.date());
    }

    private SearchKey sentSince(ImapRequestLineReader request) throws DecodingException {
        final SearchKey result;
        nextIsI(request);
        nextIsN(request);
        nextIsC(request);
        nextIsE(request);
        nextIsSpace(request);
        final DayMonthYear value = request.date();
        result = SearchKey.buildSentSince(value);
        return result;
    }

    private SearchKey savedSince(ImapRequestLineReader request) throws DecodingException {
        nextIsI(request);
        nextIsN(request);
        nextIsC(request);
        nextIsE(request);
        nextIsSpace(request);
        return SearchKey.buildSavedSince(request.date());
    }

    private SearchKey saveDateSupported(ImapRequestLineReader request) throws DecodingException {
        nextIsT(request);
        nextIsE(request);
        nextIsS(request);
        nextIsU(request);
        nextIsP(request);
        nextIsP(request);
        nextIsO(request);
        nextIsR(request);
        nextIsT(request);
        nextIsE(request);
        nextIsD(request);
        return SearchKey.buildSaveDateSupported();
    }

    private SearchKey since(ImapRequestLineReader request) throws DecodingException {
        final SearchKey result;
        nextIsN(request);
        nextIsC(request);
        nextIsE(request);
        nextIsSpace(request);
        final DayMonthYear value = request.date();
        result = SearchKey.buildSince(value);
        return result;
    }

    private SearchKey sentOn(ImapRequestLineReader request) throws DecodingException {
        final SearchKey result;
        nextIsN(request);
        nextIsSpace(request);
        final DayMonthYear value = request.date();
        result = SearchKey.buildSentOn(value);
        return result;
    }

    private SearchKey savedOn(ImapRequestLineReader request) throws DecodingException {
        nextIsN(request);
        nextIsSpace(request);
        return SearchKey.buildSavedOn(request.date());
    }

    private SearchKey before(ImapRequestLineReader request) throws DecodingException {
        final SearchKey result;
        nextIsF(request);
        nextIsO(request);
        nextIsR(request);
        nextIsE(request);
        nextIsSpace(request);
        final DayMonthYear value = request.date();
        result = SearchKey.buildBefore(value);
        return result;
    }

    private SearchKey bcc(ImapRequestLineReader request, Charset charset) throws DecodingException {
        final SearchKey result;
        nextIsC(request);
        nextIsSpace(request);
        final String value = request.astring(charset);
        result = SearchKey.buildBcc(value);
        return result;
    }

    private SearchKey text(ImapRequestLineReader request, Charset charset) throws DecodingException {
        final SearchKey result;
        nextIsX(request);
        nextIsT(request);
        nextIsSpace(request);
        final String value = request.astring(charset);
        result = SearchKey.buildText(value);
        return result;
    }

    private SearchKey emailId(ImapRequestLineReader request) throws DecodingException {
        nextIsM(request);
        nextIsA(request);
        nextIsI(request);
        nextIsL(request);
        nextIsI(request);
        nextIsD(request);
        nextIsSpace(request);

        return SearchKey.buildMessageId(request.astring());
    }

    private SearchKey uid(ImapRequestLineReader request) throws DecodingException {
        final SearchKey result;
        nextIsD(request);
        nextIsSpace(request);
        final UidRange[] range = request.parseUidRange();
        result = SearchKey.buildUidSet(range);
        return result;
    }

    private SearchKey sequenceSet(ImapSession session, ImapRequestLineReader request) throws DecodingException {
        final IdRange[] range = request.parseIdRange(session);
        return SearchKey.buildSequenceSet(range);
    }

    private SearchKey to(ImapRequestLineReader request, Charset charset) throws DecodingException {
        final SearchKey result;
        nextIsSpace(request);
        final String value = request.astring(charset);
        result = SearchKey.buildTo(value);
        return result;
    }

    private SearchKey threadId(ImapRequestLineReader request, Charset charset) throws DecodingException {
        nextIsR(request);
        nextIsE(request);
        nextIsA(request);
        nextIsD(request);
        nextIsI(request);
        nextIsD(request);
        nextIsSpace(request);
        String astring = request.astring(charset);
        return SearchKey.buildThreadId(astring);
    }

    private SearchKey subject(ImapRequestLineReader request, Charset charset) throws DecodingException {
        final SearchKey result;
        nextIsB(request);
        nextIsJ(request);
        nextIsE(request);
        nextIsC(request);
        nextIsT(request);
        nextIsSpace(request);
        final String value = request.astring(charset);
        result = SearchKey.buildSubject(value);
        return result;
    }

    private SearchKey a(ImapRequestLineReader request) throws DecodingException {
        final int next = consumeAndCap(request);
        switch (next) {
        case 'L':
            return all(request);
        case 'N':
            return answered(request);
        default:
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown search key");
        }
    }

    private SearchKey answered(ImapRequestLineReader request) throws DecodingException {
        final SearchKey result;
        nextIsS(request);
        nextIsW(request);
        nextIsE(request);
        nextIsR(request);
        nextIsE(request);
        nextIsD(request);
        result = SearchKey.buildAnswered();
        return result;
    }

    private SearchKey all(ImapRequestLineReader request) throws DecodingException {
        final SearchKey result;
        nextIsL(request);
        result = SearchKey.buildAll();
        return result;
    }

    private void nextIsSpace(ImapRequestLineReader request) throws DecodingException {
        final char next = request.consume();
        if (next != ' ') {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown search key");
        }
    }

    private void nextIsG(ImapRequestLineReader request) throws DecodingException {
        nextIs(request, 'G', 'g');
    }

    private void nextIsM(ImapRequestLineReader request) throws DecodingException {
        nextIs(request, 'M', 'm');
    }

    private void nextIsI(ImapRequestLineReader request) throws DecodingException {
        nextIs(request, 'I', 'i');
    }

    private void nextIsN(ImapRequestLineReader request) throws DecodingException {
        nextIs(request, 'N', 'n');
    }

    private void nextIsA(ImapRequestLineReader request) throws DecodingException {
        nextIs(request, 'A', 'a');
    }

    private void nextIsT(ImapRequestLineReader request) throws DecodingException {
        nextIs(request, 'T', 't');
    }

    private void nextIsY(ImapRequestLineReader request) throws DecodingException {
        nextIs(request, 'Y', 'y');
    }

    private void nextIsX(ImapRequestLineReader request) throws DecodingException {
        nextIs(request, 'X', 'x');
    }
    
    private void nextIsU(ImapRequestLineReader request) throws DecodingException {
        nextIs(request, 'U', 'u');
    }
    
    private void nextIsO(ImapRequestLineReader request) throws DecodingException {
        nextIs(request, 'O', 'o');
    }

    private void nextIsQ(ImapRequestLineReader request) throws DecodingException {
        nextIs(request, 'Q', 'q');
    }

    private void nextIsF(ImapRequestLineReader request) throws DecodingException {
        nextIs(request, 'F', 'f');
    }

    private void nextIsJ(ImapRequestLineReader request) throws DecodingException {
        nextIs(request, 'J', 'j');
    }

    private void nextIsC(ImapRequestLineReader request) throws DecodingException {
        nextIs(request, 'C', 'c');
    }

    private void nextIsD(ImapRequestLineReader request) throws DecodingException {
        nextIs(request, 'D', 'd');
    }

    private void nextIsB(ImapRequestLineReader request) throws DecodingException {
        nextIs(request, 'B', 'b');
    }

    private void nextIsR(ImapRequestLineReader request) throws DecodingException {
        nextIs(request, 'R', 'r');
    }

    private void nextIsE(ImapRequestLineReader request) throws DecodingException {
        nextIs(request, 'E', 'e');
    }

    private void nextIsW(ImapRequestLineReader request) throws DecodingException {
        nextIs(request, 'W', 'w');
    }

    private void nextIsS(ImapRequestLineReader request) throws DecodingException {
        nextIs(request, 'S', 's');
    }

    private void nextIsL(ImapRequestLineReader request) throws DecodingException {
        nextIs(request, 'L', 'l');
    }

    private void nextIsP(ImapRequestLineReader request) throws DecodingException {
        nextIs(request, 'P', 'p');
    }

    private void nextIsV(ImapRequestLineReader request) throws DecodingException {
        nextIs(request, 'V', 'v');
    }

    private void nextIs(ImapRequestLineReader request, char upper, char lower) throws DecodingException {
        final char next = request.consume();
        if (next != upper && next != lower) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown search key");
        }
    }

    public SearchKey decode(ImapSession session, ImapRequestLineReader request) throws DecodingException, IllegalCharsetNameException, UnsupportedCharsetException {
        request.nextWordChar();
        final Context context = new Context();
        final SearchKey firstKey = searchKey(session, request, context, true);
        final SearchKey result;
        if (request.nextChar() == ' ') {
            List<SearchKey> keys = new ArrayList<>();
            keys.add(firstKey);
            while (request.nextChar() == ' ') {
                request.nextWordChar();
                final SearchKey key = searchKey(session, request, context, false);
                keys.add(key);
            }
            result = SearchKey.buildAnd(keys);
        } else {
            result = firstKey;
        }
        request.eol();
        return result;
    }

    private ImapMessage unsupportedCharset(Tag tag) {
        final ResponseCode badCharset = StatusResponse.ResponseCode.badCharset();
        return taggedNo(tag, ImapConstants.SEARCH_COMMAND, HumanReadableText.BAD_CHARSET, badCharset);
    }

    /**
     * Parse the {@link SearchResultOption}'s which are used for ESEARCH
     */
    List<SearchResultOption> parseOptions(ImapRequestLineReader reader) throws DecodingException {
        List<SearchResultOption> options = new ArrayList<>();
        reader.consumeChar('(');
        reader.nextWordChar();
        
        int cap = consumeAndCap(reader);

        while (cap != ')') {
            switch (cap) {
            case 'A':
                nextIsL(reader);
                nextIsL(reader);
                options.add(SearchResultOption.ALL);
                break;
            case 'C':
                nextIsO(reader);
                nextIsU(reader);
                nextIsN(reader);
                nextIsT(reader);
                options.add(SearchResultOption.COUNT);
                break;
            case 'M':
                final int c = consumeAndCap(reader);
                switch (c) {
                case 'A':
                    nextIsX(reader);
                    options.add(SearchResultOption.MAX);
                    break;
                case 'I':
                    nextIsN(reader);
                    options.add(SearchResultOption.MIN);
                    break;
                default:
                    throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown search key");
                }
                break;
            // Check for SAVE options which is part of the SEARCHRES extension
            case 'S':
                nextIsA(reader);
                nextIsV(reader);
                nextIsE(reader);
                options.add(SearchResultOption.SAVE);
                break;
            default:
                throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown search key");
            }
            reader.nextWordChar();
            cap = consumeAndCap(reader);
        }
        // if the options are empty then we parsed RETURN () which is a shortcut for ALL.
        // See http://www.faqs.org/rfcs/rfc4731.html 3.1
        if (options.isEmpty()) {
            options.add(SearchResultOption.ALL);
        }
        return options;
    }
    
    @Override
    protected ImapMessage decode(ImapRequestLineReader request, Tag tag, boolean useUids, ImapSession session) throws DecodingException {
        try {
            SearchKey recent = null;
            List<SearchResultOption> options = null;
            int c = ImapRequestLineReader.cap(request.nextWordChar());
            if (c == 'R') {
                // if we found a R its either RECENT or RETURN so consume it
                request.consume();
                
                nextIsE(request);
                c = consumeAndCap(request);

                switch (c) {
                case 'C':
                    recent = recent(request);
                    break;
                case 'T': 
                    nextIsU(request);
                    nextIsR(request);
                    nextIsN(request);
                    request.nextWordChar();
                    options = parseOptions(request);
                    break;

                default:
                    throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown search key");
                }
            }
            final SearchKey finalKey;

            if (recent != null) {
                if (request.nextChar() != ' ') {
                    request.eol();
                    finalKey = recent;
                } else {
                    // Parse the search term from the request
                    final SearchKey key = decode(session, request);
                    finalKey = SearchKey.buildAnd(Arrays.asList(recent, key));
                }
            } else {
                // Parse the search term from the request
                finalKey = decode(session, request);
            }
           
            
            
            if (options == null) {
                options = new ArrayList<>();
            }

            return new SearchRequest(new SearchOperation(finalKey, options), useUids, tag);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            LOGGER.debug("Unable to decode request", e);
            return unsupportedCharset(tag);
        }
    }

}
