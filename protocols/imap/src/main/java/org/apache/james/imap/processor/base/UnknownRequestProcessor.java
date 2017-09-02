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

package org.apache.james.imap.processor.base;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConfiguration;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.request.ImapRequest;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnknownRequestProcessor implements ImapProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(UnknownRequestProcessor.class);

    private final StatusResponseFactory factory;

    public UnknownRequestProcessor(StatusResponseFactory factory) {
        super();
        this.factory = factory;
    }

    public ImapResponseMessage process(ImapMessage message) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Unknown message: " + message);
        }
        final ImapResponseMessage result;
        if (message instanceof ImapRequest) {
            ImapRequest request = (ImapRequest) message;
            final String tag = request.getTag();
            final ImapCommand command = request.getCommand();
            result = factory.taggedBad(tag, command, HumanReadableText.UNKNOWN_COMMAND);
        } else {
            result = factory.untaggedBad(HumanReadableText.UNKNOWN_COMMAND);
        }
        return result;
    }

    public void process(ImapMessage message, Responder responder, ImapSession session) {
        final ImapResponseMessage response = process(message);
        responder.respond(response);
    }

    @Override
    public void configure(ImapConfiguration imapConfiguration) {

    }
}
