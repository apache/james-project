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
import java.util.Iterator;
import java.util.List;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.FetchData;
import org.apache.james.imap.api.message.FetchData.Item;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.message.request.FetchRequest;
import org.apache.james.imap.message.response.FetchResponse;
import org.apache.james.imap.processor.AbstractMailboxProcessor;
import org.apache.james.imap.processor.EnableProcessor;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.MailboxMetaData;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MessageRangeException;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MessageResultIterator;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.MemoizedSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;

public class FetchProcessor extends AbstractMailboxProcessor<FetchRequest> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FetchProcessor.class);

    public FetchProcessor(ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory,
            MetricFactory metricFactory) {
        super(FetchRequest.class, next, mailboxManager, factory, metricFactory);
    }

    @Override
    protected void processRequest(FetchRequest request, ImapSession session, Responder responder) {
        boolean useUids = request.isUseUids();
        IdRange[] idSet = request.getIdSet();
        FetchData fetch = computeFetchData(request, session);

        try {
            final long changedSince = fetch.getChangedSince();

            MessageManager mailbox = getSelectedMailbox(session)
                .orElseThrow(() -> new MailboxException("Session not in SELECTED state"));

            boolean vanished = fetch.getVanished();
            if (vanished && !EnableProcessor.getEnabledCapabilities(session).contains(ImapConstants.SUPPORTS_QRESYNC)) {
                taggedBad(request, responder, HumanReadableText.QRESYNC_NOT_ENABLED);
                return;
            }
           
            if (vanished && changedSince == -1) {
                taggedBad(request, responder, HumanReadableText.QRESYNC_VANISHED_WITHOUT_CHANGEDSINCE);
                return;
            }
            final MailboxSession mailboxSession = session.getMailboxSession();

            MemoizedSupplier<MailboxMetaData> metaData = MemoizedSupplier.of(Throwing.supplier(
                    () -> mailbox.getMetaData(false, mailboxSession, MailboxMetaData.FetchGroup.NO_COUNT))
                .sneakyThrow());
            if (fetch.getChangedSince() != -1 || fetch.contains(Item.MODSEQ)) {
                // Enable CONDSTORE as this is a CONDSTORE enabling command
                condstoreEnablingCommand(session, responder,  metaData.get(), true);
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
                respondVanished(mailboxSession, mailbox, ranges, changedSince, metaData.get(), responder);
            }
            processMessageRanges(session, mailbox, ranges, fetch, useUids, mailboxSession, responder);

            
            // Don't send expunge responses if FETCH is used to trigger this
            // processor. See IMAP-284
            final boolean omitExpunged = (!useUids);
            unsolicitedResponses(session, responder, omitExpunged, useUids);
            okComplete(request, responder);
        } catch (MessageRangeException e) {
            LOGGER.debug("Fetch failed for mailbox {} because of invalid sequence-set {}", session.getSelected().getMailboxId(), idSet, e);
            taggedBad(request, responder, HumanReadableText.INVALID_MESSAGESET);
        } catch (MailboxException e) {
            LOGGER.error("Fetch failed for mailbox {} and sequence-set {}", session.getSelected().getMailboxId(), idSet, e);
            no(request, responder, HumanReadableText.SEARCH_FAILED);
        }
    }

    private FetchData computeFetchData(FetchRequest request, ImapSession session) {
        // if QRESYNC is enable its necessary to also return the UID in all cases
        if (EnableProcessor.getEnabledCapabilities(session).contains(ImapConstants.SUPPORTS_QRESYNC)) {
            return FetchData.Builder.from(request.getFetch())
                .fetch(Item.UID)
                .build();
        }
        return request.getFetch();
    }

    /**
     * Process the given message ranges by fetch them and pass them to the
     * {@link org.apache.james.imap.api.process.ImapProcessor.Responder}
     */
    private void processMessageRanges(ImapSession session, MessageManager mailbox, List<MessageRange> ranges, FetchData fetch, boolean useUids, MailboxSession mailboxSession, Responder responder) throws MailboxException {
        final FetchResponseBuilder builder = new FetchResponseBuilder(new EnvelopeBuilder());
        FetchGroup resultToFetch = FetchDataConverter.getFetchGroup(fetch);

        for (MessageRange range : ranges) {
            if (fetch.isOnlyFlags()) {
                processMessageRangeForFlags(session, mailbox, fetch, mailboxSession, responder, builder, range);
            } else {
                processMessageRange(session, mailbox, fetch, mailboxSession, responder, builder, resultToFetch, range);
            }
        }

    }

    private void processMessageRangeForFlags(ImapSession session, MessageManager mailbox, FetchData fetch, MailboxSession mailboxSession, Responder responder, FetchResponseBuilder builder, MessageRange range) {
        SelectedMailbox selected = session.getSelected();
        Iterator<ComposedMessageIdWithMetaData> results = Flux.from(mailbox.listMessagesMetadata(range, mailboxSession))
            .filter(ids -> !fetch.contains(Item.MODSEQ) || ids.getModSeq().asLong() > fetch.getChangedSince())
            .toStream()
            .iterator();

        while (results.hasNext()) {
            ComposedMessageIdWithMetaData result = results.next();

            try {
                final FetchResponse response = builder.build(fetch, result, mailbox, selected, mailboxSession);
                responder.respond(response);
            } catch (MessageRangeException e) {
                // we can't for whatever reason find the message so
                // just skip it and log it to debug
                LOGGER.debug("Unable to find message with uid {}", result.getComposedMessageId().getUid(), e);
            } catch (MailboxException e) {
                // we can't for whatever reason find parse all requested parts of the message. This may because it was deleted while try to access the parts.
                // So we just skip it
                //
                // See IMAP-347
                LOGGER.error("Unable to fetch message with uid {}, so skip it", result.getComposedMessageId().getUid(), e);
            }
        }
    }

    private void processMessageRange(ImapSession session, MessageManager mailbox, FetchData fetch, MailboxSession mailboxSession, Responder responder, FetchResponseBuilder builder, FetchGroup resultToFetch, MessageRange range) throws MailboxException {
        MessageResultIterator messages = mailbox.getMessages(range, resultToFetch, mailboxSession);
        SelectedMailbox selected = session.getSelected();
        while (messages.hasNext()) {
            final MessageResult result = messages.next();

            //skip unchanged messages - this should be filtered at the mailbox level to take advantage of indexes
            if (fetch.contains(Item.MODSEQ) && result.getModSeq().asLong() <= fetch.getChangedSince()) {
                continue;
            }

            try {
                final FetchResponse response = builder.build(fetch, result, mailbox, selected, mailboxSession);
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


    @Override
    protected Closeable addContextToMDC(FetchRequest request) {
        return MDCBuilder.create()
            .addContext(MDCBuilder.ACTION, "FETCH")
            .addContext("useUid", request.isUseUids())
            .addContext("idSet", IdRange.toString(request.getIdSet()))
            .addContext("fetchedData", request.getFetch())
            .build();
    }
}
