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
import java.util.List;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.GetACLRequest;
import org.apache.james.imap.message.response.ACLResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.MailboxMetaData;
import org.apache.james.mailbox.MessageManager.MailboxMetaData.FetchGroup;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

/**
 * GETACL Processor.
 */
public class GetACLProcessor extends AbstractMailboxProcessor<GetACLRequest> implements CapabilityImplementingProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(GetACLProcessor.class);

    private static final List<Capability> CAPABILITIES = ImmutableList.of(ImapConstants.SUPPORTS_ACL);

    public GetACLProcessor(ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory,
            MetricFactory metricFactory) {
        super(GetACLRequest.class, next, mailboxManager, factory, metricFactory);
    }

    @Override
    protected void processRequest(GetACLRequest request, ImapSession session, Responder responder) {

        final MailboxManager mailboxManager = getMailboxManager();
        final MailboxSession mailboxSession = session.getMailboxSession();
        final String mailboxName = request.getMailboxName();
        try {

            MailboxPath mailboxPath = PathConverter.forSession(session).buildFullPath(mailboxName);
            // Check that mailbox exists
            MessageManager messageManager = mailboxManager.getMailbox(mailboxPath, mailboxSession);
            /*
             * RFC 4314 section 6.
             * An implementation MUST make sure the ACL commands themselves do
             * not give information about mailboxes with appropriately
             * restricted ACLs. For example, when a user agent executes a GETACL
             * command on a mailbox that the user has no permission to LIST, the
             * server would respond to that request with the same error that
             * would be used if the mailbox did not exist, thus revealing no
             * existence information, much less the mailbox’s ACL.
             */
            if (!mailboxManager.hasRight(mailboxPath, MailboxACL.Right.Lookup, mailboxSession)) {
                no(request, responder, HumanReadableText.MAILBOX_NOT_FOUND);
            } else if (!mailboxManager.hasRight(mailboxPath, MailboxACL.Right.Administer, mailboxSession)) {
                /* RFC 4314 section 4. */
                Object[] params = new Object[] {
                        MailboxACL.Right.Administer.toString(),
                        request.getCommand().getName(),
                        mailboxName
                };
                HumanReadableText text = new HumanReadableText(HumanReadableText.UNSUFFICIENT_RIGHTS_KEY, HumanReadableText.UNSUFFICIENT_RIGHTS_DEFAULT_VALUE, params);
                no(request, responder, text);
            } else {
                MailboxMetaData metaData = messageManager.getMetaData(false, mailboxSession, FetchGroup.NO_COUNT);
                ACLResponse aclResponse = new ACLResponse(mailboxName, metaData.getACL());
                responder.respond(aclResponse);
                okComplete(request, responder);
                // FIXME should we send unsolicited responses here?
                // unsolicitedResponses(session, responder, false);
            }
        } catch (MailboxNotFoundException e) {
            no(request, responder, HumanReadableText.MAILBOX_NOT_FOUND);
        } catch (MailboxException e) {
            LOGGER.error("{} failed for mailbox {}", request.getCommand().getName(), mailboxName, e);
            no(request, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
        }

    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        return CAPABILITIES;
    }

    @Override
    protected Closeable addContextToMDC(GetACLRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "GET_ACL")
            .addToContext("mailbox", request.getMailboxName())
            .build();
    }
}
