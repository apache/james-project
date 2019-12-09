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

import java.util.List;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.BodyFetchElement;
import org.apache.james.imap.api.message.FetchData;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.SectionType;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.FetchPartPathDecoder;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.ImapRequestLineReader.CharacterValidator;
import org.apache.james.imap.message.request.FetchRequest;

/**
 * Parse FETCH commands
 */
public class FetchCommandParser extends AbstractUidCommandParser {
    private static final byte[] CHANGEDSINCE = "CHANGEDSINCE".getBytes();
    private static final byte[] VANISHED = "VANISHED".getBytes();

    public FetchCommandParser() {
        super(ImapCommand.selectedStateCommand(ImapConstants.FETCH_COMMAND_NAME));
    }

    /**
     * Create a {@link FetchData} by reading from the
     * {@link ImapRequestLineReader}
     *
     * @return fetchData
     */
    protected FetchData fetchRequest(ImapRequestLineReader request) throws DecodingException {
        FetchData fetch = new FetchData();

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

                next = request.nextChar();
                switch (next) {
                case 'C':
                    // Now check for the CHANGEDSINCE option which is part of CONDSTORE
                    request.consumeWord(new CharacterValidator() {
                        int pos = 0;
                        @Override
                        public boolean isValid(char chr) {
                            if (pos > CHANGEDSINCE.length) {
                                return false;
                            } else {
                                return CHANGEDSINCE[pos++] == ImapRequestLineReader.cap(chr);
                            }
                        }
                    });
                    fetch.setChangedSince(request.number(true));
                    
                    break;
                
                case 'V':
                    // Check for the VANISHED option which is part of QRESYNC
                    request.consumeWord(new CharacterValidator() {
                        int pos = 0;
                        @Override
                        public boolean isValid(char chr) {
                            if (pos > VANISHED.length) {
                                return false;
                            } else {
                                return VANISHED[pos++] == ImapRequestLineReader.cap(chr);
                            }
                        }
                    });
                    fetch.setVanished(true);
                    break;
                default:
                    break;
                }
               
                
                request.consumeChar(')');

            }
        } else {
            addNextElement(request, fetch);

        }

        return fetch;
    }

    private void addNextElement(ImapRequestLineReader reader, FetchData fetch) throws DecodingException {
        // String name = element.toString();
        String name = readWord(reader, " [)\r\n");
        char next = reader.nextChar();
        // Simple elements with no '[]' parameters.
        if (next != '[') {
            if ("FAST".equalsIgnoreCase(name)) {
                fetch.setFlags(true);
                fetch.setInternalDate(true);
                fetch.setSize(true);
            } else if ("FULL".equalsIgnoreCase(name)) {
                fetch.setFlags(true);
                fetch.setInternalDate(true);
                fetch.setSize(true);
                fetch.setEnvelope(true);
                fetch.setBody(true);
            } else if ("ALL".equalsIgnoreCase(name)) {
                fetch.setFlags(true);
                fetch.setInternalDate(true);
                fetch.setSize(true);
                fetch.setEnvelope(true);
            } else if ("FLAGS".equalsIgnoreCase(name)) {
                fetch.setFlags(true);
            } else if ("RFC822.SIZE".equalsIgnoreCase(name)) {
                fetch.setSize(true);
            } else if ("ENVELOPE".equalsIgnoreCase(name)) {
                fetch.setEnvelope(true);
            } else if ("INTERNALDATE".equalsIgnoreCase(name)) {
                fetch.setInternalDate(true);
            } else if ("BODY".equalsIgnoreCase(name)) {
                fetch.setBody(true);
            } else if ("BODYSTRUCTURE".equalsIgnoreCase(name)) {
                fetch.setBodyStructure(true);
            } else if ("UID".equalsIgnoreCase(name)) {
                fetch.setUid(true);
            } else if ("RFC822".equalsIgnoreCase(name)) {
                fetch.add(BodyFetchElement.createRFC822(), false);
            } else if ("RFC822.HEADER".equalsIgnoreCase(name)) {
                fetch.add(BodyFetchElement.createRFC822Header(), true);
            } else if ("RFC822.TEXT".equalsIgnoreCase(name)) {
                fetch.add(BodyFetchElement.createRFC822Text(), false);
            } else if ("MODSEQ".equalsIgnoreCase(name)) {
                fetch.setModSeq(true);
            } else {
                throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Invalid fetch attribute: " + name);
            }
        } else {
            reader.consumeChar('[');

            String parameter = readWord(reader, "]");

            reader.consumeChar(']');

            final Long firstOctet;
            final Long numberOfOctets;
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

            final BodyFetchElement bodyFetchElement = createBodyElement(parameter, firstOctet, numberOfOctets);
            final boolean isPeek = isPeek(name);
            fetch.add(bodyFetchElement, isPeek);
        }
    }

    private boolean isPeek(String name) throws DecodingException {
        final boolean isPeek;
        if ("BODY".equalsIgnoreCase(name)) {
            isPeek = false;
        } else if ("BODY.PEEK".equalsIgnoreCase(name)) {
            isPeek = true;
        } else {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Invalid fetch attibute: " + name + "[]");
        }
        return isPeek;
    }

    private BodyFetchElement createBodyElement(String parameter, Long firstOctet, Long numberOfOctets) throws DecodingException {
        final String responseName = "BODY[" + parameter + "]";
        FetchPartPathDecoder decoder = new FetchPartPathDecoder();
        decoder.decode(parameter);
        final SectionType sectionType = decoder.getSpecifier();

        final List<String> names = decoder.getNames();
        final int[] path = decoder.getPath();
        return new BodyFetchElement(responseName, sectionType, path, names, firstOctet, numberOfOctets);
    }

    private String readWord(ImapRequestLineReader request, String terminator) throws DecodingException {
        StringBuilder builder = new StringBuilder();
        char next = request.nextChar();
        while (terminator.indexOf(next) == -1) {
            builder.append(next);
            request.consume();
            next = request.nextChar();
        }
        return builder.toString();
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
    protected ImapMessage decode(ImapCommand command, ImapRequestLineReader request, Tag tag, boolean useUids, ImapSession session) throws DecodingException {
        IdRange[] idSet = request.parseIdRange(session);
        FetchData fetch = fetchRequest(request);

        // Check if we have VANISHED and and UID FETCH as its only allowed there
        //
        // See RFC5162 3.2. VANISHED UID FETCH Modifier
        if (fetch.getVanished() && !useUids) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "VANISHED only allowed in UID FETCH");
        }
        
        request.eol();

        return new FetchRequest(command, useUids, idSet, fetch, tag);
    }

}
