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

import static org.apache.james.mailbox.MessageManager.MailboxMetaData.RecentMode.IGNORE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.mail.Flags;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.UidRange;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponse.ResponseCode;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.message.request.StoreRequest;
import org.apache.james.imap.message.response.FetchResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.MailboxMetaData;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.NullableMessageSequenceNumber;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MessageRangeException;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class StoreProcessor extends AbstractMailboxProcessor<StoreRequest> {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoreProcessor.class);

    @Inject
    public StoreProcessor(MailboxManager mailboxManager, StatusResponseFactory factory,
            MetricFactory metricFactory) {
        super(StoreRequest.class, mailboxManager, factory, metricFactory);
    }

    @Override
    protected Mono<Void> processRequestReactive(StoreRequest request, ImapSession session, Responder responder) {
        IdRange[] idSet = request.getIdSet();
        List<MessageUid> failed = new ArrayList<>();
        List<NullableMessageSequenceNumber> failedMsns = new ArrayList<>();
        Flags flags = request.getFlags();
        List<String> userFlags = Arrays.asList(flags.getUserFlags());
        boolean omitExpunged = (!request.isUseUids());

        if (rejectUnchangedSinceZeroWithSystemFlagUpdate(request, responder, idSet, flags)) {
            return Mono.empty();
        }

        SelectedMailbox selected = session.getSelected();
            MailboxSession mailboxSession = session.getMailboxSession();

        return getSelectedMailboxReactive(session)
            .flatMap(mailbox -> Flux.fromIterable(ImmutableList.copyOf(idSet))
                .map(Throwing.<IdRange, MessageRange>function(idRange -> messageRange(selected, idRange, request.isUseUids())
                    .orElseThrow(() -> new MessageRangeException(idRange.getFormattedString() + " is an invalid range")))
                    .sneakyThrow())
                .concatMap(messageSet -> handleRange(request, session, responder, selected, mailbox, mailboxSession, failed, failedMsns, userFlags, messageSet))
                .then())
            .then(unsolicitedResponses(session, responder, omitExpunged, request.isUseUids()))
            .doOnSuccess(any -> {
                // check if we had some failed uids which didn't pass the UNCHANGEDSINCE filter
                if (failed.isEmpty() && failedMsns.isEmpty()) {
                    okComplete(request, responder);
                } else {
                    respondFailed(request, responder, failed, failedMsns);
                    }
            })
            .onErrorResume(MessageRangeException.class, e -> {
                taggedBad(request, responder, HumanReadableText.INVALID_MESSAGESET);
                return ReactorUtils.logAsMono(() -> LOGGER.debug("Store failed for mailbox {} because of an invalid sequence-set {}", session.getSelected().getMailboxId(), idSet, e));
            })
            .onErrorResume(MailboxException.class, e -> {
                no(request, responder, HumanReadableText.SAVE_FAILED);
                return ReactorUtils.logAsMono(() -> LOGGER.error("Store failed for mailbox {} and sequence-set {}", session.getSelected().getMailboxId(), idSet, e));
            });
                }
              
    private Mono<Void> handleRange(StoreRequest request, ImapSession session, Responder responder, SelectedMailbox selected, MessageManager mailbox, MailboxSession mailboxSession, List<MessageUid> failed, List<NullableMessageSequenceNumber> failedMsns, List<String> userFlags, MessageRange messageSet) {
        if (messageSet != null) {
            if (request.getUnchangedSince() != -1) {
                return Flux.from(mailbox.listMessagesMetadata(messageSet, mailboxSession))
                    .<MessageUid>handle((id, sink) -> filterIfFailed(request, selected, failed, failedMsns, userFlags, id).ifPresent(sink::next)).collectList()
                    .flatMapIterable(MessageRange::toRanges)
                    .concatMap(range -> setFlags(request, mailboxSession, mailbox, range, session, responder))
                    .then();
            } else {
                return setFlags(request, mailboxSession, mailbox, messageSet, session, responder);
            } 
        }
        return Mono.empty();
    }

    private Optional<MessageUid> filterIfFailed(StoreRequest request, SelectedMailbox selected, List<MessageUid> failed, List<NullableMessageSequenceNumber> failedMsns, List<String> userFlags, ComposedMessageIdWithMetaData r) {
                            MessageUid uid = r.getComposedMessageId().getUid();

                            boolean fail = false;

                            // Check if UNCHANGEDSINCE 0 was used and the Message contains the request flag.
                            // In such cases we need to fail for this message.
                            //
                            // From RFC4551:
                            //       Use of UNCHANGEDSINCE with a modification sequence of 0 always
                            //       fails if the metadata item exists.  A system flag MUST always be
                            //       considered existent, whether it was set or not.
        if (request.getUnchangedSince() == 0) {
                                String[] uFlags = r.getFlags().getUserFlags();
                                for (String uFlag : uFlags) {
                                    if (userFlags.contains(uFlag)) {
                                        fail = true;
                                        break;
                                    }
                                }
                            }

                            // Check if the mod-sequence of the message is <= the unchangedsince.
                            //
                            // See RFC4551 3.2. STORE and UID STORE Commands
        if (!fail && r.getModSeq().asLong() <= request.getUnchangedSince()) {
            return Optional.of(uid);
                            } else {
            if (request.isUseUids()) {
                                    failed.add(uid);
                                } else {
                                    failedMsns.add(selected.msn(uid));
                                }
            return Optional.empty();
                            }
                        }

    private boolean rejectUnchangedSinceZeroWithSystemFlagUpdate(StoreRequest request, Responder responder, IdRange[] idSet, Flags flags) {
        if (request.getUnchangedSince() == 0) {
            Flags.Flag[] systemFlags = flags.getSystemFlags();
            if (systemFlags != null && systemFlags.length != 0) {
                // we need to return all sequences as failed when using a UNCHANGEDSINCE 0 and the request specify a SYSTEM flags
                //
                // See RFC4551 3.2. STORE and UID STORE Command
                //
                //       Use of UNCHANGEDSINCE with a modification sequence of 0 always
                //       fails if the metadata item exists.  A system flag MUST always be
                //       considered existent, whether it was set or not.
                StatusResponse response = getStatusResponseFactory().taggedOk(request.getTag(), request.getCommand(), HumanReadableText.FAILED, ResponseCode.condStore(idSet));
                responder.respond(response);
                return true;
                        }
                    }
        return false;
                }

    private void respondFailed(StoreRequest request, Responder responder, List<MessageUid> failed, List<NullableMessageSequenceNumber> failedMsns) {
        if (request.isUseUids()) {
            UidRange[] idRanges = MessageRange.toRanges(failed)
                .stream()
                .map(r -> new UidRange(r.getUidFrom(), r.getUidTo()))
                .toArray(UidRange[]::new);
                    // we need to return the failed sequences
                    //
                    // See RFC4551 3.2. STORE and UID STORE Commands
            StatusResponse response = getStatusResponseFactory().taggedOk(request.getTag(), request.getCommand(), HumanReadableText.FAILED, ResponseCode.condStore(idRanges));
                    responder.respond(response);
                } else {
                    List<IdRange> ranges = new ArrayList<>();
                    for (NullableMessageSequenceNumber msn: failedMsns) {
                        msn.ifPresent(id -> ranges.add(new IdRange(id.asInt())));
                    }
                    IdRange[] failedRanges = IdRange.mergeRanges(ranges).toArray(IdRange[]::new);
                    // See RFC4551 3.2. STORE and UID STORE Commands
            StatusResponse response = getStatusResponseFactory().taggedOk(request.getTag(), request.getCommand(), HumanReadableText.FAILED, ResponseCode.condStore(failedRanges));
                    responder.respond(response);
                }
            }
    
    /**
     * Set the flags for given messages
     */
    private Mono<Void> setFlags(StoreRequest request, MailboxSession mailboxSession, MessageManager mailbox, MessageRange messageSet, ImapSession session, Responder responder) {
        boolean silent = request.isSilent();
        long unchangedSince = request.getUnchangedSince();
        
        SelectedMailbox selected = session.getSelected();
        return Mono.from(mailbox.setFlagsReactive(request.getFlags(), request.getFlagsUpdateMode(), messageSet, mailboxSession))
            .doOnNext(flagsByUid -> handlePermanentFlagChanges(mailboxSession, mailbox, responder, selected))
            .flatMap(flagsByUid -> handleCondstore(request, mailboxSession, mailbox, messageSet, session, responder, silent, unchangedSince, selected, flagsByUid));
        }
        
    private Mono<Void> handleCondstore(StoreRequest request, MailboxSession mailboxSession, MessageManager mailbox, MessageRange messageSet, ImapSession session, Responder responder, boolean silent, long unchangedSince, SelectedMailbox selected, Map<MessageUid, Flags> flagsByUid) {
        Set<Capability> enabled = EnableProcessor.getEnabledCapabilities(session);
        boolean qresyncEnabled = enabled.contains(ImapConstants.SUPPORTS_QRESYNC);
        boolean condstoreEnabled = enabled.contains(ImapConstants.SUPPORTS_CONDSTORE);
        
        if (!silent || unchangedSince != -1 || qresyncEnabled || condstoreEnabled) {
            return computeModSeqs(mailboxSession, mailbox, messageSet, unchangedSince, qresyncEnabled, condstoreEnabled)
                .flatMap(Throwing.function(modSeqs -> {
                    sendFetchResponses(responder, request.isUseUids(), silent, unchangedSince, selected, flagsByUid, qresyncEnabled, condstoreEnabled, modSeqs);
           
                    if (unchangedSince != -1) {
                        // Enable CONDSTORE as this is a CONDSTORE enabling command
                        return mailbox.getMetaDataReactive(IGNORE, mailboxSession, EnumSet.of(MailboxMetaData.Item.HighestModSeq))
                            .doOnNext(metaData -> condstoreEnablingCommand(session, responder,  metaData, true));
                }
                    return Mono.empty();
                })).then();
            }
        return Mono.empty();
    }
            
    private void handlePermanentFlagChanges(MailboxSession mailboxSession, MessageManager mailbox, Responder responder, SelectedMailbox selected) {
        // As the STORE command is allowed to create a new "flag/keyword", we need to send a FLAGS and PERMANENTFLAGS response before the FETCH response
        // if some new flag/keyword was used
        // See IMAP-303
        if (selected.hasNewApplicableFlags()) {
            flags(responder, selected);
            permanentFlags(responder, mailbox.getPermanentFlags(mailboxSession), selected);
            selected.resetNewApplicableFlags();
        }
    }

    private void sendFetchResponses(Responder responder, boolean useUids, boolean silent, long unchangedSince, SelectedMailbox selected, Map<MessageUid, Flags> flagsByUid, boolean qresyncEnabled, boolean condstoreEnabled, Map<MessageUid, ModSeq> modSeqs) {
            for (Map.Entry<MessageUid, Flags> entry : flagsByUid.entrySet()) {
                final MessageUid uid = entry.getKey();

            selected.msn(uid).foldSilent(() -> {
                LOGGER.debug("No message found with uid {} in the uid<->msn mapping for mailbox {}. This may be because it was deleted by a concurrent session. So skip it..", uid, selected.getMailboxId());
                    // skip this as it was not found in the mapping
                    // 
                    // See IMAP-346
                    return null;
                }, msn -> {

                    final Flags resultFlags = entry.getValue();
                    final MessageUid resultUid;

                    // Check if we need to include the uid. T
                    //
                    // This is the case if one of these is true:
                    //      - FETCH (UID...)  was used
                    //      - QRESYNC was enabled via ENABLE QRESYNC
                    if (useUids || qresyncEnabled) {
                        resultUid = uid;
                    } else {
                        resultUid = null;
                    }

                    if (selected.isRecent(uid)) {
                        resultFlags.add(Flags.Flag.RECENT);
                    }

                FetchResponse response = computeFetchResponse(silent, unchangedSince, qresyncEnabled, condstoreEnabled, modSeqs, uid, msn, resultFlags, resultUid);
                responder.respond(response);
                return null;
            });
        }
    }

    private Mono<Map<MessageUid, ModSeq>> computeModSeqs(MailboxSession mailboxSession, MessageManager mailbox, MessageRange messageSet, long unchangedSince, boolean qresyncEnabled, boolean condstoreEnabled) {
        // Check if we need to also send the the mod-sequences back to the client
        //
        // This is the case if one of these is true:
        //      - UNCHANGEDSINCE was used
        //      - CONDSTORE was enabled via ENABLE CONDSTORE
        //      - QRESYNC was enabled via ENABLE QRESYNC
        //
        if (unchangedSince != -1 || qresyncEnabled || condstoreEnabled) {
            return Flux.from(mailbox.listMessagesMetadata(messageSet, mailboxSession))
                .collectMap(r -> r.getComposedMessageId().getUid(), ComposedMessageIdWithMetaData::getModSeq);
        }
        return Mono.just(ImmutableMap.of());
    }

    private FetchResponse computeFetchResponse(boolean silent, long unchangedSince, boolean qresyncEnabled, boolean condstoreEnabled, Map<MessageUid, ModSeq> modSeqs, MessageUid uid, org.apache.james.mailbox.MessageSequenceNumber msn, Flags resultFlags, MessageUid resultUid) {
                    // For more information related to the FETCH response see
                    //
                    // RFC4551 3.2. STORE and UID STORE Commands
        if (unchangedSince != -1 || qresyncEnabled || condstoreEnabled) {
            if (silent) {
                        // We need to return an FETCH response which contains the mod-sequence of the message even if FLAGS.SILENT was used
                return new FetchResponse(msn, null, resultUid, null, modSeqs.get(uid), null, null, null, null, null, null, null, null);
            } else {
                        // Use a FETCH response which contains the mod-sequence and the flags
                return new FetchResponse(msn, resultFlags, resultUid, null, modSeqs.get(uid), null, null, null, null, null, null, null, null);
            }
                    } else {
                        // Use a FETCH response which only contains the flags as no CONDSTORE was used
            return new FetchResponse(msn, resultFlags, resultUid, null, null, null, null, null, null, null, null, null, null);
                    }
            }

    @Override
    protected MDCBuilder mdc(StoreRequest message) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "STORE")
            .addToContext("ranges", IdRange.toString(message.getIdSet()))
            .addToContext("useUids", Boolean.toString(message.isUseUids()))
            .addToContext("unchangedSince", Long.toString(message.getUnchangedSince()))
            .addToContext("isSilent", Boolean.toString(message.isSilent()));
    }
}
