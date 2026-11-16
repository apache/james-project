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

import jakarta.inject.Inject;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.MoveRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MoveProcessor extends AbstractMessageRangeProcessor<MoveRequest> implements CapabilityImplementingProcessor {

    private final boolean moveCapabilitySupported;

    @Inject
    public MoveProcessor(MailboxManager mailboxManager, StatusResponseFactory factory,
                         MetricFactory metricFactory, PathConverter.Factory pathConverterFactory) {
        super(MoveRequest.class, mailboxManager, factory, metricFactory, pathConverterFactory);
        moveCapabilitySupported = mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.Move);
    }

    @Override
    protected Mono<Void> processRequestReactive(MoveRequest request, ImapSession session, Responder responder) {
        // RFC 6851 section 3.3: MOVE expunges the source messages, which a mailbox selected read-only
        // (EXAMINE, RFC 9051 section 6.3.3) does not permit
        SelectedMailbox selected = session.getSelected();
        if (selected != null && selected.isReadOnly()) {
            no(request, responder, HumanReadableText.MAILBOX_IS_READ_ONLY);
            return Mono.empty();
        }
        return super.processRequestReactive(request, session, responder);
    }

    /**
     * RFC 6851 section 4.3: COPYUID is sent in an untagged OK, before the EXPUNGE responses of the moved messages.
     */
    @Override
    protected Mono<Void> respondCompletion(MoveRequest request, ImapSession session, Responder responder, StatusResponse.ResponseCode copyUid) {
        return Mono.fromRunnable(() -> responder.respond(getStatusResponseFactory().untaggedOk(HumanReadableText.MOVED_UIDS, copyUid)))
            .then(unsolicitedResponses(session, responder, request.isUseUids()))
            .then(Mono.fromRunnable(() -> okComplete(request, responder)));
    }

    @Override
    protected Flux<MessageRange> process(MailboxId targetMailbox, SelectedMailbox currentMailbox,
                                         MailboxSession mailboxSession, MessageRange messageSet) {
        return Flux.from(getMailboxManager().moveMessagesReactive(messageSet, currentMailbox.getMailboxId(), targetMailbox, mailboxSession));
    }

    @Override
    protected Flux<MessageRange> processAll(MailboxId targetMailbox, SelectedMailbox currentMailbox,
                                            MailboxSession mailboxSession, List<MessageRange> messageSets) {
        return Flux.from(getMailboxManager().moveMessagesReactive(messageSets, currentMailbox.getMailboxId(), targetMailbox, mailboxSession));
    }

    @Override
    protected String getOperationName() {
        return "Move";
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        if (moveCapabilitySupported) {
            return ImmutableList.of(ImapConstants.SUPPORTS_MOVE);
        } else {
            return ImmutableList.of();
        }
    }

    @Override
    protected MDCBuilder mdc(MoveRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "MOVE")
            .addToContext("targetMailbox", request.getMailboxName())
            .addToContext("uidEnabled", Boolean.toString(request.isUseUids()))
            .addToContext("idSet", IdRange.toString(request.getIdSet()));
    }

}
