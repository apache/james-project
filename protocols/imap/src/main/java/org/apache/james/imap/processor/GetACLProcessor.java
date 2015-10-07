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

import java.util.Collections;
import java.util.List;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.GetACLRequest;
import org.apache.james.imap.message.response.ACLResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.MetaData;
import org.apache.james.mailbox.MessageManager.MetaData.FetchGroup;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.SimpleMailboxACL.Rfc4314Rights;
import org.slf4j.Logger;

/**
 * GETACL Processor.
 * 
 */
public class GetACLProcessor extends AbstractMailboxProcessor<GetACLRequest> implements CapabilityImplementingProcessor {

    private static final List<String> CAPABILITIES = Collections.singletonList(ImapConstants.SUPPORTS_ACL);

    public GetACLProcessor(ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory) {
        super(GetACLRequest.class, next, mailboxManager, factory);
    }

    @Override
    protected void doProcess(GetACLRequest message, ImapSession session, String tag, ImapCommand command, Responder responder) {

        final MailboxManager mailboxManager = getMailboxManager();
        final MailboxSession mailboxSession = ImapSessionUtils.getMailboxSession(session);
        final String mailboxName = message.getMailboxName();
        try {

            MailboxPath mailboxPath = buildFullPath(session, mailboxName);
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
            if (!mailboxManager.hasRight(mailboxPath, Rfc4314Rights.l_Lookup_RIGHT, mailboxSession)) {
                no(command, tag, responder, HumanReadableText.MAILBOX_NOT_FOUND);
            }
            /* RFC 4314 section 4. */
            else if (!mailboxManager.hasRight(mailboxPath, Rfc4314Rights.a_Administer_RIGHT, mailboxSession)) {
                Object[] params = new Object[] {
                        Rfc4314Rights.a_Administer_RIGHT.toString(),
                        command.getName(),
                        mailboxName
                };
                HumanReadableText text = new HumanReadableText(HumanReadableText.UNSUFFICIENT_RIGHTS_KEY, HumanReadableText.UNSUFFICIENT_RIGHTS_DEFAULT_VALUE, params);
                no(command, tag, responder, text);
            }
            else {
                MetaData metaData = messageManager.getMetaData(false, mailboxSession, FetchGroup.NO_COUNT);
                ACLResponse aclResponse = new ACLResponse(mailboxName, metaData.getACL());
                responder.respond(aclResponse);
                okComplete(command, tag, responder);
                // FIXME should we send unsolicited responses here?
                // unsolicitedResponses(session, responder, false);
            }
        } catch (MailboxNotFoundException e) {
            no(command, tag, responder, HumanReadableText.MAILBOX_NOT_FOUND);
        } catch (MailboxException e) {
            Logger log = session.getLog();
            if (log.isInfoEnabled()) {
                log.info(command.getName() +" failed for mailbox " + mailboxName, e);
            }
            no(command, tag, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
        }

    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.james.imap.processor.CapabilityImplementingProcessor#
     * getImplementedCapabilities(org.apache.james.imap.api.process.ImapSession)
     */
    public List<String> getImplementedCapabilities(ImapSession session) {
        return CAPABILITIES;
    }

}
