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
package org.apache.james.imap.decode.main;

import java.util.Optional;

import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapCommandParser;
import org.apache.james.imap.decode.ImapCommandParserFactory;
import org.apache.james.imap.decode.ImapDecoder;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.scheduler.Schedulers;

/**
 * {@link ImapDecoder} implementation which parse the data via lookup the right
 * {@link ImapCommandParser} via an {@link ImapCommandParserFactory}. The
 * response will get generated via the {@link StatusResponseFactory}.
 */
public class DefaultImapDecoder implements ImapDecoder {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultImapDecoder.class);

    private final StatusResponseFactory responseFactory;

    private final ImapCommandParserFactory imapCommands;

    private final int maxInvalidCommands;

    private static final String INVALID_COMMAND_COUNT = "INVALID_COMMAND_COUNT";
    private static final int DEFAULT_MAX_INVALID_COMMANDS = 9;

    public DefaultImapDecoder(StatusResponseFactory responseFactory, ImapCommandParserFactory imapCommands) {
        this(responseFactory, imapCommands, DEFAULT_MAX_INVALID_COMMANDS);
    }

    public DefaultImapDecoder(StatusResponseFactory responseFactory, ImapCommandParserFactory imapCommands, int maxInvalidCommands) {
        this.responseFactory = responseFactory;
        this.imapCommands = imapCommands;
        this.maxInvalidCommands = maxInvalidCommands;
    }

    @Override
    public ImapMessage decode(ImapRequestLineReader request, ImapSession session) {
        try {
            Tag tag = request.tag();
            return decodeCommandTagged(request, tag, session);
        } catch (DecodingException e) {
            LOGGER.debug("Cannot parse tag", e);
            return unknownCommand(null, session);
        }
    }

    private ImapMessage decodeCommandTagged(ImapRequestLineReader request, Tag tag, ImapSession session) {
        LOGGER.debug("Got <tag>: {}", tag);
        try {
            String commandName = request.atom();
            return decodeCommandNamed(request, tag, commandName, session);
        } catch (DecodingException e) {
            LOGGER.info("Error during initial request parsing", e);
            return unknownCommand(tag, session);
        }
    }

    private ImapMessage unknownCommand(Tag tag, ImapSession session) {
        int count = retrieveUnknownCommandCount(session) + 1;

        if (count > maxInvalidCommands || session.getState() == ImapSessionState.NON_AUTHENTICATED) {
            ImapMessage message = responseFactory.bye(HumanReadableText.BYE_UNKNOWN_COMMAND);
            session.cancelOngoingProcessing();
            session.logout()
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(); // Avoid synchronously calling this onto the event loop
            return message;
        }
        session.setAttribute(INVALID_COMMAND_COUNT, count);
        if (tag == null) {
            return responseFactory.untaggedBad(HumanReadableText.UNKNOWN_COMMAND);
        }
        return responseFactory.taggedBad(tag, null, HumanReadableText.UNKNOWN_COMMAND);
    }

    private int retrieveUnknownCommandCount(ImapSession session) {
        return Optional.ofNullable(session.getAttribute(INVALID_COMMAND_COUNT))
            .map(Integer.class::cast)
            .orElse(0);
    }

    private ImapMessage decodeCommandNamed(ImapRequestLineReader request, Tag tag, String commandName, ImapSession session) {
        LOGGER.debug("Got <command>: {}", commandName);
        ImapCommandParser command = imapCommands.getParser(commandName);
        if (command == null) {
            LOGGER.info("Missing command implementation for commmand {}", commandName);
            return unknownCommand(tag, session);
        }
        ImapMessage message = command.parse(request, tag, session);
        Object count = session.getAttribute(INVALID_COMMAND_COUNT);
        if (count == null || (int) count > 0) {
            session.setAttribute(INVALID_COMMAND_COUNT, 0);
        }
        return message;
    }
}
