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

import static org.apache.james.imap.api.message.FetchData.Item.BODY;
import static org.apache.james.imap.api.message.FetchData.Item.BODY_STRUCTURE;
import static org.apache.james.imap.api.message.FetchData.Item.EMAILID;
import static org.apache.james.imap.api.message.FetchData.Item.ENVELOPE;
import static org.apache.james.imap.api.message.FetchData.Item.FLAGS;
import static org.apache.james.imap.api.message.FetchData.Item.INTERNAL_DATE;
import static org.apache.james.imap.api.message.FetchData.Item.MODSEQ;
import static org.apache.james.imap.api.message.FetchData.Item.SAVEDATE;
import static org.apache.james.imap.api.message.FetchData.Item.SIZE;
import static org.apache.james.imap.api.message.FetchData.Item.THREADID;
import static org.apache.james.imap.api.message.FetchData.Item.UID;

import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.BodyFetchElement;
import org.apache.james.imap.api.message.FetchData;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.SectionType;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.FetchPartPathDecoder;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.ImapRequestLineReader.StringMatcherCharacterValidator;
import org.apache.james.imap.message.request.FetchRequest;

import com.google.common.base.CharMatcher;

/**
 * Parse FETCH commands
 */
public class FetchCommandParser extends AbstractUidCommandParser {
    private static final String CHANGEDSINCE = "CHANGEDSINCE";
    private static final String VANISHED = "VANISHED";
    private static final CharMatcher CLOSING_BRACKET = CharMatcher.is(']');
    private static final CharMatcher NEXT_ELEMENT_END = CharMatcher.anyOf(" [)\r\n");

    @Inject
    public FetchCommandParser(StatusResponseFactory statusResponseFactory) {
        super(ImapConstants.FETCH_COMMAND, statusResponseFactory);
    }

    /**
     * Create a {@link FetchData} by reading from the
     * {@link ImapRequestLineReader}
     *
     * @return fetchData
     */
    private FetchData fetchRequest(ImapRequestLineReader request, boolean useUid) throws DecodingException {
        FetchData.Builder fetch = FetchData.builder();

        if (useUid) {
            fetch.fetch(UID);
        }

        char next = nextNonSpaceChar(request);
        if (request.nextChar() == '(') {
            request.consumeChar('(');

            next = nextNonSpaceChar(request);
            while (next != ')') {
                addNextElement(request, fetch);
                next = nextNonSpaceChar(request);
            }
            request.consumeChar(')');
            
            next = nextNonSpaceChar(request);
            if (next == '(') {
                request.consumeChar('(');
                while (parseModifier(request, fetch)) {
                    nextNonSpaceChar(request);
                }
                request.consumeChar(')');
            }
        } else {
            addNextElement(request, fetch);
        }

        return fetch.build();
    }

    private boolean parseModifier(ImapRequestLineReader request, FetchData.Builder fetch) throws DecodingException {
        char next = request.nextChar();
        switch (next) {
        case 'C':
            // Now check for the CHANGEDSINCE option which is part of CONDSTORE
            request.consumeWord(StringMatcherCharacterValidator.ignoreCase(CHANGEDSINCE));
            fetch.changedSince(request.number(true));
            return true;
        case 'P':
            request.consumeWord(StringMatcherCharacterValidator.ignoreCase("PARTIAL"));
            fetch.partial(request.parsePartialRange());
            return true;
        case 'V':
            // Check for the VANISHED option which is part of QRESYNC
            request.consumeWord(StringMatcherCharacterValidator.ignoreCase(VANISHED), true);
            fetch.vanished(true);
            return true;
        default:
            return false;
        }
    }

    private void addNextElement(ImapRequestLineReader reader, FetchData.Builder fetch) throws DecodingException {
        String name = reader.readUntil(NEXT_ELEMENT_END);
        char next = reader.nextChar();
        // Simple elements with no '[]' parameters.
        if (next != '[') {
            addNextName(fetch, name);
        } else {
            reader.consumeChar('[');

            String parameter = reader.readUntil(CLOSING_BRACKET);

            reader.consumeChar(']');

            Long firstOctet;
            Long numberOfOctets;
            if (reader.nextChar() == '<') {
                reader.consumeChar('<');
                firstOctet = reader.number();
                if (reader.nextChar() == '.') {
                    reader.consumeChar('.');
                    numberOfOctets = reader.nzNumber();
                } else {
                    numberOfOctets = null;
                }
                reader.consumeChar('>');
            } else {
                firstOctet = null;
                numberOfOctets = null;
            }

            BodyFetchElement bodyFetchElement = createBodyElement(parameter, firstOctet, numberOfOctets);
            boolean isPeek = isPeek(name);
            fetch.add(bodyFetchElement, isPeek);
        }
    }

    private FetchData.Builder addNextName(FetchData.Builder fetch, String name) throws DecodingException {
        String capitalizedName = name.toUpperCase(Locale.US);
        switch (capitalizedName) {
            case "FAST":
                return fetch.fetch(FLAGS, INTERNAL_DATE, SIZE);
            case "FULL":
                return fetch.fetch(FLAGS, INTERNAL_DATE, SIZE, ENVELOPE, BODY);
            case "ALL":
                return fetch.fetch(FLAGS, INTERNAL_DATE, SIZE, ENVELOPE);
            case "FLAGS":
                return fetch.fetch(FLAGS);
            case "RFC822.SIZE":
                return fetch.fetch(SIZE);
            case "ENVELOPE":
                return fetch.fetch(ENVELOPE);
            case "INTERNALDATE":
                return fetch.fetch(INTERNAL_DATE);
            case "BODY":
                return fetch.fetch(BODY);
            case "BODYSTRUCTURE":
                return fetch.fetch(BODY_STRUCTURE);
            case "UID":
                return fetch.fetch(UID);
            case "RFC822":
                return fetch.add(BodyFetchElement.createRFC822(), false);
            case "RFC822.HEADER":
                return fetch.add(BodyFetchElement.createRFC822Header(), true);
            case "RFC822.TEXT":
                return fetch.add(BodyFetchElement.createRFC822Text(), false);
            case "MODSEQ":
                return fetch.fetch(MODSEQ);
            case "EMAILID":
                return fetch.fetch(EMAILID);
            case "THREADID":
                return fetch.fetch(THREADID);
            case "SAVEDATE":
                return fetch.fetch(SAVEDATE);
            default:
                throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Invalid fetch attribute: " + name);
        }
    }

    private boolean isPeek(String name) throws DecodingException {
        switch (name.toUpperCase(Locale.US)) {
            case "BODY":
                return false;
            case "BODY.PEEK":
                return true;
            default:
                throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Invalid fetch attibute: " + name + "[]");
        }
    }

    private BodyFetchElement createBodyElement(String parameter, Long firstOctet, Long numberOfOctets) throws DecodingException {
        String responseName = "BODY[" + parameter + "]";
        FetchPartPathDecoder decoder = new FetchPartPathDecoder();
        decoder.decode(parameter);
        SectionType sectionType = decoder.getSpecifier();

        List<String> names = decoder.getNames();
        int[] path = decoder.getPath();
        return new BodyFetchElement(responseName, sectionType, path, names, firstOctet, numberOfOctets);
    }

    private char nextNonSpaceChar(ImapRequestLineReader request) throws DecodingException {
        char next = request.nextChar();
        while (next == ' ') {
            request.consume();
            next = request.nextChar();
        }
        return next;
    }

    @Override
    protected ImapMessage decode(ImapRequestLineReader request, Tag tag, boolean useUids, ImapSession session) throws DecodingException {
        IdRange[] idSet = request.parseIdRange(session);
        FetchData fetch = fetchRequest(request, useUids);

        // Check if we have VANISHED and and UID FETCH as its only allowed there
        //
        // See RFC5162 3.2. VANISHED UID FETCH Modifier
        if (fetch.getVanished() && !useUids) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "VANISHED only allowed in UID FETCH");
        }
        
        request.eol();

        return new FetchRequest(useUids, idSet, fetch, tag);
    }

}
