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

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.UidRange;
import org.apache.james.imap.api.message.request.ImapRequest;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponse.ResponseCode;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SearchResUtil;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.AbstractMailboxSelectionRequest;
import org.apache.james.imap.message.request.AbstractMailboxSelectionRequest.ClientSpecifiedUidValidity;
import org.apache.james.imap.message.response.ExistsResponse;
import org.apache.james.imap.message.response.RecentResponse;
import org.apache.james.imap.processor.base.SelectedMailboxImpl;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.MailboxMetaData;
import org.apache.james.mailbox.MessageManager.MailboxMetaData.FetchGroup;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.MessageRangeException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.metrics.api.MetricFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

abstract class AbstractSelectionProcessor<R extends AbstractMailboxSelectionRequest> extends AbstractMailboxProcessor<R> implements PermitEnableCapabilityProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractSelectionProcessor.class);
    private static final List<Capability> CAPS = ImmutableList.of(ImapConstants.SUPPORTS_QRESYNC, ImapConstants.SUPPORTS_CONDSTORE);

    private final StatusResponseFactory statusResponseFactory;
    private final boolean openReadOnly;
    private final EventBus eventBus;
    
    public AbstractSelectionProcessor(Class<R> acceptableClass, ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory statusResponseFactory, boolean openReadOnly,
                                      MetricFactory metricFactory, EventBus eventBus) {
        super(acceptableClass, next, mailboxManager, statusResponseFactory, metricFactory);
        this.statusResponseFactory = statusResponseFactory;
        this.openReadOnly = openReadOnly;

        this.eventBus = eventBus;
    }

    @Override
    protected void processRequest(R request, ImapSession session, Responder responder) {
        final String mailboxName = request.getMailboxName();
        try {
            final MailboxPath fullMailboxPath = PathConverter.forSession(session).buildFullPath(mailboxName);

            respond(session, fullMailboxPath, request, responder);
           
            
        } catch (MailboxNotFoundException e) {
            LOGGER.debug("Select failed as mailbox does not exist {}", mailboxName, e);
            responder.respond(statusResponseFactory.taggedNo(request.getTag(), request.getCommand(), HumanReadableText.FAILURE_NO_SUCH_MAILBOX));
        } catch (MailboxException e) {
            LOGGER.error("Select failed for mailbox {}", mailboxName, e);
            no(request, responder, HumanReadableText.SELECT);
        } 
    }

    private void respond(ImapSession session, MailboxPath fullMailboxPath, AbstractMailboxSelectionRequest request, Responder responder) throws MailboxException, MessageRangeException {

        ClientSpecifiedUidValidity lastKnownUidValidity = request.getLastKnownUidValidity();
        Long modSeq = request.getKnownModSeq();
        IdRange[] knownSequences = request.getKnownSequenceSet();
        UidRange[] knownUids = request.getKnownUidSet();

        // Check if a QRESYNC parameter was used and if so if QRESYNC was enabled before.
        // If it was not enabled before its needed to return a BAD response
        //
        // From RFC5162 3.1. QRESYNC Parameter to SELECT/EXAMINE
        //
        //    A server MUST respond with a tagged BAD response if the Quick
        //    Resynchronization parameter to SELECT/EXAMINE command is specified
        //    and the client hasn't issued "ENABLE QRESYNC" in the current
        //    connection.
        if (!lastKnownUidValidity.isUnknown() && !EnableProcessor.getEnabledCapabilities(session).contains(ImapConstants.SUPPORTS_QRESYNC)) {
            taggedBad(request, responder, HumanReadableText.QRESYNC_NOT_ENABLED);
            return;
        }

        final MailboxMetaData metaData = selectMailbox(fullMailboxPath, session);
        final SelectedMailbox selected = session.getSelected();
        MessageUid firstUnseen = metaData.getFirstUnseen();
        
        flags(responder, selected);
        exists(responder, metaData);
        recent(responder, selected);
        uidValidity(responder, metaData);
        
        
        // try to write the UNSEEN message to the client and retry if we fail because of concurrent sessions.
        // 
        // See IMAP-345
        int retryCount = 0;
        while (unseen(responder, firstUnseen, selected) == false) {
            // if we not was able to get find the unseen within 5 retries we should just not send it
            if (retryCount == 5) {
                LOGGER.info("Unable to uid for unseen message {} in mailbox {}", firstUnseen, selected.getMailboxId().serialize());
                break;
            }
            firstUnseen = selectMailbox(fullMailboxPath, session).getFirstUnseen();
            retryCount++;
            
        }
        
        permanentFlags(responder, metaData, selected);
        highestModSeq(responder, metaData, selected);
        uidNext(responder, metaData);
        
        if (request.getCondstore()) {
           condstoreEnablingCommand(session, responder, metaData, false);
        }
        
        // Now do the QRESYNC processing if necessary
        // 
        // If the mailbox does not store the mod-sequence in a permanent way its needed to not process the QRESYNC paramters
        // The same is true if none are given ;)
        if (metaData.isModSeqPermanent() && !lastKnownUidValidity.isUnknown()) {
            if (lastKnownUidValidity.correspondsTo(metaData.getUidValidity())) {
                
                final MailboxManager mailboxManager = getMailboxManager();
                final MailboxSession mailboxSession = session.getMailboxSession();
                final MessageManager mailbox = mailboxManager.getMailbox(fullMailboxPath, mailboxSession);

                //  If the provided UIDVALIDITY matches that of the selected mailbox, the
                //  server then checks the last known modification sequence.
                //
                //  The server sends the client any pending flag changes (using FETCH
                //  responses that MUST contain UIDs) and expunges those that have
                //  occurred in this mailbox since the provided modification sequence.

                UidRange[] uidSet = request.getUidSet();

                if (uidSet == null) {
                    // See mailbox had some messages stored before, if not we don't need to query at all
                    MessageUid uidNext = metaData.getUidNext();
                    if (!uidNext.isFirst()) {
                        // Use UIDNEXT -1 as max uid as stated in the QRESYNC RFC
                        uidSet = new UidRange[] {new UidRange(MessageUid.MIN_VALUE, uidNext.previous())};
                    }
                }
                
                if (uidSet != null) {
                    // RFC5162 3.1. QRESYNC Parameter to SELECT/EXAMINE
                    //
                    // Message sequence match data:
                    //
                    //      A client MAY provide a parenthesized list of a message sequence set
                    //      and the corresponding UID sets.  Both MUST be provided in ascending
                    //      order.  The server uses this data to restrict the range for which it
                    //      provides expunged message information.
                    //
                    //
                    //      Conceptually, the client provides a small sample of sequence numbers
                    //      for which it knows the corresponding UIDs.  The server then compares
                    //      each sequence number and UID pair the client provides with the
                    //      current state of the mailbox.  If a pair matches, then the client
                    //      knows of any expunges up to, and including, the message, and thus
                    //      will not include that range in the VANISHED response, even if the
                    //      "mod-sequence-value" provided by the client is too old for the server
                    //      to have data of when those messages were expunged.
                    //
                    //      Thus, if the Nth message number in the first set in the list is 4,
                    //      and the Nth UID in the second set in the list is 8, and the mailbox's
                    //      fourth message has UID 8, then no UIDs equal to or less than 8 are
                    //      present in the VANISHED response.  If the (N+1)th message number is
                    //      12, and the (N+1)th UID is 24, and the (N+1)th message in the mailbox
                    //      has UID 25, then the lowest UID included in the VANISHED response
                    //      would be 9.
                    if (knownSequences != null && knownUids != null) {
                        
                        // Add all uids which are contained in the knownuidsset to a List so we can later access them via the index
                        List<MessageUid> knownUidsList = new ArrayList<>();
                        for (UidRange range : knownUids) {
                            for (MessageUid uid : range) {
                                knownUidsList.add(uid);
                            }
                        }
                        
                        // loop over the known sequences and check the UID for MSN X again the known UID X 
                        MessageUid firstUid = MessageUid.MIN_VALUE;
                        int index = 0;
                        for (IdRange knownSequence : knownSequences) {
                            boolean done = false;
                            for (Long uid : knownSequence) {

                                // Check if we have uids left to check against
                                if (knownUidsList.size() > index++) {
                                    int msn = uid.intValue();
                                    MessageUid knownUid = knownUidsList.get(index);

                                    // Check if the uid mathc if not we are done here
                                    done = selected.uid(msn)
                                        .filter(selectedUid -> selectedUid.equals(knownUid))
                                        .isPresent();
                                    if (done) {
                                        break;
                                    } else {
                                        firstUid = knownUid;
                                    }

                                } else {
                                    done = true;
                                    break;
                                }
                            }

                            // We found the first uid to start with 
                            if (done) {
                                firstUid = firstUid.next();

                                // Ok now its time to filter out the IdRanges which we are not interested in
                                List<UidRange> filteredUidSet = new ArrayList<>();
                                for (UidRange r : uidSet) {
                                    if (r.getLowVal().compareTo(firstUid) < 0) {
                                        if (r.getHighVal().compareTo(firstUid) > 0) {
                                            filteredUidSet.add(new UidRange(firstUid, r.getHighVal()));
                                        }
                                    } else {
                                        filteredUidSet.add(r);
                                    }
                                }
                                uidSet = filteredUidSet.toArray(UidRange[]::new);

                                break;
                            }
                        }
                        
                    }
                    
                    List<MessageRange> ranges = new ArrayList<>();
                    for (UidRange range : uidSet) {
                        MessageRange messageSet = range.toMessageRange();
                        if (messageSet != null) {
                            MessageRange normalizedMessageSet = normalizeMessageRange(session.getSelected(), messageSet);
                            ranges.add(normalizedMessageSet);
                        }
                    }
                    
                    // TODO: Reconsider if we can do something to make the handling better. Maybe at least cache the triplets for the expunged
                    //       while have the server running. This could maybe allow us to not return every expunged message all the time
                    //  
                    //      As we don't store the <<MSN, UID>, <MODSEQ>> in a permanent way its the best to just ignore it here.
                    //
                    //      From RFC5162 4.1. Server Implementations That Don't Store Extra State
                    //
                    //
                    //          Strictly speaking, a server implementation that doesn't remember mod-
                    //          sequences associated with expunged messages can be considered
                    //          compliant with this specification.  Such implementations return all
                    //          expunged messages specified in the UID set of the UID FETCH
                    //          (VANISHED) command every time, without paying attention to the
                    //          specified CHANGEDSINCE mod-sequence.  Such implementations are
                    //          discouraged, as they can end up returning VANISHED responses that are
                    //          bigger than the result of a UID SEARCH command for the same UID set.
                    //
                    //          Clients that use the message sequence match data can reduce the scope
                    //          of this VANISHED response substantially in the typical case where
                    //          expunges have not happened, or happen only toward the end of the
                    //          mailbox.
                    //
                    respondVanished(mailboxSession, mailbox, ranges, modSeq, metaData, responder);
                }
                taggedOk(responder, request, metaData, HumanReadableText.SELECT);
            } else {
                
                taggedOk(responder, request, metaData, HumanReadableText.QRESYNC_UIDVALIDITY_MISMATCH);
            }
        } else {
            taggedOk(responder, request, metaData, HumanReadableText.SELECT);
        }

        // Reset the saved sequence-set after successful SELECT / EXAMINE
        // See RFC 5812 2.1. Normative Description of the SEARCHRES Extension
        SearchResUtil.resetSavedSequenceSet(session);
    }



    private void highestModSeq(Responder responder, MailboxMetaData metaData, SelectedMailbox selected) {
        final StatusResponse untaggedOk;
        if (metaData.isModSeqPermanent()) {
            final ModSeq highestModSeq = metaData.getHighestModSeq();
            untaggedOk = statusResponseFactory.untaggedOk(HumanReadableText.HIGHEST_MOD_SEQ, ResponseCode.highestModSeq(highestModSeq));
        } else {
            untaggedOk = statusResponseFactory.untaggedOk(HumanReadableText.NO_MOD_SEQ, ResponseCode.noModSeq());
        }
        responder.respond(untaggedOk);        
    }

    private void uidNext(Responder responder, MailboxMetaData metaData) throws MailboxException {
        final MessageUid uid = metaData.getUidNext();
        final StatusResponse untaggedOk = statusResponseFactory.untaggedOk(HumanReadableText.UIDNEXT, ResponseCode.uidNext(uid));
        responder.respond(untaggedOk);
    }

    private void taggedOk(Responder responder, ImapRequest request, MailboxMetaData metaData, HumanReadableText text) {
        final boolean writeable = metaData.isWriteable() && !openReadOnly;
        final ResponseCode code;
        if (writeable) {
            code = ResponseCode.readWrite();
        } else {
            code = ResponseCode.readOnly();
        }
        final StatusResponse taggedOk = statusResponseFactory.taggedOk(request.getTag(), request.getCommand(), text, code);
        responder.respond(taggedOk);
    }

    private boolean unseen(Responder responder, MessageUid firstUnseen, SelectedMailbox selected) throws MailboxException {
        if (firstUnseen != null) {
            final MessageUid unseenUid = firstUnseen;

            return selected.msn(unseenUid).fold(() -> {
                LOGGER.debug("No message found with uid {} in mailbox {}", unseenUid, selected.getMailboxId().serialize());
                return false;
            }, msn -> {
                final StatusResponse untaggedOk = statusResponseFactory.untaggedOk(HumanReadableText.unseen(msn), ResponseCode.unseen(msn));
                responder.respond(untaggedOk);
                return true;
            });
        }
        return true;


    }

    private void uidValidity(Responder responder, MailboxMetaData metaData) throws MailboxException {
        final UidValidity uidValidity = metaData.getUidValidity();
        final StatusResponse untaggedOk = statusResponseFactory.untaggedOk(HumanReadableText.UID_VALIDITY, ResponseCode.uidValidity(uidValidity));
        responder.respond(untaggedOk);
    }

    private void recent(Responder responder, SelectedMailbox selected) {
        final int recentCount = selected.recentCount();
        final RecentResponse recentResponse = new RecentResponse(recentCount);
        responder.respond(recentResponse);
    }

    private void exists(Responder responder, MailboxMetaData metaData) throws MailboxException {
        final long messageCount = metaData.getMessageCount();
        final ExistsResponse existsResponse = new ExistsResponse(messageCount);
        responder.respond(existsResponse);
    }

    private MailboxMetaData selectMailbox(MailboxPath mailboxPath, ImapSession session) throws MailboxException {
        final MailboxManager mailboxManager = getMailboxManager();
        final MailboxSession mailboxSession = session.getMailboxSession();
        final MessageManager mailbox = mailboxManager.getMailbox(mailboxPath, mailboxSession);

        final SelectedMailbox sessionMailbox;
        final SelectedMailbox currentMailbox = session.getSelected();
        if (currentMailbox == null || !currentMailbox.getMailboxId().equals(mailbox.getId())) {
            
            // QRESYNC EXTENSION
            //
            // Response with the CLOSE return-code when the currently selected mailbox is closed implicitly using the SELECT/EXAMINE command on another mailbox
            //
            // See rfc5162 3.7. CLOSED Response Code
            if (currentMailbox != null) {
                getStatusResponseFactory().untaggedOk(HumanReadableText.QRESYNC_CLOSED, ResponseCode.closed());
            }
            session.selected(new SelectedMailboxImpl(getMailboxManager(), eventBus, session, mailbox));

            sessionMailbox = session.getSelected();
            
        } else {
            // TODO: Check if we need to handle CONDSTORE there too 
            sessionMailbox = currentMailbox;
        }
        final MailboxMetaData metaData = mailbox.getMetaData(!openReadOnly, mailboxSession, MailboxMetaData.FetchGroup.FIRST_UNSEEN);
        addRecent(metaData, sessionMailbox);
        return metaData;
    }


    private void addRecent(MailboxMetaData metaData, SelectedMailbox sessionMailbox) throws MailboxException {
        final List<MessageUid> recentUids = metaData.getRecent();
        for (MessageUid uid : recentUids) {
            sessionMailbox.addRecent(uid);
        }
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        return CAPS;
    }

    @Override
    public List<Capability> getPermitEnableCapabilities(ImapSession session) {
        return CAPS;
    }

    @Override
    public void enable(ImapMessage message, Responder responder, ImapSession session, Capability capability) throws EnableException {

        if (EnableProcessor.getEnabledCapabilities(session).contains(capability) == false) {
            SelectedMailbox sm = session.getSelected();
            // Send a HIGHESTMODSEQ response if the there was a select mailbox before and the client just enabled
            // QRESYNC or CONDSTORE
            //
            // See http://www.dovecot.org/list/dovecot/2008-March/029561.html
            if (capability.equals(ImapConstants.SUPPORTS_CONDSTORE) || capability.equals(ImapConstants.SUPPORTS_QRESYNC)) {
                try {
                    MailboxMetaData metaData  = null;
                    boolean send = false;
                    if (sm != null) {
                        MessageManager mailbox = getSelectedMailbox(session);
                        metaData = mailbox.getMetaData(false, session.getMailboxSession(), FetchGroup.NO_COUNT);
                        send = true;
                    }
                    condstoreEnablingCommand(session, responder, metaData, send);
                } catch (MailboxException e) {
                    throw new EnableException("Unable to enable " + capability.asString(), e);
                }
            }
            
        }
    }
    
    
}
