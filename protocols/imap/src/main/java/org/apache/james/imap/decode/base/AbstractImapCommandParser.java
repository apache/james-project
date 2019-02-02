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

package org.apache.james.imap.decode.base;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.MessagingImapCommandParser;
import org.apache.james.protocols.imap.DecodingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * <strong>Note:</strong>
 * </p>
 */
public abstract class AbstractImapCommandParser implements MessagingImapCommandParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractImapCommandParser.class);

    private final ImapCommand command;

    private StatusResponseFactory statusResponseFactory;

    public AbstractImapCommandParser(ImapCommand command) {
        super();
        this.command = command;
    }

    public ImapCommand getCommand() {
        return command;
    }

    @Override
    public final StatusResponseFactory getStatusResponseFactory() {
        return statusResponseFactory;
    }

    @Override
    public final void setStatusResponseFactory(StatusResponseFactory statusResponseFactory) {
        this.statusResponseFactory = statusResponseFactory;
    }

    /**
     * Parses a request into a command message for later processing.
     * 
     * @param request
     *            <code>ImapRequestLineReader</code>, not null
     * @return <code>ImapCommandMessage</code>, not null
     */
    @Override
    public final ImapMessage parse(ImapRequestLineReader request, String tag, ImapSession session) {
        ImapMessage result;
        if (!command.validForState(session.getState())) {
            result = statusResponseFactory.taggedNo(tag, command, HumanReadableText.INVALID_COMMAND);
        } else {
            try {

                result = decode(command, request, tag, session);
            } catch (DecodingException e) {
                LOGGER.debug("Cannot parse protocol ", e);
                result = statusResponseFactory.taggedBad(tag, command, e.getKey());
            }
        }
        return result;
    }

    /**
     * Parses a request into a command message for later processing.
     * 
     * @param command
     *            <code>ImapCommand</code> to be parsed, not null
     * @param request
     *            <code>ImapRequestLineReader</code>, not null
     * @param tag
     *            command tag, not null
     * @param session
     *            imap session
     * @return <code>ImapCommandMessage</code>, not null
     * @throws DecodingException
     *             if the request cannot be parsed
     */
    protected abstract ImapMessage decode(ImapCommand command, ImapRequestLineReader request, String tag, ImapSession session) throws DecodingException;

}
