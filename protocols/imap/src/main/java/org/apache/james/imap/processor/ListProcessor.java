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

import static org.apache.james.mailbox.MailboxManager.MailboxSearchFetchType.Minimal;

import java.io.Closeable;

import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.display.ModifiedUtf7;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.MailboxType;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.ListRequest;
import org.apache.james.imap.message.response.ListResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.model.search.PrefixedRegex;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListProcessor extends AbstractMailboxProcessor<ListRequest> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ListProcessor.class);

    public ListProcessor(ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory,
            MetricFactory metricFactory) {
        super(ListRequest.class, next, mailboxManager, factory, metricFactory);
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
     */
    @Override
    protected void processRequest(ListRequest request, ImapSession session, Responder responder) {
        String baseReferenceName = request.getBaseReferenceName();
        String mailboxPatternString = request.getMailboxPattern();
        MailboxSession mailboxSession = session.getMailboxSession();

        try {
            if (mailboxPatternString.length() == 0) {
                respondNamespace(baseReferenceName, responder, mailboxSession);
            } else {
                respondMailboxList(baseReferenceName, mailboxPatternString, session, responder, mailboxSession);
            }
            okComplete(request, responder);
        } catch (MailboxException e) {
            LOGGER.error("List failed for mailboxName {}", mailboxPatternString, e);
            no(request, responder, HumanReadableText.SEARCH_FAILED);
        }
    }

    protected ImapResponseMessage createResponse(MailboxMetaData.Children children, MailboxMetaData.Selectability selectability, String name, char hierarchyDelimiter, MailboxType type) {
        return new ListResponse(children, selectability, name, hierarchyDelimiter);
    }

    private void respondNamespace(String referenceName, Responder responder, MailboxSession mailboxSession) {
        // An empty mailboxName signifies a request for the hierarchy
        // delimiter and root name of the referenceName argument
        String referenceRoot = ModifiedUtf7.decodeModifiedUTF7(computeReferenceRoot(referenceName, mailboxSession));

        responder.respond(createResponse(
            MailboxMetaData.Children.CHILDREN_ALLOWED_BUT_UNKNOWN,
            MailboxMetaData.Selectability.NOSELECT,
            referenceRoot,
            mailboxSession.getPathDelimiter(),
            MailboxType.OTHER));
    }

    private String computeReferenceRoot(String referenceName, MailboxSession mailboxSession) {
        if (referenceName.length() > 0 && referenceName.charAt(0) == MailboxConstants.NAMESPACE_PREFIX_CHAR) {
            // A qualified reference name - get the root element
            int firstDelimiter = referenceName.indexOf(mailboxSession.getPathDelimiter());
            if (firstDelimiter == -1) {
                 return referenceName;
            } else {
                return referenceName.substring(0, firstDelimiter);
            }
        } else {
            // A relative reference name, return "" to indicate it is
            // non-rooted
            return "";
        }
    }

    private void respondMailboxList(String referenceName, String mailboxName, ImapSession session, Responder responder, MailboxSession mailboxSession) throws MailboxException {
        // If the mailboxPattern is fully qualified, ignore the
        // reference name.
        String finalReferencename = referenceName;
        if (mailboxName.charAt(0) == MailboxConstants.NAMESPACE_PREFIX_CHAR) {
            finalReferencename = "";
        }
        // Is the interpreted (combined) pattern relative?
        // Should the namespace section be returned or not?
        boolean isRelative = ((finalReferencename + mailboxName).charAt(0) != MailboxConstants.NAMESPACE_PREFIX_CHAR);

        MailboxPath basePath = computeBasePath(session, finalReferencename, isRelative);

        getMailboxManager().search(
                MailboxQuery.builder()
                    .userAndNamespaceFrom(basePath)
                    .expression(new PrefixedRegex(
                        basePath.getName(),
                        ModifiedUtf7.decodeModifiedUTF7(mailboxName),
                        mailboxSession.getPathDelimiter()))
                    .build(), Minimal, mailboxSession)
            .doOnNext(metaData -> processResult(responder, isRelative, metaData, getMailboxType(session, metaData.getPath())))
            .then()
            .block();
    }

    private MailboxPath computeBasePath(ImapSession session, String finalReferencename, boolean isRelative) {
        String decodedName = ModifiedUtf7.decodeModifiedUTF7(finalReferencename);
        if (isRelative) {
            return MailboxPath.forUser(session.getUserName(), decodedName);
        } else {
            return PathConverter.forSession(session).buildFullPath(decodedName);
        }
    }

    private void processResult(Responder responder, boolean relative, MailboxMetaData listResult, MailboxType mailboxType) {
        String mailboxName = mailboxName(relative, listResult.getPath(), listResult.getHierarchyDelimiter());

        ImapResponseMessage response =
            createResponse(
                listResult.inferiors(),
                listResult.getSelectability(),
                mailboxName,
                listResult.getHierarchyDelimiter(),
                mailboxType);
        responder.respond(response);
    }

    /**
     * retrieve mailboxType for specified mailboxPath using provided
     * MailboxTyper
     * 
     * @param session
     *            current imap session
     * @param path
     *            mailbox's path
     * @return MailboxType value
     */
    protected MailboxType getMailboxType(ImapSession session, MailboxPath path) {
        return MailboxType.OTHER;
    }

    @Override
    protected boolean isAcceptable(ImapMessage message) {
        return ListRequest.class.equals(message.getClass());
    }

    @Override
    protected Closeable addContextToMDC(ListRequest request) {
        return MDCBuilder.create()
            .addContext(MDCBuilder.ACTION, "LIST")
            .addContext("base", request.getBaseReferenceName())
            .addContext("pattern", request.getMailboxPattern())
            .build();
    }
}
