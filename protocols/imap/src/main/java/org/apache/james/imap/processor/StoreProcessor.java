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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.mail.Flags;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.UidRange;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponse.ResponseCode;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
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
import org.apache.james.mailbox.model.MessageRange.Type;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;

public class StoreProcessor extends AbstractMailboxProcessor<StoreRequest> {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoreProcessor.class);

    /**
     * The {@link ImapCommand} which should be used for the response if some CONDSTORE option is used
     */
    private static final ImapCommand CONDSTORE_COMMAND = ImapCommand.selectedStateCommand("Conditional STORE");
    
    public StoreProcessor(ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory,
            MetricFactory metricFactory) {
        super(StoreRequest.class, next, mailboxManager, factory, metricFactory);
    }

    @Override
    protected void processRequest(StoreRequest request, ImapSession session, Responder responder) {
        IdRange[] idSet = request.getIdSet();
        boolean useUids = request.isUseUids();
        long unchangedSince = request.getUnchangedSince();

        try {
            MessageManager mailbox = getSelectedMailbox(session)
                .orElseThrow(() -> new MailboxException("Session not in SELECTED state"));
            MailboxSession mailboxSession = session.getMailboxSession();
            Flags flags = request.getFlags();

            if (unchangedSince != -1) {
                MailboxMetaData metaData = mailbox.getMetaData(false, mailboxSession, MailboxMetaData.FetchGroup.NO_COUNT);
                if (!metaData.isModSeqPermanent()) {
                    // Check if the mailbox did not support modsequences. If so return a tagged bad response.
                    // See RFC4551 3.1.2. NOMODSEQ Response Code 
                    taggedBad(request, responder, HumanReadableText.NO_MOD_SEQ);
                    return;
                } else if (unchangedSince == 0) {
                    Flags.Flag[] systemFlags = flags.getSystemFlags();
                    if (systemFlags != null && systemFlags.length != 0) {
                        // we need to return all sequences as failed when using a UNCHANGEDSINCE 0 and the request specify a SYSTEM flags
                        //
                        // See RFC4551 3.2. STORE and UID STORE Command;
                        //
                        //       Use of UNCHANGEDSINCE with a modification sequence of 0 always
                        //       fails if the metadata item exists.  A system flag MUST always be
                        //       considered existent, whether it was set or not.
                        final StatusResponse response = getStatusResponseFactory().taggedOk(request.getTag(), request.getCommand(), HumanReadableText.FAILED, ResponseCode.condStore(idSet));
                        responder.respond(response);
                        return;
                    }
                }
              
            } 
            final List<MessageUid> failed = new ArrayList<>();
            List<NullableMessageSequenceNumber> failedMsns = new ArrayList<>();
            final List<String> userFlags = Arrays.asList(flags.getUserFlags());
            for (IdRange range : idSet) {
                final SelectedMailbox selected = session.getSelected();
                MessageRange messageSet = messageRange(selected, range, useUids);
                if (messageSet != null) {

                    if (unchangedSince != -1) {
                        List<MessageUid> uids = new ArrayList<>();

                        Iterator<ComposedMessageIdWithMetaData> results = Flux.from(
                            mailbox.listMessagesMetadata(messageSet, mailboxSession))
                            .toStream()
                            .iterator();
                        while (results.hasNext()) {
                            ComposedMessageIdWithMetaData r = results.next();
                            MessageUid uid = r.getComposedMessageId().getUid();

                            boolean fail = false;

                            // Check if UNCHANGEDSINCE 0 was used and the Message contains the request flag.
                            // In such cases we need to fail for this message.
                            //
                            // From RFC4551:
                            //       Use of UNCHANGEDSINCE with a modification sequence of 0 always
                            //       fails if the metadata item exists.  A system flag MUST always be
                            //       considered existent, whether it was set or not.
                            if (unchangedSince == 0) {
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
                            if (!fail && r.getModSeq().asLong() <= unchangedSince) {
                                uids.add(uid);
                            } else {
                                if (useUids) {
                                    failed.add(uid);
                                } else {
                                    failedMsns.add(selected.msn(uid));
                                }
                            }
                        }
                        List<MessageRange> mRanges = MessageRange.toRanges(uids);
                        for (MessageRange mRange : mRanges) {
                            setFlags(request, mailboxSession, mailbox, mRange, session, responder);
                        }
                    } else {
                        setFlags(request, mailboxSession, mailbox, messageSet, session, responder);
                    }

                }


            }
            final boolean omitExpunged = (!useUids);
            unsolicitedResponses(session, responder, omitExpunged, useUids);
            
            // check if we had some failed uids which didn't pass the UNCHANGEDSINCE filter
            if (failed.isEmpty() && failedMsns.isEmpty()) {
                okComplete(request, responder);
            } else {
                if (useUids) {
                    List<MessageRange> ranges = MessageRange.toRanges(failed);
                    UidRange[] idRanges = new UidRange[ranges.size()];
                    for (int i = 0; i < ranges.size(); i++) {
                        MessageRange r = ranges.get(i);
                        if (r.getType() == Type.ONE) {
                            idRanges[i] = new UidRange(r.getUidFrom());
                        } else {
                            idRanges[i] = new UidRange(r.getUidFrom(), r.getUidTo());
                        }
                    }
                    // we need to return the failed sequences
                    //
                    // See RFC4551 3.2. STORE and UID STORE Commands
                    final StatusResponse response = getStatusResponseFactory().taggedOk(request.getTag(), request.getCommand(), HumanReadableText.FAILED, ResponseCode.condStore(idRanges));
                    responder.respond(response);
                } else {
                    List<IdRange> ranges = new ArrayList<>();
                    for (NullableMessageSequenceNumber msn: failedMsns) {
                        msn.ifPresent(id -> ranges.add(new IdRange(id.asInt())));
                    }
                    IdRange[] failedRanges = IdRange.mergeRanges(ranges).toArray(IdRange[]::new);
                    // See RFC4551 3.2. STORE and UID STORE Commands
                    final StatusResponse response = getStatusResponseFactory().taggedOk(request.getTag(), request.getCommand(), HumanReadableText.FAILED, ResponseCode.condStore(failedRanges));
                    responder.respond(response);
                    
                }
            }
        } catch (MessageRangeException e) {
            LOGGER.debug("Store failed for mailbox {} because of an invalid sequence-set {}", session.getSelected().getMailboxId(), idSet, e);
            taggedBad(request, responder, HumanReadableText.INVALID_MESSAGESET);
        } catch (MailboxException e) {
            LOGGER.error("Store failed for mailbox {} and sequence-set {}", session.getSelected().getMailboxId(), idSet, e);
            no(request, responder, HumanReadableText.SAVE_FAILED);
        }
    }
    
    /**
     * Set the flags for given messages
     */
    private void setFlags(StoreRequest request, MailboxSession mailboxSession, MessageManager mailbox, MessageRange messageSet, ImapSession session, Responder responder) throws MailboxException {
        
        final Flags flags = request.getFlags();
        final boolean useUids = request.isUseUids();
        final boolean silent = request.isSilent();
        final long unchangedSince = request.getUnchangedSince();
        final MessageManager.FlagsUpdateMode mode = request.getFlagsUpdateMode();
        
        SelectedMailbox selected = session.getSelected();
        final Map<MessageUid, Flags> flagsByUid = mailbox.setFlags(flags, mode, messageSet, mailboxSession);
        // As the STORE command is allowed to create a new "flag/keyword", we need to send a FLAGS and PERMANENTFLAGS response before the FETCH response
        // if some new flag/keyword was used
        // See IMAP-303
        if (selected.hasNewApplicableFlags()) {
            flags(responder, selected);
            permanentFlags(responder, mailbox.getMetaData(false, mailboxSession, MailboxMetaData.FetchGroup.NO_COUNT), selected);
            selected.resetNewApplicableFlags();
        }
        
        Set<Capability> enabled = EnableProcessor.getEnabledCapabilities(session);
        boolean qresyncEnabled = enabled.contains(ImapConstants.SUPPORTS_QRESYNC);
        boolean condstoreEnabled = enabled.contains(ImapConstants.SUPPORTS_CONDSTORE);
        
        if (!silent || unchangedSince != -1 || qresyncEnabled || condstoreEnabled) {
            final Map<MessageUid, ModSeq> modSeqs = new HashMap<>();
           
            // Check if we need to also send the the mod-sequences back to the client
            //
            // This is the case if one of these is true:
            //      - UNCHANGEDSINCE was used
            //      - CONDSTORE was enabled via ENABLE CONDSTORE
            //      - QRESYNC was enabled via ENABLE QRESYNC
            //
            if (unchangedSince != -1 || qresyncEnabled || condstoreEnabled) {
                Iterator<ComposedMessageIdWithMetaData> results = Flux.from(
                    mailbox.listMessagesMetadata(messageSet, mailboxSession))
                    .toStream()
                    .iterator();
                while (results.hasNext()) {
                    ComposedMessageIdWithMetaData r = results.next();
                    // Store the modseq for the uid for later usage in the response
                    modSeqs.put(r.getComposedMessageId().getUid(),r.getModSeq());
                }
            }
            
            for (Map.Entry<MessageUid, Flags> entry : flagsByUid.entrySet()) {
                final MessageUid uid = entry.getKey();

                selected.msn(uid).fold(() -> {
                    LOGGER.debug("No message found with uid {} in the uid<->msn mapping for mailbox {}. This may be because it was deleted by a concurrent session. So skip it..", uid, selected.getPath().asString());
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

                    final FetchResponse response;
                    // For more information related to the FETCH response see
                    //
                    // RFC4551 3.2. STORE and UID STORE Commands
                    if (silent && (unchangedSince != -1 || qresyncEnabled || condstoreEnabled)) {
                        // We need to return an FETCH response which contains the mod-sequence of the message even if FLAGS.SILENT was used
                        response = new FetchResponse(msn, null, resultUid, modSeqs.get(uid), null, null, null, null, null, null);
                    } else if (!silent && (unchangedSince != -1 || qresyncEnabled || condstoreEnabled)) {
                        //
                        // Use a FETCH response which contains the mod-sequence and the flags
                        response = new FetchResponse(msn, resultFlags, resultUid, modSeqs.get(uid), null, null, null, null, null, null);
                    } else {
                        // Use a FETCH response which only contains the flags as no CONDSTORE was used
                        response = new FetchResponse(msn, resultFlags, resultUid, null, null, null, null, null, null, null);
                    }
                    responder.respond(response);
                    return null;
                });
            }

            if (unchangedSince != -1) {
                // Enable CONDSTORE as this is a CONDSTORE enabling command
                condstoreEnablingCommand(session, responder,  mailbox.getMetaData(false, mailboxSession, MailboxMetaData.FetchGroup.NO_COUNT), true);
                                  
            }
        }
        
    }

    @Override
    protected Closeable addContextToMDC(StoreRequest message) {
        return MDCBuilder.create()
            .addContext(MDCBuilder.ACTION, "STORE")
            .addContext("ranges", IdRange.toString(message.getIdSet()))
            .addContext("useUids", message.isUseUids())
            .addContext("unchangedSince", message.getUnchangedSince())
            .addContext("isSilent", message.isSilent())
            .build();
    }
}
