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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.mail.Flags;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.IdRange;
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
import org.apache.james.mailbox.MessageManager.MetaData;
import org.apache.james.mailbox.MessageManager.MetaData.FetchGroup;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MessageRangeException;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageRange.Type;
import org.apache.james.mailbox.model.FetchGroupImpl;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MessageResultIterator;

public class StoreProcessor extends AbstractMailboxProcessor<StoreRequest> {

    /**
     * The {@link ImapCommand} which should be used for the response if some CONDSTORE option is used
     */
    private final static ImapCommand CONDSTORE_COMMAND = ImapCommand.selectedStateCommand("Conditional STORE");
    
    public StoreProcessor(final ImapProcessor next, final MailboxManager mailboxManager, final StatusResponseFactory factory) {
        super(StoreRequest.class, next, mailboxManager, factory);
    }

    /**
     * @see org.apache.james.imap.processor.AbstractMailboxProcessor
     * #doProcess(org.apache.james.imap.api.message.request.ImapRequest,
     * org.apache.james.imap.api.process.ImapSession, java.lang.String,
     * org.apache.james.imap.api.ImapCommand, org.apache.james.imap.api.process.ImapProcessor.Responder)
     */
    protected void doProcess(StoreRequest request, ImapSession session, String tag, ImapCommand command, Responder responder) {
        final IdRange[] idSet = request.getIdSet();
        final boolean useUids = request.isUseUids();
        final long unchangedSince = request.getUnchangedSince();
        ImapCommand imapCommand = command;
        
        try {
            final MessageManager mailbox = getSelectedMailbox(session);
            final MailboxSession mailboxSession = ImapSessionUtils.getMailboxSession(session);
            final Flags flags = request.getFlags();
            
            if (unchangedSince != -1) {
            	MetaData metaData = mailbox.getMetaData(false, mailboxSession, FetchGroup.NO_COUNT);
                if (metaData.isModSeqPermanent() == false) {
                    // Check if the mailbox did not support modsequences. If so return a tagged bad response.
                    // See RFC4551 3.1.2. NOMODSEQ Response Code 
                    taggedBad(command, tag, responder, HumanReadableText.NO_MOD_SEQ);
                    return;
                } else if (unchangedSince == 0){
                    Flags.Flag[] systemFlags = flags.getSystemFlags();
                    if (systemFlags != null && systemFlags.length != 0) {
                        // we need to return all sequences as failed when using a UNCHANGEDSINCE 0 and the request specify a SYSTEM flags
                        //
                        // See RFC4551 3.2. STORE and UID STORE Command;
                        //
                        //       Use of UNCHANGEDSINCE with a modification sequence of 0 always
                        //       fails if the metadata item exists.  A system flag MUST always be
                        //       considered existent, whether it was set or not.
                        final StatusResponse response = getStatusResponseFactory().taggedOk(tag, command, HumanReadableText.FAILED, ResponseCode.condStore(idSet));
                        responder.respond(response);
                        return;
                    }
                }
              
            } 
            final List<Long> failed = new ArrayList<Long>();
            final List<String> userFlags = Arrays.asList(flags.getUserFlags());
            for (int i = 0; i < idSet.length; i++) {
                final SelectedMailbox selected = session.getSelected();
                MessageRange messageSet = messageRange(selected, idSet[i], useUids);
                if (messageSet != null) {
                    
                    if (unchangedSince != -1) {
                        // Ok we have a CONDSTORE option so use the CONDSTORE_COMMAND
                        imapCommand = CONDSTORE_COMMAND;
                        
                        List<Long> uids = new ArrayList<Long>();

                        MessageResultIterator results = mailbox.getMessages(messageSet, FetchGroupImpl.MINIMAL, mailboxSession);
                        while(results.hasNext()) {
                            MessageResult r = results.next();
                            long uid = r.getUid();
                            
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
                                for (int a = 0; a < uFlags.length; a++) {
                                    if (userFlags.contains(uFlags[a])) {
                                        fail = true;
                                        break;
                                    }
                                }
                            }
                            
                            // Check if the mod-sequence of the message is <= the unchangedsince.
                            // 
                            // See RFC4551 3.2. STORE and UID STORE Commands
                            if (!fail && r.getModSeq() <= unchangedSince) {
                                uids.add(uid);
                            } else {
                                if (useUids) {
                                    failed.add(uid);
                                } else {
                                    failed.add((long) selected.msn(uid));
                                }
                            }
                        }
                        List<MessageRange> mRanges = MessageRange.toRanges(uids);
                        for (int a = 0 ; a < mRanges.size(); a++) {
                            setFlags(request, mailboxSession, mailbox, mRanges.get(a), session, tag, imapCommand, responder);
                        }
                    } else {
                        setFlags(request, mailboxSession, mailbox, messageSet, session, tag, imapCommand, responder);
                    }
                    
                }

                
            }
            final boolean omitExpunged = (!useUids);
            unsolicitedResponses(session, responder, omitExpunged, useUids);
            
            // check if we had some failed uids which didn't pass the UNCHANGEDSINCE filter
            if (failed.isEmpty()) {
                okComplete(imapCommand, tag, responder);
            } else {
                // Convert the MessageRanges to an array of IdRange. 
                // TODO: Maybe this should get moved in an util class
                List<MessageRange> ranges = MessageRange.toRanges(failed);
                IdRange[] idRanges = new IdRange[ranges.size()];
                for (int i = 0 ; i < ranges.size(); i++) {
                    MessageRange r = ranges.get(i);
                    if (r.getType() == Type.ONE) {
                        idRanges[i] = new IdRange(r.getUidFrom());
                    } else {
                        idRanges[i] = new IdRange(r.getUidFrom(), r.getUidTo());
                    }
                }
                // we need to return the failed sequences
                //
                // See RFC4551 3.2. STORE and UID STORE Commands
                final StatusResponse response = getStatusResponseFactory().taggedOk(tag, command, HumanReadableText.FAILED, ResponseCode.condStore(idRanges));
                responder.respond(response);
               
            }
        } catch (MessageRangeException e) {
            if (session.getLog().isDebugEnabled()) {
                session.getLog().debug("Store failed for mailbox " + session.getSelected().getPath() + " because of an invalid sequence-set " + idSet.toString(), e); 
            }
            taggedBad(imapCommand, tag, responder, HumanReadableText.INVALID_MESSAGESET);
        } catch (MailboxException e) {
            if (session.getLog().isInfoEnabled()) {
                session.getLog().info("Store failed for mailbox " + session.getSelected().getPath(), e);
            }
            no(imapCommand, tag, responder, HumanReadableText.SAVE_FAILED);
        }
    }
    
    /**
     * Set the flags for given messages
     * 
     * @param request
     * @param mailboxSession
     * @param mailbox
     * @param messageSet
     * @param selected
     * @param tag
     * @param command
     * @param responder
     * @throws MailboxException
     */
    private void setFlags(StoreRequest request, MailboxSession mailboxSession, MessageManager mailbox, MessageRange messageSet, ImapSession session, String tag, ImapCommand command, Responder responder) throws MailboxException {
        
        final Flags flags = request.getFlags();
        final boolean useUids = request.isUseUids();
        final boolean silent = request.isSilent();
        final boolean isSignedPlus = request.isSignedPlus();
        final boolean isSignedMinus = request.isSignedMinus();
        final long unchangedSince = request.getUnchangedSince();
        final MessageManager.FlagsUpdateMode mode;
        if (isSignedMinus) {
            mode = MessageManager.FlagsUpdateMode.REMOVE;
        } else if (isSignedPlus) {
            mode = MessageManager.FlagsUpdateMode.ADD;
        } else {
            mode = MessageManager.FlagsUpdateMode.REPLACE;
        }
        
        SelectedMailbox selected = session.getSelected();
        final Map<Long, Flags> flagsByUid = mailbox.setFlags(flags, mode, messageSet, mailboxSession);
        // As the STORE command is allowed to create a new "flag/keyword", we need to send a FLAGS and PERMANENTFLAGS response before the FETCH response
        // if some new flag/keyword was used
        // See IMAP-303
        if (selected.hasNewApplicableFlags()) {
            flags(responder, selected);
            permanentFlags(responder, mailbox.getMetaData(false, mailboxSession, FetchGroup.NO_COUNT), selected);
            selected.resetNewApplicableFlags();
        }
        
        Set<String> enabled = EnableProcessor.getEnabledCapabilities(session);
        boolean qresyncEnabled = enabled.contains(ImapConstants.SUPPORTS_QRESYNC);
        boolean condstoreEnabled = enabled.contains(ImapConstants.SUPPORTS_CONDSTORE);
        
        if (!silent || unchangedSince != -1 || qresyncEnabled || condstoreEnabled) {
            final Map<Long, Long> modSeqs = new HashMap<Long, Long>();
           
            // Check if we need to also send the the mod-sequences back to the client
            //
            // This is the case if one of these is true:
            //      - UNCHANGEDSINCE was used
            //      - CONDSTORE was enabled via ENABLE CONDSTORE
            //      - QRESYNC was enabled via ENABLE QRESYNC
            //
            if (unchangedSince != -1 || qresyncEnabled || condstoreEnabled) {
                MessageResultIterator results = mailbox.getMessages(messageSet, FetchGroupImpl.MINIMAL, mailboxSession);
                while(results.hasNext()) {
                    MessageResult r = results.next();
                    // Store the modseq for the uid for later usage in the response
                    modSeqs.put(r.getUid(),r.getModSeq());
                }
            }
            
            for (Map.Entry<Long, Flags> entry : flagsByUid.entrySet()) {
                final long uid = entry.getKey();
                final int msn = selected.msn(uid);

                if (msn == SelectedMailbox.NO_SUCH_MESSAGE) {
                    if(session.getLog().isDebugEnabled()) {
                        session.getLog().debug("No message found with uid " + uid + " in the uid<->msn mapping for mailbox " + selected.getPath().getFullName(mailboxSession.getPathDelimiter()) +" , this may be because it was deleted by a concurrent session. So skip it..");
                        
                    }
                    // skip this as it was not found in the mapping
                    // 
                    // See IMAP-346
                    continue;
                }

                final Flags resultFlags = entry.getValue();
                final Long resultUid;
                
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
                // For more informations related to the FETCH response see
                //
                // RFC4551 3.2. STORE and UID STORE Commands
                if (silent && (unchangedSince != -1 || qresyncEnabled || condstoreEnabled)) {
                    // We need to return an FETCH response which contains the mod-sequence of the message even if FLAGS.SILENT was used
                    response = new FetchResponse(msn, null, resultUid, modSeqs.get(uid), null, null, null, null, null, null);
                } else if (!silent && (unchangedSince != -1 || qresyncEnabled || condstoreEnabled)){
                    //
                    // Use a FETCH response which contains the mod-sequence and the flags
                    response = new FetchResponse(msn, resultFlags, resultUid, modSeqs.get(uid), null, null, null, null, null, null);
                } else {
                    // Use a FETCH response which only contains the flags as no CONDSTORE was used
                    response = new FetchResponse(msn, resultFlags, resultUid, null, null, null, null, null, null, null);
                }
                responder.respond(response);
            }

            if (unchangedSince != -1) {
                // Enable CONDSTORE as this is a CONDSTORE enabling command
                condstoreEnablingCommand(session, responder,  mailbox.getMetaData(false, mailboxSession, FetchGroup.NO_COUNT), true);
                                  
            }
        }
        
    }
}
