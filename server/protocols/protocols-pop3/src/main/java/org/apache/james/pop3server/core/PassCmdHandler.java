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
package org.apache.james.pop3server.core;

import java.io.IOException;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.pop3server.mailbox.MailboxAdapter;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.lib.POP3BeforeSMTPHelper;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.core.AbstractPassCmdHandler;
import org.apache.james.protocols.pop3.mailbox.Mailbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link PassCmdHandler} which also handles POP3 Before SMTP
 * 
 */
public class PassCmdHandler extends AbstractPassCmdHandler  {
    private static final Logger LOGGER = LoggerFactory.getLogger(PassCmdHandler.class);

    private MailboxManager manager;

    @Inject
    public void setMailboxManager(@Named("mailboxmanager") MailboxManager manager) {
        this.manager = manager;
    }

    @Override
    public Response onCommand(POP3Session session, Request request) {
        Response response =  super.onCommand(session, request);
        if (POP3Response.OK_RESPONSE.equals(response.getRetCode())) {
            POP3BeforeSMTPHelper.addIPAddress(session.getRemoteAddress().getAddress().getHostAddress());
        }
        return response;
    }


    @Override
    protected Mailbox auth(POP3Session session, String username, String password) throws Exception {
        MailboxSession mSession = null;
        try {
            mSession = manager.login(session.getUser(), password);
            manager.startProcessingRequest(mSession);
            MailboxPath inbox = MailboxPath.inbox(mSession);
            
            // check if the mailbox exists, if not create it
            if (!manager.mailboxExists(inbox, mSession)) {
                Optional<MailboxId> mailboxId = manager.createMailbox(inbox, mSession);
                LOGGER.info("Provisioning INBOX. {} created.", mailboxId);
            }
            MessageManager mailbox = manager.getMailbox(MailboxPath.inbox(mSession), mSession);
            return new MailboxAdapter(manager, mailbox, mSession);
        } catch (BadCredentialsException e) {
            return null;
        } catch (MailboxException e) {
            throw new IOException("Unable to access mailbox for user " + session.getUser(), e);
        } finally {
            if (mSession != null) {
                manager.endProcessingRequest(mSession);
            }
        }

    }

}
