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
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.StatusDataItems;
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
        public static final ImapRequestLineReader.CharacterValidator INSTANCE = new ListCharValidator();

        @Override
        public boolean isValid(char chr) {
            if (ImapRequestLineReader.isListWildcard(chr)) {
                return true;
            }
            return super.isValid(chr);
        }
    }

    @Inject
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

        Pair<EnumSet<ListReturnOption>, Optional<StatusDataItems>> listReturnOptions = parseReturnOptions(request);
        request.eol();

        if (listOptions.isEmpty() && listReturnOptions.getLeft().isEmpty()) {
            return createMessage(referenceName, mailboxPattern, tag);
        }
        if (listOptions.contains(ListSelectOption.SPECIAL_USE)) {
            listReturnOptions.getLeft().add(ListReturnOption.SPECIAL_USE);
        }
        return new ListRequest(ImapConstants.LIST_COMMAND, referenceName, mailboxPattern, tag, listOptions, listReturnOptions.getLeft(), listReturnOptions.getRight());
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
            return readSelectS(request);
        }
        throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS,
            "Unknown select option: '" + request.consumeWord(ImapRequestLineReader.NoopCharValidator.INSTANCE) + "'");
    }

    private ListSelectOption readSelectS(ImapRequestLineReader request) throws DecodingException {
        request.consume();
        char c = request.nextChar();
        if (c == 'u' || c == 'U') {
            consumeSubscribed(request);
            return ListSelectOption.SUBSCRIBED;
        } else {
            consumeSpecialUse(request);
            return ListSelectOption.SPECIAL_USE;
        }
    }

    private Pair<ListReturnOption, Optional<StatusDataItems>> readS(ImapRequestLineReader request) throws DecodingException {
        request.consume();
        char c = request.nextWordChar();
        if (c == 'T' || c == 't') {
            return readStatus(request);
        } else if (c == 'P' || c == 'p') {
            return Pair.of(readSpecialUse(request), Optional.empty());
        } else {
            return Pair.of(readReturnSubscribed(request), Optional.empty());
        }
    }

    private Pair<ListReturnOption, Optional<StatusDataItems>> readStatus(ImapRequestLineReader request) throws DecodingException {
        // 'S' is already consummed
        assertChar(request, 'T', 't');
        assertChar(request, 'A', 'a');
        assertChar(request, 'T', 't');
        assertChar(request, 'U', 'u');
        assertChar(request, 'S', 's');
        return Pair.of(ListReturnOption.STATUS, Optional.of(StatusCommandParser.statusDataItems(request)));
    }

    private ListReturnOption readSpecialUse(ImapRequestLineReader request) throws DecodingException {
        consumeSpecialUse(request);
        return ListReturnOption.SPECIAL_USE;
    }

    private void consumeSpecialUse(ImapRequestLineReader request) throws DecodingException {
        // 'S' is already consummed
        assertChar(request, 'P', 'p');
        assertChar(request, 'E', 'e');
        assertChar(request, 'C', 'c');
        assertChar(request, 'I', 'i');
        assertChar(request, 'A', 'a');
        assertChar(request, 'L', 'l');
        assertChar(request, '-', '-');
        assertChar(request, 'U', 'u');
        assertChar(request, 'S', 's');
        assertChar(request, 'E', 'e');
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
        assertChar(request, 'M', 'm');
        assertChar(request, 'O', 'o');
        assertChar(request, 'T', 't');
        assertChar(request, 'E', 'e');
        return ListSelectOption.REMOTE;
    }

    private ListSelectOption readRecursivematch(ImapRequestLineReader request) throws DecodingException {
        assertChar(request, 'C', 'c');
        assertChar(request, 'U', 'u');
        assertChar(request, 'R', 'r');
        assertChar(request, 'S', 's');
        assertChar(request, 'I', 'i');
        assertChar(request, 'V', 'v');
        assertChar(request, 'E', 'e');
        assertChar(request, 'M', 'm');
        assertChar(request, 'A', 'a');
        assertChar(request, 'T', 't');
        assertChar(request, 'C', 'c');
        assertChar(request, 'H', 'h');
        return ListSelectOption.RECURSIVEMATCH;
    }

    private Pair<EnumSet<ListReturnOption>, Optional<StatusDataItems>> parseReturnOptions(ImapRequestLineReader request) throws DecodingException {
        if (request.nextWordCharLenient().isPresent()) {
            String returnKey = request.consumeWord(AtomCharValidator.INSTANCE);
            if ("RETURN".equalsIgnoreCase(returnKey)) {
                EnumSet<ListReturnOption> listReturnOptions = EnumSet.noneOf(ListReturnOption.class);
                request.nextWordChar();
                request.consumeChar('(');
                request.nextWordChar();

                Optional<StatusDataItems> statusDataItems = Optional.empty();
                while (request.nextChar() != ')') {
                    Pair<ListReturnOption, Optional<StatusDataItems>> listReturnOption = parseListReturnOption(request);
                    listReturnOptions.add(listReturnOption.getLeft());
                    if (listReturnOption.getRight().isPresent()) {
                        statusDataItems = listReturnOption.getRight();
                    }
                    request.nextWordChar();
                }
                request.consumeChar(')');
                return Pair.of(listReturnOptions, statusDataItems);
            } else {
                throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown '" + returnKey + "' option'");
            }
        }
        return Pair.of(EnumSet.noneOf(ListReturnOption.class), Optional.empty());
    }

    private Pair<ListReturnOption, Optional<StatusDataItems>> parseListReturnOption(ImapRequestLineReader request) throws DecodingException {
        char c = request.nextWordChar();
        if (c == 'c' || c == 'C') {
            return Pair.of(readChildren(request), Optional.empty());
        }
        if (c == 's' || c == 'S') {
            return readS(request);
        }
        if (c == 'm' || c == 'M') {
            return Pair.of(readMyRight(request), Optional.empty());
        }
        throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS,
            "Unknown return option: '" + request.consumeWord(ImapRequestLineReader.NoopCharValidator.INSTANCE) + "'");
    }

    private ListReturnOption readChildren(ImapRequestLineReader request) throws DecodingException {
        assertChar(request, 'C', 'c');
        assertChar(request, 'H', 'h');
        assertChar(request, 'I', 'i');
        assertChar(request, 'L', 'l');
        assertChar(request, 'D', 'd');
        assertChar(request, 'R', 'r');
        assertChar(request, 'E', 'e');
        assertChar(request, 'N', 'n');
        return ListReturnOption.CHILDREN;
    }

    private ListReturnOption readMyRight(ImapRequestLineReader request) throws DecodingException {
        assertChar(request, 'M', 'm');
        assertChar(request, 'Y', 'y');
        assertChar(request, 'R', 'r');
        assertChar(request, 'I', 'i');
        assertChar(request, 'G', 'g');
        assertChar(request, 'H', 'h');
        assertChar(request, 'T', 't');
        assertChar(request, 'S', 's');
        return ListReturnOption.MYRIGHTS;
    }

    private ListReturnOption readReturnSubscribed(ImapRequestLineReader request) throws DecodingException {
        consumeSubscribed(request);
        return ListReturnOption.SUBSCRIBED;
    }

    private void consumeSubscribed(ImapRequestLineReader request) throws DecodingException {
        // s is already consumed
        assertChar(request, 'U', 'u');
        assertChar(request, 'B', 'b');
        assertChar(request, 'S', 's');
        assertChar(request, 'C', 'c');
        assertChar(request, 'R', 'r');
        assertChar(request, 'I', 'i');
        assertChar(request, 'B', 'b');
        assertChar(request, 'E', 'e');
        assertChar(request, 'D', 'd');
    }

    private void assertChar(ImapRequestLineReader reader, char c, char cUp) throws DecodingException {
        char c2 = reader.consume();
        if (c2 != c && c2 != cUp) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unexpected token in select option. Expecting " + c + " got " + c2);
        }
    }
}
