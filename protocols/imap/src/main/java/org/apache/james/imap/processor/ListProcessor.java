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

import java.util.ArrayList;
import java.util.List;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.display.CharsetUtil;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.MailboxType;
import org.apache.james.imap.api.process.MailboxTyper;
import org.apache.james.imap.message.request.ListRequest;
import org.apache.james.imap.message.response.ListResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxMetaData.Children;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MailboxQuery;

public class ListProcessor extends AbstractMailboxProcessor<ListRequest> {

    public ListProcessor(final ImapProcessor next, final MailboxManager mailboxManager, final StatusResponseFactory factory) {
        super(ListRequest.class, next, mailboxManager, factory);
    }

    /**
     * @see
     * org.apache.james.imap.processor.AbstractMailboxProcessor
     * #doProcess(org.apache.james.imap.api.message.request.ImapRequest,
     * org.apache.james.imap.api.process.ImapSession, java.lang.String,
     * org.apache.james.imap.api.ImapCommand,
     * org.apache.james.imap.api.process.ImapProcessor.Responder)
     */
    protected void doProcess(ListRequest request, ImapSession session, String tag, ImapCommand command, Responder responder) {
        final String baseReferenceName = request.getBaseReferenceName();
        final String mailboxPatternString = request.getMailboxPattern();
        doProcess(baseReferenceName, mailboxPatternString, session, tag, command, responder, null);
    }

    protected ImapResponseMessage createResponse(boolean noInferior, boolean noSelect, boolean marked, boolean unmarked, boolean hasChildren, boolean hasNoChildren, String mailboxName, char delimiter, MailboxType type) {
        return new ListResponse(noInferior, noSelect, marked, unmarked, hasChildren, hasNoChildren, mailboxName, delimiter);
    }

    /**
     * (from rfc3501)<br>
     * The LIST command returns a subset of names from the complete set of all
     * names available to the client. Zero or more untagged LIST replies are
     * returned, containing the name attributes, hierarchy delimiter, and name;
     * see the description of the LIST reply for more detail.<br>
     * ...<br>
     * An empty ("" string) mailbox name argument is a special request to return
     * the hierarchy delimiter and the root name of the name given in the
     * reference. The value returned as the root MAY be the empty string if the
     * reference is non-rooted or is an empty string.
     * 
     * @param referenceName
     * @param mailboxName
     * @param session
     * @param tag
     * @param command
     * @param responder
     */
    protected final void doProcess(final String referenceName, final String mailboxName, final ImapSession session, final String tag, ImapCommand command, final Responder responder, final MailboxTyper mailboxTyper) {
        try {
            // Should the namespace section be returned or not?
            final boolean isRelative;

            final List<MailboxMetaData> results;

            final String user = ImapSessionUtils.getUserName(session);
            final MailboxSession mailboxSession = ImapSessionUtils.getMailboxSession(session);

            if (mailboxName.length() == 0) {
                // An empty mailboxName signifies a request for the hierarchy
                // delimiter and root name of the referenceName argument

                String referenceRoot;
                if (referenceName.length() > 0 && referenceName.charAt(0) == MailboxConstants.NAMESPACE_PREFIX_CHAR) {
                    // A qualified reference name - get the root element
                    isRelative = false;
                    int firstDelimiter = referenceName.indexOf(mailboxSession.getPathDelimiter());
                    if (firstDelimiter == -1) {
                        referenceRoot = referenceName;
                    } else {
                        referenceRoot = referenceName.substring(0, firstDelimiter);
                    }
                    referenceRoot = CharsetUtil.decodeModifiedUTF7(referenceRoot);
                } else {
                    // A relative reference name, return "" to indicate it is
                    // non-rooted
                    referenceRoot = "";
                    isRelative = true;
                }
                // Get the mailbox for the reference name.
                final MailboxPath rootPath = new MailboxPath(referenceRoot, "", "");
                results = new ArrayList<MailboxMetaData>(1);
                results.add(new MailboxMetaData() {

                    /**
                     * @see org.apache.james.mailbox.MailboxMetaData#inferiors()
                     */
                    public Children inferiors() {
                        return Children.CHILDREN_ALLOWED_BUT_UNKNOWN;
                    }

                    /**
                     * @see org.apache.james.mailbox.MailboxMetaData#getSelectability()
                     */
                    public Selectability getSelectability() {
                        return Selectability.NOSELECT;
                    }

                    /**
                     * @see org.apache.james.mailbox.MailboxMetaData#getHierarchyDelimiter()
                     */
                    public char getHierarchyDelimiter() {
                        return mailboxSession.getPathDelimiter();
                    }

                    /**
                     * (non-Javadoc)
                     * @see org.apache.james.mailbox.MailboxMetaData#getPath()
                     */
                    public MailboxPath getPath() {
                        return rootPath;
                    }
                    
                });
            } else {
                // If the mailboxPattern is fully qualified, ignore the
                // reference name.
                String finalReferencename = referenceName;
                if (mailboxName.charAt(0) == MailboxConstants.NAMESPACE_PREFIX_CHAR) {
                    finalReferencename = "";
                }
                // Is the interpreted (combined) pattern relative?
                isRelative = ((finalReferencename + mailboxName).charAt(0) != MailboxConstants.NAMESPACE_PREFIX_CHAR);

                finalReferencename = CharsetUtil.decodeModifiedUTF7(finalReferencename);

                MailboxPath basePath = null;
                if (isRelative) {
                    basePath = new MailboxPath(MailboxConstants.USER_NAMESPACE, user, finalReferencename);
                } else {
                    basePath = buildFullPath(session, finalReferencename);
                }

                results = getMailboxManager().search(new MailboxQuery(basePath, CharsetUtil.decodeModifiedUTF7(mailboxName), mailboxSession.getPathDelimiter()), mailboxSession);
            }

            for (final MailboxMetaData metaData : results) {
                processResult(responder, isRelative, metaData, getMailboxType(session, mailboxTyper, metaData.getPath()));
            }

            okComplete(command, tag, responder);
        } catch (MailboxException e) {
            if (session.getLog().isInfoEnabled()) {
                session.getLog().info("List failed", e);
            }
            no(command, tag, responder, HumanReadableText.SEARCH_FAILED);
        }
    }

    void processResult(final Responder responder, final boolean relative, final MailboxMetaData listResult, final MailboxType mailboxType) {
        final char delimiter = listResult.getHierarchyDelimiter();
        final String mailboxName = mailboxName(relative, listResult.getPath(), delimiter);

        final Children inferiors = listResult.inferiors();
        final boolean noInferior = MailboxMetaData.Children.NO_INFERIORS.equals(inferiors);
        final boolean hasChildren = MailboxMetaData.Children.HAS_CHILDREN.equals(inferiors);
        final boolean hasNoChildren = MailboxMetaData.Children.HAS_NO_CHILDREN.equals(inferiors);
        boolean noSelect = false;
        boolean marked = false;
        boolean unmarked = false;
        switch (listResult.getSelectability()) {
        case MARKED:
            marked = true;
            break;
        case UNMARKED:
            unmarked = true;
            break;
        case NOSELECT:
            noSelect = true;
            break;
        default:
            break;
        }
        responder.respond(createResponse(noInferior, noSelect, marked, unmarked, hasChildren, hasNoChildren, mailboxName, delimiter, mailboxType));
    }

    /**
     * retrieve mailboxType for specified mailboxPath using provided
     * MailboxTyper
     * 
     * @param session
     *            current imap session
     * @param mailboxTyper
     *            provided MailboxTyper used to retrieve mailbox type
     * @param path
     *            mailbox's path
     * @return MailboxType value
     */
    private MailboxType getMailboxType(ImapSession session, MailboxTyper mailboxTyper, MailboxPath path) {
        MailboxType result = MailboxType.OTHER;
        if (mailboxTyper != null) {
            result = mailboxTyper.getMailboxType(session, path);
        }
        return result;
    }

    protected boolean isAcceptable(final ImapMessage message) {
        return ListRequest.class.equals(message.getClass());
    }
}
