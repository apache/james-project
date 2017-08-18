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

import java.util.List;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.DeleteACLRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL.EditMode;
import org.apache.james.mailbox.model.MailboxACL.MailboxACLEntryKey;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.SimpleMailboxACL;
import org.apache.james.mailbox.model.SimpleMailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.SimpleMailboxACL.SimpleMailboxACLEntryKey;
import org.apache.james.metrics.api.MetricFactory;

import com.google.common.collect.ImmutableList;

/**
 * DELETEACL Processor.
 * 
 * @author Peter Palaga
 */
public class DeleteACLProcessor extends AbstractMailboxProcessor<DeleteACLRequest> implements CapabilityImplementingProcessor {

    private static final List<String> CAPABILITIES = ImmutableList.of(ImapConstants.SUPPORTS_ACL);

    public DeleteACLProcessor(ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory,
            MetricFactory metricFactory) {
        super(DeleteACLRequest.class, next, mailboxManager, factory, metricFactory);
    }

    @Override
    protected void doProcess(DeleteACLRequest message, ImapSession session, String tag, ImapCommand command, Responder responder) {

        final MailboxManager mailboxManager = getMailboxManager();
        final MailboxSession mailboxSession = ImapSessionUtils.getMailboxSession(session);
        final String mailboxName = message.getMailboxName();
        final String identifier = message.getIdentifier();
        try {

            MailboxPath mailboxPath = PathConverter.forSession(session).buildFullPath(mailboxName);
            // Check that mailbox exists
            mailboxManager.getMailbox(mailboxPath, mailboxSession);
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
                
                MailboxACLEntryKey key = new SimpleMailboxACLEntryKey(identifier);
                
                // FIXME check if identifier is a valid user or group
                // FIXME Servers, when processing a command that has an identifier as a
                // parameter (i.e., any of SETACL, DELETEACL, and LISTRIGHTS commands),
                // SHOULD first prepare the received identifier using "SASLprep" profile
                // [SASLprep] of the "stringprep" algorithm [Stringprep].  If the
                // preparation of the identifier fails or results in an empty string,
                // the server MUST refuse to perform the command with a BAD response.
                // Note that Section 6 recommends additional identifier’s verification
                // steps.

                mailboxManager.setRights(mailboxPath,
                    new SimpleMailboxACL.SimpleMailboxACLCommand(key, EditMode.REPLACE, null), mailboxSession);

                okComplete(command, tag, responder);
                // FIXME should we send unsolicited responses here?
                // unsolicitedResponses(session, responder, false);
            }
        } catch (UnsupportedRightException e) {
            /*
             * RFc 4314, section 3.1
             * Note that an unrecognized right MUST cause the command to return the
             * BAD response.  In particular, the server MUST NOT silently ignore
             * unrecognized rights.
             * */
            Object[] params = new Object[] {e.getUnsupportedRight()};
            HumanReadableText text = new HumanReadableText(HumanReadableText.UNSUPPORTED_RIGHT_KEY, HumanReadableText.UNSUPPORTED_RIGHT_DEFAULT_VALUE, params);
            taggedBad(command, tag, responder, text);
        } catch (MailboxNotFoundException e) {
            no(command, tag, responder, HumanReadableText.MAILBOX_NOT_FOUND);
        } catch (MailboxException e) {
            session.getLog().error(command.getName() +" failed for mailbox " + mailboxName, e);
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
