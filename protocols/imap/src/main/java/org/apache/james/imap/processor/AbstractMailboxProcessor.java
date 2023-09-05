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
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.mail.Flags;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.UidRange;
import org.apache.james.imap.api.message.request.ImapRequest;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponse.ResponseCode;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.main.DeniedAccessOnSharedMailboxException;
import org.apache.james.imap.message.response.ExistsResponse;
import org.apache.james.imap.message.response.ExpungeResponse;
import org.apache.james.imap.message.response.FetchResponse;
import org.apache.james.imap.message.response.FlagsResponse;
import org.apache.james.imap.message.response.RecentResponse;
import org.apache.james.imap.message.response.VanishedResponse;
import org.apache.james.imap.processor.base.AbstractProcessor;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.MailboxMetaData;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.NullableMessageSequenceNumber;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MessageRangeException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageRange.Type;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class AbstractMailboxProcessor<R extends ImapRequest> extends AbstractProcessor<R> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractMailboxProcessor.class);

    public static final String IMAP_PREFIX = "IMAP-";
    private final MailboxManager mailboxManager;
    private final StatusResponseFactory factory;
    private final MetricFactory metricFactory;

    public AbstractMailboxProcessor(Class<R> acceptableClass, MailboxManager mailboxManager, StatusResponseFactory factory,
                                    MetricFactory metricFactory) {
        super(acceptableClass);
        this.mailboxManager = mailboxManager;
        this.factory = factory;
        this.metricFactory = metricFactory;
    }

    @Override
    protected final Mono<Void> doProcess(R acceptableMessage, Responder responder, ImapSession session) {
        if (acceptableMessage.getCommand().validForState(session.getState())) {
            MailboxSession mailboxSession = session.getMailboxSession();

            return Mono.from(metricFactory.decoratePublisherWithTimerMetric(IMAP_PREFIX + acceptableMessage.getCommand().getName(),
                mailboxManager.manageProcessing(
                    processRequestReactive(acceptableMessage, session, responder)
                        .onErrorResume(DeniedAccessOnSharedMailboxException.class, e -> {
                            no(acceptableMessage, responder, HumanReadableText.DENIED_SHARED_MAILBOX);
                            return Mono.empty();
                        })
                        .onErrorResume(e -> {
                            no(acceptableMessage, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
                            return ReactorUtils.logAsMono(() -> LOGGER.error("Unexpected error during IMAP processing", e));
                        }), mailboxSession)));
        } else {
            ImapResponseMessage response = factory.taggedNo(acceptableMessage.getTag(), acceptableMessage.getCommand(), HumanReadableText.INVALID_COMMAND);
            responder.respond(response);
            return Mono.empty();
        }
    }

    protected void flags(Responder responder, SelectedMailbox selected) {
        responder.respond(new FlagsResponse(selected.getApplicableFlags()));
    }

    protected void permanentFlags(Responder responder, Flags permanentFlags, SelectedMailbox selected) {
        if (permanentFlags.contains(Flags.Flag.USER)) {
            permanentFlags.add(selected.getApplicableFlags());
        }
        final StatusResponse untaggedOk = factory.untaggedOk(HumanReadableText.permanentFlags(permanentFlags), ResponseCode.permanentFlags(permanentFlags));
        responder.respond(untaggedOk);
    }
    
    protected Mono<Void> unsolicitedResponses(ImapSession session, ImapProcessor.Responder responder, boolean useUids) {
        return unsolicitedResponses(session, responder, false, useUids);
    }

    /**
     * Sends any unsolicited responses to the client, such as EXISTS and FLAGS
     * responses when the selected mailbox is modified by another user.
     */
    protected Mono<Void> unsolicitedResponses(ImapSession session, ImapProcessor.Responder responder, boolean omitExpunged, boolean useUid) {
        final SelectedMailbox selected = session.getSelected();
        if (selected == null) {
            LOGGER.debug("No mailbox selected");
            return Mono.empty();
        } else {
            return unsolicitedResponses(session, responder, selected, omitExpunged, useUid);
        }
    }

    private Mono<Void> unsolicitedResponses(ImapSession session, ImapProcessor.Responder responder, SelectedMailbox selected, boolean omitExpunged, boolean useUid) {
        return Mono.fromRunnable(() -> {
            boolean sizeChanged = selected.isSizeChanged();
            // New message response
            if (sizeChanged) {
                addExistsResponses(selected, responder);
            }
            // Expunged messages
            if (!omitExpunged) {
                final Collection<MessageUid> expungedUids = selected.expungedUids();
                if (!expungedUids.isEmpty()) {
                    // Check if QRESYNC was enabled. If so we MUST use VANISHED responses
                    if (EnableProcessor.getEnabledCapabilities(session).contains(ImapConstants.SUPPORTS_QRESYNC)) {
                        addVanishedResponse(selected, expungedUids, responder);
                    } else {
                        addExpungedResponses(selected, expungedUids, responder);
                    }
                    // Only reset the events if we send the EXPUNGE or VANISHED responses. See IMAP-286
                    selected.resetExpungedUids();
                }

            }
            if (sizeChanged || (selected.isRecentUidRemoved() && !omitExpunged)) {
                addRecentResponses(selected, responder);
                selected.resetRecentUidRemoved();
            }
        })
            .then(addFlagsResponses(session, selected, responder, useUid))
            .then(Mono.fromRunnable(selected::resetEvents));
    }

    private void addExpungedResponses(SelectedMailbox selected, Collection<MessageUid> expungedUids, ImapProcessor.Responder responder) {
        for (MessageUid uid : expungedUids) {

            // we need to remove the message in the loop to the sequence numbers
            // are updated correctly.
            // See 7.4.1. EXPUNGE Response
            final NullableMessageSequenceNumber msn = selected.remove(uid);
            ExpungeResponse response = new ExpungeResponse(msn);
            responder.respond(response);
        }
    }
    
    private void addVanishedResponse(SelectedMailbox selected, Collection<MessageUid> expungedUids, ImapProcessor.Responder responder) {
        for (MessageUid uid : expungedUids) {
            selected.remove(uid);
        }
        UidRange[] uidRange = uidRanges(MessageRange.toRanges(expungedUids));
        responder.respond(new VanishedResponse(uidRange, false));
    }
    
    private Mono<Void> addFlagsResponses(ImapSession session, SelectedMailbox selected, ImapProcessor.Responder responder, boolean useUid) {
        MessageManager messageManager = selected.getMessageManager();
        MailboxSession mailboxSession = session.getMailboxSession();

        Collection<MessageUid> flagUpdateUids = selected.flagUpdateUids();
        if (!flagUpdateUids.isEmpty()) {

            return Mono.fromRunnable(() -> addApplicableFlagResponse(session, selected, responder, useUid))
                .then(Flux.fromIterable(MessageRange.toRanges(flagUpdateUids))
                    .concatMap(range ->
                        addFlagsResponses(session, selected, responder, useUid, range, messageManager, mailboxSession))
                    .then()
                    .onErrorResume(MailboxException.class, e -> {
                        handleResponseException(responder, e, HumanReadableText.FAILURE_TO_LOAD_FLAGS, session);
                        return Mono.empty();
                    }));
        } else {
            return Mono.fromRunnable(() -> addApplicableFlagResponse(session, selected, responder, useUid));
        }
    }

    private void addApplicableFlagResponse(ImapSession session, SelectedMailbox selected, ImapProcessor.Responder responder, boolean useUid) {
        // To be lazily initialized only if needed, which is in minority of cases.
        MessageManager messageManager = selected.getMessageManager();
        MailboxSession mailboxSession = session.getMailboxSession();

        // Check if we need to send a FLAGS and PERMANENTFLAGS response before the FETCH response
        // This is the case if some new flag/keyword was used
        // See IMAP-303
        if (selected.hasNewApplicableFlags()) {
            flags(responder, selected);
            permanentFlags(responder, messageManager.getPermanentFlags(mailboxSession), selected);
            selected.resetNewApplicableFlags();
        }
    }
    
    private Mono<Void> addFlagsResponses(ImapSession session,
                                   SelectedMailbox selected,
                                   ImapProcessor.Responder responder,
                                   boolean useUid,
                                   MessageRange messageSet, MessageManager mailbox,
                                   MailboxSession mailboxSession) {
        final boolean qresyncEnabled = EnableProcessor.getEnabledCapabilities(session).contains(ImapConstants.SUPPORTS_QRESYNC);
        final boolean condstoreEnabled = EnableProcessor.getEnabledCapabilities(session).contains(ImapConstants.SUPPORTS_CONDSTORE);
        return Flux.from(
            mailbox.listMessagesMetadata(messageSet, mailboxSession))
            .doOnNext(Throwing.consumer(mr -> {
                MessageUid uid = mr.getComposedMessageId().getUid();
                selected.msn(uid).fold(() -> {
                    LOGGER.debug("No message found with uid {} in the uid<->msn mapping for mailbox {}. This may be because it was deleted by a concurrent session. So skip it..", uid, selected.getMailboxId().serialize());
                    // skip this as it was not found in the mapping
                    //
                    // See IMAP-346
                    return null;
                }, msn -> {

                    final Flags flags = mr.getFlags();
                    final MessageUid uidOut;
                    if (useUid || qresyncEnabled) {
                        uidOut = uid;
                    } else {
                        uidOut = null;
                    }
                    if (selected.isRecent(uid)) {
                        flags.add(Flags.Flag.RECENT);
                    } else {
                        flags.remove(Flags.Flag.RECENT);
                    }
                    final FetchResponse response;

                    // Check if we also need to return the MODSEQ in the response. This is true if CONDSTORE or
                    // if QRESYNC was enabled, and the mailbox supports the permant storage of mod-sequences
                    if (condstoreEnabled || qresyncEnabled) {
                        response = new FetchResponse(msn, flags, uidOut, null, mr.getModSeq(), null, null, null, null, null, null, null, null);
                    } else {
                        response = new FetchResponse(msn, flags, uidOut, null, null, null, null, null, null, null, null, null, null);
                    }
                    responder.respond(response);
                    return null;
                });
            })).then();
    }

    protected void condstoreEnablingCommand(ImapSession session, Responder responder, MailboxMetaData metaData, boolean sendHighestModSeq) {
        Set<Capability> enabled = EnableProcessor.getEnabledCapabilities(session);
        if (!enabled.contains(ImapConstants.SUPPORTS_CONDSTORE)) {
            if (sendHighestModSeq) {
                ModSeq highestModSeq = metaData.getHighestModSeq();

                StatusResponse untaggedOk = getStatusResponseFactory().untaggedOk(HumanReadableText.HIGHEST_MOD_SEQ, ResponseCode.highestModSeq(highestModSeq));
                responder.respond(untaggedOk);
            }
            enabled.add(ImapConstants.SUPPORTS_CONDSTORE);
        }
    }

    private void addRecentResponses(SelectedMailbox selected, ImapProcessor.Responder responder) {
        final int recentCount = selected.recentCount();
        RecentResponse response = new RecentResponse(recentCount);
        responder.respond(response);
    }

    private void addExistsResponses(SelectedMailbox selected, ImapProcessor.Responder responder) {
        final long existsCount = selected.existsCount();
        final ExistsResponse response = new ExistsResponse(existsCount);
        responder.respond(response);
    }

    private void handleResponseException(ImapProcessor.Responder responder, MailboxException e, HumanReadableText message, ImapSession session) {
        LOGGER.error("{}", message, e);
        // TODO: consider whether error message should be passed to the user
        final StatusResponse response = factory.untaggedNo(message);
        responder.respond(response);
    }

    protected void okComplete(ImapRequest request, ImapProcessor.Responder responder) {
        final StatusResponse response = factory.taggedOk(request.getTag(), request.getCommand(), HumanReadableText.COMPLETED);
        responder.respond(response);
    }

    protected void okComplete(ImapRequest request,  ResponseCode code, ImapProcessor.Responder responder) {
        final StatusResponse response = factory.taggedOk(request.getTag(), request.getCommand(), HumanReadableText.COMPLETED, code);
        responder.respond(response);
    }

    protected void no(ImapRequest request, ImapProcessor.Responder responder, HumanReadableText displayTextKey) {
        StatusResponse response = factory.taggedNo(request.getTag(), request.getCommand(), displayTextKey);
        responder.respond(response);
    }

    protected void no(ImapRequest request, ImapProcessor.Responder responder, HumanReadableText displayTextKey, StatusResponse.ResponseCode responseCode) {
        StatusResponse response = factory.taggedNo(request.getTag(), request.getCommand(), displayTextKey, responseCode);
        responder.respond(response);
    }

    protected void taggedBad(ImapRequest request, ImapProcessor.Responder responder, HumanReadableText e) {
        StatusResponse response = factory.taggedBad(request.getTag(), request.getCommand(), e);

        responder.respond(response);
    }

    protected void bye(ImapProcessor.Responder responder) {
        final StatusResponse response = factory.bye(HumanReadableText.BYE);
        responder.respond(response);
    }

    protected void bye(ImapProcessor.Responder responder, HumanReadableText key) {
        final StatusResponse response = factory.bye(key);
        responder.respond(response);
    }

    protected void processRequest(R request, ImapSession session, Responder responder) {
        processRequestReactive(request, session, responder).block();
    }

    protected Mono<Void> processRequestReactive(R request, ImapSession session, Responder responder) {
        return Mono.deferContextual(context -> Mono.fromRunnable(() -> {
            try (Closeable mdc = ReactorUtils.retrieveMDCBuilder(context).build()) {
                processRequest(request, session, responder);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        })).subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
            .then();
    }

    /**
     * Joins the elements of a mailboxPath together and returns them as a string
     */
    private String joinMailboxPath(MailboxPath mailboxPath, char delimiter) {
        StringBuilder sb = new StringBuilder();
        if (mailboxPath.getNamespace() != null && !mailboxPath.getNamespace().equals("")) {
            sb.append(mailboxPath.getNamespace());
        }
        if (mailboxPath.getUser() != null && !mailboxPath.getUser().equals("")) {
            if (sb.length() > 0) {
                sb.append(delimiter);
            }
            sb.append(mailboxPath.getUser().asString());
        }
        if (mailboxPath.getName() != null && !mailboxPath.getName().equals("")) {
            if (sb.length() > 0) {
                sb.append(delimiter);
            }
            sb.append(mailboxPath.getName());
        }
        return sb.toString();
    }

    protected String mailboxName(boolean relative, MailboxPath path, char delimiter) {
        if (relative) {
            return path.getName();
        } else {
            return joinMailboxPath(path, delimiter);
        }
    }

    protected MailboxManager getMailboxManager() {
        return mailboxManager;
    }

    protected StatusResponseFactory getStatusResponseFactory() {
        return factory;
    }

    protected Mono<MessageManager> getSelectedMailboxReactive(ImapSession session, Mono<MessageManager> ifEmpty) {
        return Optional.ofNullable(session.getSelected())
            .map(selectedMailbox -> Mono.from(getMailboxManager()
                .getMailboxReactive(selectedMailbox.getMailboxId(), session.getMailboxSession())))
            .orElse(ifEmpty);
    }

    protected Mono<MessageManager> getSelectedMailboxReactive(ImapSession session) {
        return getSelectedMailboxReactive(session, Mono.error(() -> new MailboxException("Session not in SELECTED state")));
    }

    /**
     * Return a {@link MessageRange} for the given values. If the MessageRange
     * can not be generated a {@link MailboxException} will get thrown
     */
    protected Optional<MessageRange> messageRange(SelectedMailbox selected, IdRange range, boolean useUids) throws MessageRangeException {
        long lowVal = range.getLowVal();
        long highVal = range.getHighVal();

        if (!useUids) {
            return Optional.of(msnRangeToMessageRange(selected, lowVal, highVal));
        } else {
            if (selected.existsCount() <= 0) {
                return Optional.empty();
            }
            // Take care of "*" and "*:*" values by return the last message in
            // the mailbox. See IMAP-289
            MessageUid lastUid = selected.getLastUid().orElse(MessageUid.MIN_VALUE);
            if (lowVal == Long.MAX_VALUE && highVal == Long.MAX_VALUE) {
                return Optional.of(MessageRange.one(lastUid));
            } else if (highVal == Long.MAX_VALUE && lastUid.compareTo(MessageUid.of(lowVal)) < 0) {
                // Sequence uid ranges which use *:<uid-higher-then-last-uid>
                // MUST return at least the highest uid in the mailbox
                // See IMAP-291
                return Optional.of(MessageRange.one(lastUid));
            } 
            return Optional.of(MessageRange.range(MessageUid.of(lowVal), MessageUid.of(highVal)));
        }
    }

    private MessageRange msnRangeToMessageRange(SelectedMailbox selected, long lowVal, long highVal)
            throws MessageRangeException {
        // Take care of "*" and "*:*" values by return the last message in
        // the mailbox. See IMAP-289
        if (lowVal == Long.MAX_VALUE && highVal == Long.MAX_VALUE) {
            Optional<MessageUid> last = selected.getLastUid();
            if (!last.isPresent()) {
                throw new MessageRangeException("Mailbox is empty");
            }
            return last.get().toRange();
        }

        MessageUid lowUid = msnlowValToUid(selected, lowVal);
        MessageUid highUid = msnHighValToUid(selected, highVal);
        return MessageRange.range(lowUid, highUid);
    }

    private MessageUid msnlowValToUid(SelectedMailbox selected, long lowVal) throws MessageRangeException {
        Optional<MessageUid> uid;
        if (lowVal != Long.MIN_VALUE) {
            uid = selected.uid((int) lowVal);
            if (!uid.isPresent()) {
                throw new MessageRangeException("No message found with msn " + lowVal);
            }
        } else {
            uid = selected.getFirstUid();
            if (!uid.isPresent()) {
                throw new MessageRangeException("Mailbox is empty");
            }
        }
        return uid.get();
    }
    

    private MessageUid msnHighValToUid(SelectedMailbox selected, long highVal) throws MessageRangeException {
        Optional<MessageUid> uid;
        if (highVal != Long.MAX_VALUE) {
            uid = selected.uid((int) highVal);
            if (!uid.isPresent()) {
                throw new MessageRangeException("No message found with msn " + highVal);
            }
        } else {
            uid = selected.getLastUid();
            if (!uid.isPresent()) {
                throw new MessageRangeException("Mailbox is empty");
            }
        }
        return uid.get();
    }
    
    /**
     * Format MessageRange to RANGE format applying selected folder min & max
     * UIDs constraints
     * 
     * @param selected
     *            currently selected mailbox
     * @param range
     *            input range
     * @return normalized message range
     */
    protected MessageRange normalizeMessageRange(SelectedMailbox selected, MessageRange range) {
        Type rangeType = range.getType();
        MessageUid start;
        MessageUid end;

        switch (rangeType) {
        case ONE:
            return range;
        case ALL:
            start = selected.getFirstUid().orElse(MessageUid.MIN_VALUE);
            end = selected.getLastUid().orElse(MessageUid.MAX_VALUE);
            return MessageRange.range(start, end);
        case RANGE:
            start = range.getUidFrom();
            if (start.equals(MessageUid.MAX_VALUE) || start.compareTo(selected.getFirstUid().orElse(MessageUid.MIN_VALUE)) < 0) {
                start = selected.getFirstUid().orElse(MessageUid.MIN_VALUE);
            }
            end = range.getUidTo();
            if (end.equals(MessageUid.MAX_VALUE) || end.compareTo(selected.getLastUid().orElse(MessageUid.MAX_VALUE)) > 0) {
                end = selected.getLastUid().orElse(MessageUid.MAX_VALUE);
            }
            return MessageRange.range(start, end);
        case FROM:
            start = range.getUidFrom();
            if (start.equals(MessageUid.MAX_VALUE) || start.compareTo(selected.getFirstUid().orElse(MessageUid.MIN_VALUE)) < 0) {
                start = selected.getFirstUid().orElse(MessageUid.MIN_VALUE);
            }
            
            end = selected.getLastUid().orElse(MessageUid.MAX_VALUE);
            return MessageRange.range(start, end);
        default:
            throw new RuntimeException("Unknown message range type: " + rangeType);
        }
    }
    
    
    /**
     * Send VANISHED responses if needed. 
     */
    protected void respondVanished(SelectedMailbox selectedMailbox, List<MessageRange> ranges, Responder responder) {
        Set<MessageUid> vanishedUids = new HashSet<>();
        for (MessageRange range : ranges) {
            MessageUid from = range.getUidFrom();
            MessageUid to = range.getUidTo();
            while (from.compareTo(to) <= 0) {
                MessageUid copy = from;
                selectedMailbox.msn(from).foldSilent(
                    () -> vanishedUids.add(copy),
                    msn -> {
                        // ignore still there
                        return true;
                    });
                from = from.next();
            }

        }
        UidRange[] vanishedIdRanges = uidRanges(MessageRange.toRanges(vanishedUids));
        if (vanishedIdRanges.length > 0) {
            responder.respond(new VanishedResponse(vanishedIdRanges, true));
        }
    }

    protected UidRange[] uidRanges(Collection<MessageRange> mRanges) {
        UidRange[] idRanges = new UidRange[mRanges.size()];
        Iterator<MessageRange> mIt = mRanges.iterator();
        int i = 0;
        while (mIt.hasNext()) {
            MessageRange mr = mIt.next();
            UidRange ir;
            if (mr.getType() == Type.ONE) {
                ir = new UidRange(mr.getUidFrom());
            } else {
                ir = new UidRange(mr.getUidFrom(), mr.getUidTo());
            }
            idRanges[i++] = ir;
        }
        return idRanges;
    }

}
