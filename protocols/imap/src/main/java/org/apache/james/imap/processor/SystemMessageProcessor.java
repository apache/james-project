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

import java.io.Closeable;

import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.SystemMessage;
import org.apache.james.imap.processor.base.AbstractChainedProcessor;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.util.MDCBuilder;

/**
 * Processes system messages unrelated to IMAP.
 */
public class SystemMessageProcessor extends AbstractChainedProcessor<SystemMessage> {

    private final MailboxManager mailboxManager;

    public SystemMessageProcessor(ImapProcessor next, MailboxManager mailboxManager) {
        super(SystemMessage.class, next);
        this.mailboxManager = mailboxManager;
    }

    @Override
    protected void doProcess(SystemMessage message, Responder responder, ImapSession session) {
        try {
            switch (message) {
            case FORCE_LOGOUT:
                forceLogout(session);
                break;
            default:
                session.getLog().info("Unknown system message " + message);
                break;
            }
        } catch (MailboxException e) {
            session.getLog().error("Cannot force logout", e);
        }
    }

    /**
     * Forces a logout of any mailbox session.
     * 
     * @param imapSession
     *            not null
     * @throws MailboxException
     *             when forced logout fails
     */
    private void forceLogout(ImapSession imapSession) throws MailboxException {
        final MailboxSession session = ImapSessionUtils.getMailboxSession(imapSession);
        if (session == null) {
            if (imapSession.getLog().isTraceEnabled()) {
                imapSession.getLog().trace("No mailbox session so no force logout needed");
            }
        } else {
            session.close();
            mailboxManager.logout(session, true);
        }
    }

    @Override
    protected Closeable addContextToMDC(SystemMessage message) {
        return MDCBuilder.create()
            .addContext(MDCBuilder.ACTION, "SYSTEM_MESSAGE")
            .addContext("message", message)
            .build();
    }
}
