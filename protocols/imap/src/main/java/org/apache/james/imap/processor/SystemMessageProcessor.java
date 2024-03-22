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

package org.apache.james.imap.processor;

import jakarta.inject.Inject;

import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.SystemMessage;
import org.apache.james.imap.processor.base.AbstractProcessor;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

/**
 * Processes system messages unrelated to IMAP.
 */
public class SystemMessageProcessor extends AbstractProcessor<SystemMessage> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SystemMessageProcessor.class);

    private final MailboxManager mailboxManager;

    @Inject
    public SystemMessageProcessor(MailboxManager mailboxManager) {
        super(SystemMessage.class);
        this.mailboxManager = mailboxManager;
    }

    @Override
    protected Mono<Void> doProcess(SystemMessage message, Responder responder, ImapSession session) {
        switch (message) {
            case FORCE_LOGOUT:
                forceLogout(session);
                break;
            default:
                LOGGER.info("Unknown system message {}", message);
                break;
        }
        return Mono.empty();
    }

    /**
     * Forces a logout of any mailbox session.
     * 
     * @param imapSession
     *            not null
     */
    private void forceLogout(ImapSession imapSession) {
        final MailboxSession session = imapSession.getMailboxSession();
        if (session == null) {
            LOGGER.trace("No mailbox session so no force logout needed");
        }
    }

    @Override
    protected MDCBuilder mdc(SystemMessage message) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "SYSTEM_MESSAGE")
            .addToContext("message", message.toString());
    }
}
