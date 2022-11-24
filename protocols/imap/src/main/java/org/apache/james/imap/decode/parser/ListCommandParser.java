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

import java.util.EnumSet;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.ImapRequestLineReader.AtomCharValidator;
import org.apache.james.imap.message.request.ListRequest;
import org.apache.james.imap.message.request.ListRequest.ListReturnOption;
import org.apache.james.imap.message.request.ListRequest.ListSelectOption;

/**
 * Parse LIST commands
 */
public class ListCommandParser extends AbstractUidCommandParser {

    private static class ListCharValidator extends AtomCharValidator {
        public static ImapRequestLineReader.CharacterValidator INSTANCE = new ListCharValidator();

        @Override
        public boolean isValid(char chr) {
            if (ImapRequestLineReader.isListWildcard(chr)) {
                return true;
            }
            return super.isValid(chr);
        }
    }

    public ListCommandParser(StatusResponseFactory statusResponseFactory) {
        super(ImapConstants.LIST_COMMAND, statusResponseFactory);
    }

    protected ListCommandParser(ImapCommand command, StatusResponseFactory statusResponseFactory) {
        super(command, statusResponseFactory);
    }

    /**
     * Reads an argument of type "list_mailbox" from the request, which is the
     * second argument for a LIST or LSUB command. Valid values are a "string"
     * argument, an "atom" with wildcard characters.
     * 
     * @return An argument of type "list_mailbox"
     */
    public String listMailbox(ImapRequestLineReader request) throws DecodingException {
        char next = request.nextWordChar();
        switch (next) {
        case '"':
            return request.consumeQuoted();
        case '{':
            return request.consumeLiteral(null);
        default:
            return request.consumeWord(ListCharValidator.INSTANCE);
        }
    }

    @Override
    protected ImapMessage decode(ImapRequestLineReader request, Tag tag, boolean useUids, ImapSession session) throws DecodingException {
        EnumSet<ListSelectOption> listOptions = parseSelectOptions(request);
        String referenceName = request.mailbox();
        String mailboxPattern = listMailbox(request);

        EnumSet<ListReturnOption> listReturnOptions = parseReturnOptions(request);
        request.eol();

        if (listOptions.isEmpty() && listReturnOptions.isEmpty()) {
            return createMessage(referenceName, mailboxPattern, tag);
        }
        return new ListRequest(ImapConstants.LIST_COMMAND, referenceName, mailboxPattern, tag, listOptions, listReturnOptions);
    }

    protected ImapMessage createMessage(String referenceName, String mailboxPattern, Tag tag) {
        return new ListRequest(referenceName, mailboxPattern, tag);
    }

    private EnumSet<ListSelectOption> parseSelectOptions(ImapRequestLineReader request) throws DecodingException {
        EnumSet<ListSelectOption> listOptions = EnumSet.noneOf(ListSelectOption.class);
        if (request.nextWordChar() != '(') {
            return listOptions;
        }

        request.consumeChar('(');
        request.nextWordChar();

        while (request.nextChar() != ')') {
            listOptions.add(parseListSelectOption(request));
            request.nextWordChar();
        }
        request.consumeChar(')');
        return listOptions;
    }

    private ListSelectOption parseListSelectOption(ImapRequestLineReader request) throws DecodingException {
        char c = request.nextWordChar();
        if (c == 'r' || c == 'R') {
            return readR(request);
        }
        if (c == 's' || c == 'S') {
            return readSubscribed(request);
        }
        throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS,
            "Unknown select option: '" + request.consumeWord(ImapRequestLineReader.NoopCharValidator.INSTANCE) + "'");
    }

    private ListSelectOption readSubscribed(ImapRequestLineReader request) throws DecodingException {
        assertChar(request, 'S');
        assertChar(request, 'U');
        assertChar(request, 'B');
        assertChar(request, 'S');
        assertChar(request, 'C');
        assertChar(request, 'R');
        assertChar(request, 'I');
        assertChar(request, 'B');
        assertChar(request, 'E');
        assertChar(request, 'D');
        return ListSelectOption.SUBSCRIBED;
    }

    private ListSelectOption readR(ImapRequestLineReader request) throws DecodingException {
        request.consume();
        char c2 = request.nextChar();
        if (c2 == 'e' || c2 == 'E') {
            request.consume();
            char c3 = request.nextChar();
            if (c3 == 'm' || c3 == 'M') {
                return readRemote(request);
            } else {
                return readRecursivematch(request);
            }
        }
        throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS,
            "Unknown select option: '" + request.consumeWord(ImapRequestLineReader.NoopCharValidator.INSTANCE) + "'");
    }

    private ListSelectOption readRemote(ImapRequestLineReader request) throws DecodingException {
        assertChar(request, 'M');
        assertChar(request, 'O');
        assertChar(request, 'T');
        assertChar(request, 'E');
        return ListSelectOption.REMOTE;
    }

    private ListSelectOption readRecursivematch(ImapRequestLineReader request) throws DecodingException {
        assertChar(request, 'C');
        assertChar(request, 'U');
        assertChar(request, 'R');
        assertChar(request, 'S');
        assertChar(request, 'I');
        assertChar(request, 'V');
        assertChar(request, 'E');
        assertChar(request, 'M');
        assertChar(request, 'A');
        assertChar(request, 'T');
        assertChar(request, 'C');
        assertChar(request, 'H');
        return ListSelectOption.RECURSIVEMATCH;
    }

    private EnumSet<ListReturnOption> parseReturnOptions(ImapRequestLineReader request) throws DecodingException {
        if (request.nextWordCharLenient().isPresent()) {
            String returnKey = request.consumeWord(AtomCharValidator.INSTANCE);
            if ("RETURN".equalsIgnoreCase(returnKey)) {
                EnumSet<ListReturnOption> listReturnOptions = EnumSet.noneOf(ListReturnOption.class);
                request.nextWordChar();
                request.consumeChar('(');
                request.nextWordChar();

                while (request.nextChar() != ')') {
                    listReturnOptions.add(parseListReturnOption(request));
                    request.nextWordChar();
                }
                request.consumeChar(')');
                return listReturnOptions;
            } else {
                throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown '" + returnKey + "' option'");
            }
        }
        return EnumSet.noneOf(ListReturnOption.class);
    }

    private ListReturnOption parseListReturnOption(ImapRequestLineReader request) throws DecodingException {
        char c = request.nextWordChar();
        if (c == 'c' || c == 'C') {
            return readChildren(request);
        }
        if (c == 's' || c == 'S') {
            return readReturnSubscribed(request);
        }
        throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS,
            "Unknown return option: '" + request.consumeWord(ImapRequestLineReader.NoopCharValidator.INSTANCE) + "'");
    }

    private ListReturnOption readChildren(ImapRequestLineReader request) throws DecodingException {
        assertChar(request, 'C');
        assertChar(request, 'H');
        assertChar(request, 'I');
        assertChar(request, 'L');
        assertChar(request, 'D');
        assertChar(request, 'R');
        assertChar(request, 'E');
        assertChar(request, 'N');
        return ListReturnOption.CHILDREN;
    }

    private ListReturnOption readReturnSubscribed(ImapRequestLineReader request) throws DecodingException {
        assertChar(request, 'S');
        assertChar(request, 'U');
        assertChar(request, 'B');
        assertChar(request, 'S');
        assertChar(request, 'C');
        assertChar(request, 'R');
        assertChar(request, 'I');
        assertChar(request, 'B');
        assertChar(request, 'E');
        assertChar(request, 'D');
        return ListReturnOption.SUBSCRIBED;
    }

    private void assertChar(ImapRequestLineReader reader, char c) throws DecodingException {
        char c2 = reader.consume();
        if (Character.toUpperCase(c) != Character.toUpperCase(c2)) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unexpected token in select option. Expecting " + c + " got " + c2);
        }
    }
}
