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

package org.apache.james.imap.processor.fetch;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.BodyFetchElement;
import org.apache.james.imap.api.message.FetchData;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.FetchRequest;
import org.apache.james.imap.message.response.FetchResponse;
import org.apache.james.imap.processor.AbstractMailboxProcessor;
import org.apache.james.imap.processor.EnableProcessor;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.MetaData;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MessageRangeException;
import org.apache.james.mailbox.model.FetchGroupImpl;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MessageResult.FetchGroup;
import org.apache.james.mailbox.model.MessageResult.MimePath;
import org.apache.james.mailbox.model.MessageResultIterator;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FetchProcessor extends AbstractMailboxProcessor<FetchRequest> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FetchProcessor.class);

    public FetchProcessor(ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory,
            MetricFactory metricFactory) {
        super(FetchRequest.class, next, mailboxManager, factory, metricFactory);
    }

    /**
     * @see
     * org.apache.james.imap.processor.AbstractMailboxProcessor#doProcess(org.apache.james.imap.api.message.request.ImapRequest,
     * org.apache.james.imap.api.process.ImapSession, java.lang.String,
     * org.apache.james.imap.api.ImapCommand,
     * org.apache.james.imap.api.process.ImapProcessor.Responder)
     */
    protected void doProcess(FetchRequest request, ImapSession session, String tag, ImapCommand command, Responder responder) {
        final boolean useUids = request.isUseUids();
        final IdRange[] idSet = request.getIdSet();
        final FetchData fetch = request.getFetch();
        
        try {
            final Long changedSince = fetch.getChangedSince();

            final MessageManager mailbox = getSelectedMailbox(session);

            if (mailbox == null) {
                throw new MailboxException("Session not in SELECTED state");
            }

            final boolean vanished = fetch.getVanished();
            if (vanished && !EnableProcessor.getEnabledCapabilities(session).contains(ImapConstants.SUPPORTS_QRESYNC)) {
                taggedBad(command, tag, responder, HumanReadableText.QRESYNC_NOT_ENABLED);
                return;
            }
           
            if (vanished && changedSince == -1) {
                taggedBad(command, tag, responder, HumanReadableText.QRESYNC_VANISHED_WITHOUT_CHANGEDSINCE);
                return;
            }
            final MailboxSession mailboxSession = ImapSessionUtils.getMailboxSession(session);

            MetaData metaData = mailbox.getMetaData(false, mailboxSession, org.apache.james.mailbox.MessageManager.MetaData.FetchGroup.NO_COUNT);
            if (fetch.getChangedSince() != -1 || fetch.isModSeq()) {
                // Enable CONDSTORE as this is a CONDSTORE enabling command
                condstoreEnablingCommand(session, responder,  metaData, true);
            }
            
            List<MessageRange> ranges = new ArrayList<>();

            for (IdRange range : idSet) {
                MessageRange messageSet = messageRange(session.getSelected(), range, useUids);
                if (messageSet != null) {
                    MessageRange normalizedMessageSet = normalizeMessageRange(session.getSelected(), messageSet);
                    MessageRange batchedMessageSet = MessageRange.range(normalizedMessageSet.getUidFrom(), normalizedMessageSet.getUidTo());
                    ranges.add(batchedMessageSet);
                }
            }

            if (vanished) {
                // TODO: From the QRESYNC RFC it seems ok to send the VANISHED responses after the FETCH Responses. 
                //       If we do so we could prolly save one mailbox access which should give use some more speed up
                respondVanished(mailboxSession, mailbox, ranges, changedSince, metaData, responder);
            }
            // if QRESYNC is enable its necessary to also return the UID in all cases
            if (EnableProcessor.getEnabledCapabilities(session).contains(ImapConstants.SUPPORTS_QRESYNC)) {
                fetch.setUid(true);
            }
            processMessageRanges(session, mailbox, ranges, fetch, useUids, mailboxSession, responder);

            
            // Don't send expunge responses if FETCH is used to trigger this
            // processor. See IMAP-284
            final boolean omitExpunged = (!useUids);
            unsolicitedResponses(session, responder, omitExpunged, useUids);
            okComplete(command, tag, responder);
        } catch (MessageRangeException e) {
            LOGGER.debug("Fetch failed for mailbox {} because of invalid sequence-set {}", session.getSelected().getPath(), idSet, e);
            taggedBad(command, tag, responder, HumanReadableText.INVALID_MESSAGESET);
        } catch (MailboxException e) {
            LOGGER.error("Fetch failed for mailbox {} and sequence-set {}", session.getSelected().getPath(), idSet, e);
            no(command, tag, responder, HumanReadableText.SEARCH_FAILED);
        }
    }


    
    /**
     * Process the given message ranges by fetch them and pass them to the
     * {@link org.apache.james.imap.api.process.ImapProcessor.Responder}
     * 
     * @param session
     * @param mailbox
     * @param ranges
     * @param fetch
     * @param useUids
     * @param mailboxSession
     * @param responder
     * @throws MailboxException
     */
    protected void processMessageRanges(ImapSession session, MessageManager mailbox, List<MessageRange> ranges, FetchData fetch, boolean useUids, MailboxSession mailboxSession, Responder responder) throws MailboxException {
        final FetchResponseBuilder builder = new FetchResponseBuilder(new EnvelopeBuilder());
        FetchGroup resultToFetch = getFetchGroup(fetch);

        for (MessageRange range : ranges) {
            MessageResultIterator messages = mailbox.getMessages(range, resultToFetch, mailboxSession);
            while (messages.hasNext()) {
                final MessageResult result = messages.next();

                //skip unchanged messages - this should be filtered at the mailbox level to take advantage of indexes
                if (fetch.isModSeq() && result.getModSeq() <= fetch.getChangedSince()) {
                    continue;
                }

                try {
                    final FetchResponse response = builder.build(fetch, result, mailbox, session, useUids);
                    responder.respond(response);
                } catch (MessageRangeException e) {
                    // we can't for whatever reason find the message so
                    // just skip it and log it to debug
                    LOGGER.debug("Unable to find message with uid {}", result.getUid(), e);
                } catch (MailboxException e) {
                    // we can't for whatever reason find parse all requested parts of the message. This may because it was deleted while try to access the parts.
                    // So we just skip it 
                    //
                    // See IMAP-347
                    LOGGER.error("Unable to fetch message with uid {}, so skip it", result.getUid(), e);
                }
            }

            // Throw the exception if we received one
            if (messages.getException() != null) {
                throw messages.getException();
            }
        }

    }

    protected FetchGroup getFetchGroup(FetchData fetch) {
        FetchGroupImpl result = new FetchGroupImpl();

        if (fetch.isEnvelope()) {
            result.or(FetchGroup.HEADERS);
        }
        if (fetch.isBody() || fetch.isBodyStructure()) {
            result.or(FetchGroup.MIME_DESCRIPTOR);
        }

        Collection<BodyFetchElement> bodyElements = fetch.getBodyElements();
        if (bodyElements != null) {
            for (BodyFetchElement element : bodyElements) {
                final int sectionType = element.getSectionType();
                final int[] path = element.getPath();
                final boolean isBase = (path == null || path.length == 0);
                switch (sectionType) {
                    case BodyFetchElement.CONTENT:
                        if (isBase) {
                            addContent(result, path, isBase, FetchGroup.FULL_CONTENT);
                        } else {
                            addContent(result, path, isBase, FetchGroup.MIME_CONTENT);
                        }
                        break;
                    case BodyFetchElement.HEADER:
                    case BodyFetchElement.HEADER_NOT_FIELDS:
                    case BodyFetchElement.HEADER_FIELDS:
                        addContent(result, path, isBase, FetchGroup.HEADERS);
                        break;
                    case BodyFetchElement.MIME:
                        addContent(result, path, isBase, FetchGroup.MIME_HEADERS);
                        break;
                    case BodyFetchElement.TEXT:
                        addContent(result, path, isBase, FetchGroup.BODY_CONTENT);
                        break;
                    default:
                        break;
                }

            }
        }
        return result;
    }

    private void addContent(FetchGroupImpl result, int[] path, boolean isBase, int content) {
        if (isBase) {
            result.or(content);
        } else {
            MimePath mimePath = new MimePathImpl(path);
            result.addPartContent(mimePath, content);
        }
    }

    @Override
    protected Closeable addContextToMDC(FetchRequest message) {
        return MDCBuilder.create()
            .addContext(MDCBuilder.ACTION, "FETCH")
            .addContext("useUid", message.isUseUids())
            .addContext("idSet", IdRange.toString(message.getIdSet()))
            .addContext("fetchedData", message.getFetch())
            .build();
    }
}
