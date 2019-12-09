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
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapCommandParser;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * <strong>Note:</strong>
 * </p>
 */
public abstract class AbstractImapCommandParser implements ImapCommandParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractImapCommandParser.class);

    private final ImapCommand command;

    private final StatusResponseFactory statusResponseFactory;

    public AbstractImapCommandParser(ImapCommand command, StatusResponseFactory statusResponseFactory) {
        this.command = command;
        this.statusResponseFactory = statusResponseFactory;
    }

    public ImapCommand getCommand() {
        return command;
    }

    protected StatusResponse taggedNo(Tag tag, ImapCommand command, HumanReadableText displayTextKey, StatusResponse.ResponseCode code) {
        return statusResponseFactory.taggedNo(tag, command, displayTextKey, code);
    }

    /**
     * Parses a request into a command message for later processing.
     * 
     * @param request
     *            <code>ImapRequestLineReader</code>, not null
     * @return <code>ImapCommandMessage</code>, not null
     */
    @Override
    public final ImapMessage parse(ImapRequestLineReader request, Tag tag, ImapSession session) {
        if (!command.validForState(session.getState())) {
            return statusResponseFactory.taggedNo(tag, command, HumanReadableText.INVALID_COMMAND);
        }
        try {
            return decode(command, request, tag, session);
        } catch (DecodingException e) {
            LOGGER.debug("Cannot parse protocol ", e);
            return statusResponseFactory.taggedBad(tag, command, e.getKey());
        }
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
    protected abstract ImapMessage decode(ImapCommand command, ImapRequestLineReader request, Tag tag, ImapSession session) throws DecodingException;

}
