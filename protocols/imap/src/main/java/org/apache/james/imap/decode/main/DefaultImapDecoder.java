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

import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.ImapCommandParser;
import org.apache.james.imap.decode.ImapCommandParserFactory;
import org.apache.james.imap.decode.ImapDecoder;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.protocols.imap.DecodingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private final static String INVALID_COMMAND_COUNT = "INVALID_COMMAND_COUNT";
    public final static int DEFAULT_MAX_INVALID_COMMANDS = 9;

    public DefaultImapDecoder(StatusResponseFactory responseFactory, ImapCommandParserFactory imapCommands) {
        this(responseFactory, imapCommands, DEFAULT_MAX_INVALID_COMMANDS);
    }

    public DefaultImapDecoder(StatusResponseFactory responseFactory, ImapCommandParserFactory imapCommands, int maxInvalidCommands) {
        this.responseFactory = responseFactory;
        this.imapCommands = imapCommands;
        this.maxInvalidCommands = maxInvalidCommands;
    }

    /**
     * @see
     * org.apache.james.imap.decode.ImapDecoder#decode(org.apache.james.imap.decode.ImapRequestLineReader,
     * org.apache.james.imap.api.process.ImapSession)
     */
    public ImapMessage decode(ImapRequestLineReader request, ImapSession session) {
        ImapMessage message;
        try {
            final String tag = request.tag();
            message = decodeCommandTagged(request, tag, session);
        } catch (DecodingException e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Cannot parse tag", e);
            }
            message = unknownCommand(null, session);
        }
        return message;
    }

    private ImapMessage decodeCommandTagged(ImapRequestLineReader request, String tag, ImapSession session) {
        ImapMessage message;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Got <tag>: " + tag);
        }
        try {
            final String commandName = request.atom();
            message = decodeCommandNamed(request, tag, commandName, session);
        } catch (DecodingException e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Error during initial request parsing", e);
            }
            message = unknownCommand(tag, session);
        }
        return message;
    }

    private ImapMessage unknownCommand(String tag, ImapSession session) {
        ImapMessage message;
        Object c = session.getAttribute(INVALID_COMMAND_COUNT);
        int count = 0;
        if (c != null) {
            count = (Integer) c;
        }
        count++;
        if (count > maxInvalidCommands || session.getState() == ImapSessionState.NON_AUTHENTICATED) {
            message = responseFactory.bye(HumanReadableText.BYE_UNKNOWN_COMMAND);
            session.logout();
        } else {
            session.setAttribute(INVALID_COMMAND_COUNT, count);
            if (tag == null) {
                message = responseFactory.untaggedBad(HumanReadableText.UNKNOWN_COMMAND);
            } else {
                message = responseFactory.taggedBad(tag, null, HumanReadableText.UNKNOWN_COMMAND);
            }

        }

        return message;
    }

    private ImapMessage decodeCommandNamed(ImapRequestLineReader request, String tag, String commandName, ImapSession session) {
        ImapMessage message;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Got <command>: " + commandName);
        }
        final ImapCommandParser command = imapCommands.getParser(commandName);
        if (command == null) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Missing command implementation for commmand " + commandName);
            }
            message = unknownCommand(tag, session);
        } else {
            message = command.parse(request, tag, session);
            session.setAttribute(INVALID_COMMAND_COUNT, 0);
        }
        return message;
    }
}
