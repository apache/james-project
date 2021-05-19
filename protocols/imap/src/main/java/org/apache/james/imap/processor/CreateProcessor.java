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

import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.CreateRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.TooLongMailboxNameException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateProcessor extends AbstractMailboxProcessor<CreateRequest> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CreateProcessor.class);

    public CreateProcessor(ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory,
            MetricFactory metricFactory) {
        super(CreateRequest.class, next, mailboxManager, factory, metricFactory);
    }

    @Override
    protected void processRequest(CreateRequest request, ImapSession session, Responder responder) {
        final MailboxPath mailboxPath = PathConverter.forSession(session).buildFullPath(request.getMailboxName());
        try {
            final MailboxManager mailboxManager = getMailboxManager();
            mailboxManager.createMailbox(mailboxPath, session.getMailboxSession());
            unsolicitedResponses(session, responder, false);
            okComplete(request, responder);
        } catch (MailboxExistsException e) {
            LOGGER.debug("Create failed for mailbox {} as it already exists", mailboxPath, e);
            no(request, responder, HumanReadableText.MAILBOX_EXISTS);
        } catch (TooLongMailboxNameException e) {
            LOGGER.debug("The mailbox name length is over limit: {}", mailboxPath.getName(), e);
            taggedBad(request, responder, HumanReadableText.FAILURE_MAILBOX_NAME);
        } catch (MailboxException e) {
            LOGGER.error("Create failed for mailbox {}", mailboxPath, e);
            no(request, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
        }
    }

    @Override
    protected Closeable addContextToMDC(CreateRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "CREATE")
            .addToContext("mailbox", request.getMailboxName())
            .build();
    }
}
