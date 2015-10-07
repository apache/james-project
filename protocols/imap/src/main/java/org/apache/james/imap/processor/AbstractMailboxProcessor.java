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

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.mail.Flags;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.request.ImapRequest;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponse.ResponseCode;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.message.response.ExistsResponse;
import org.apache.james.imap.message.response.ExpungeResponse;
import org.apache.james.imap.message.response.FetchResponse;
import org.apache.james.imap.message.response.FlagsResponse;
import org.apache.james.imap.message.response.RecentResponse;
import org.apache.james.imap.message.response.VanishedResponse;
import org.apache.james.imap.processor.base.AbstractChainedProcessor;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.MetaData;
import org.apache.james.mailbox.MessageManager.MetaData.FetchGroup;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MessageRangeException;
import org.apache.james.mailbox.model.FetchGroupImpl;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageRange.Type;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MessageResultIterator;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.NumericRange;

abstract public class AbstractMailboxProcessor<M extends ImapRequest> extends AbstractChainedProcessor<M> {

    private final MailboxManager mailboxManager;
    private final StatusResponseFactory factory;

    public AbstractMailboxProcessor(final Class<M> acceptableClass, final ImapProcessor next, final MailboxManager mailboxManager, final StatusResponseFactory factory) {
        super(acceptableClass, next);
        this.mailboxManager = mailboxManager;
        this.factory = factory;
    }

    protected final void doProcess(final M acceptableMessage, final Responder responder, final ImapSession session) {
        final M request = acceptableMessage;
        process(request, responder, session);
    }

    protected final void process(final M message, final Responder responder, final ImapSession session) {
        final ImapCommand command = message.getCommand();
        final String tag = message.getTag();
        doProcess(message, command, tag, responder, session);
    }

    final void doProcess(final M message, final ImapCommand command, final String tag, Responder responder, ImapSession session) {
        if (!command.validForState(session.getState())) {
            ImapResponseMessage response = factory.taggedNo(tag, command, HumanReadableText.INVALID_COMMAND);
            responder.respond(response);

        } else {
            getMailboxManager().startProcessingRequest(ImapSessionUtils.getMailboxSession(session));

            doProcess(message, session, tag, command, responder);

            getMailboxManager().endProcessingRequest(ImapSessionUtils.getMailboxSession(session));

        }
    }


    protected void flags(Responder responder, SelectedMailbox selected) {
        responder.respond(new FlagsResponse(selected.getApplicableFlags()));
    }

    protected void permanentFlags(Responder responder, MessageManager.MetaData metaData, SelectedMailbox selected) {
        final Flags permanentFlags = metaData.getPermanentFlags();
        if (permanentFlags.contains(Flags.Flag.USER)) {
            permanentFlags.add(selected.getApplicableFlags());
        }
        final StatusResponse untaggedOk = factory.untaggedOk(HumanReadableText.permanentFlags(permanentFlags), ResponseCode.permanentFlags(permanentFlags));
        responder.respond(untaggedOk);
    }
    
    protected void unsolicitedResponses(final ImapSession session, final ImapProcessor.Responder responder, boolean useUids) {
        unsolicitedResponses(session, responder, false, useUids);
    }

    /**
     * Sends any unsolicited responses to the client, such as EXISTS and FLAGS
     * responses when the selected mailbox is modified by another user.
     */
    protected void unsolicitedResponses(final ImapSession session, final ImapProcessor.Responder responder, boolean omitExpunged, boolean useUid) {
        final SelectedMailbox selected = session.getSelected();
        if (selected == null) {
            if (session.getLog().isDebugEnabled()) {
                session.getLog().debug("No mailbox selected");
            }
        } else {
            unsolicitedResponses(session, responder, selected, omitExpunged, useUid);
        }
    }

    private void unsolicitedResponses(final ImapSession session, final ImapProcessor.Responder responder, final SelectedMailbox selected, boolean omitExpunged, boolean useUid) {
        final boolean sizeChanged = selected.isSizeChanged();
        // New message response
        if (sizeChanged) {
            addExistsResponses(session, selected, responder);
        }
        // Expunged messages
        if (!omitExpunged) {
            final Collection<Long> expungedUids = selected.expungedUids();
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

        // Message updates
        addFlagsResponses(session, selected, responder, useUid);
        
        selected.resetEvents();
    }

    private void addExpungedResponses(SelectedMailbox selected, Collection<Long> expungedUids, final ImapProcessor.Responder responder) {
        for (final Long uid : expungedUids) {
            final long uidValue = uid.longValue();

            // we need to remove the message in the loop to the sequence numbers
            // are updated correctly.
            // See 7.4.1. EXPUNGE Response
            final int msn = selected.remove(uidValue);
            ExpungeResponse response = new ExpungeResponse(msn);
            responder.respond(response);
        }
    }
    
    private void addVanishedResponse(SelectedMailbox selected, Collection<Long> expungedUids, final ImapProcessor.Responder responder) {
        for (final Long uid : expungedUids) {
            final long uidValue = uid.longValue();
            selected.remove(uidValue);
        }
        IdRange[] uidRange = idRanges(MessageRange.toRanges(expungedUids));
        responder.respond(new VanishedResponse(uidRange, false));
    }
    
    private void addFlagsResponses(final ImapSession session, final SelectedMailbox selected, final ImapProcessor.Responder responder, boolean useUid) {
       
        try {
  
            // To be lazily initialized only if needed, which is in minority of cases.
            MessageManager messageManager = null;

            final MailboxSession mailboxSession = ImapSessionUtils.getMailboxSession(session);

            // Check if we need to send a FLAGS and PERMANENTFLAGS response before the FETCH response
            // This is the case if some new flag/keyword was used
            // See IMAP-303
            if (selected.hasNewApplicableFlags()) {
                messageManager = getMailbox(session, selected);
                flags(responder, selected);
                permanentFlags(responder, messageManager.getMetaData(false, mailboxSession, MessageManager.MetaData.FetchGroup.NO_COUNT), selected);
                selected.resetNewApplicableFlags();
            }
            
            final Collection<Long> flagUpdateUids = selected.flagUpdateUids();
            if (!flagUpdateUids.isEmpty()) {
                Iterator<MessageRange> ranges = MessageRange.toRanges(flagUpdateUids).iterator();
                while(ranges.hasNext()) {
                 if (messageManager == null)
                 messageManager = getMailbox(session, selected);
                    addFlagsResponses(session, selected, responder, useUid, ranges.next(), messageManager, mailboxSession);
                }

            }
            
        } catch (MailboxException e) {
            handleResponseException(responder, e, HumanReadableText.FAILURE_TO_LOAD_FLAGS, session);
        }

    }
    
    protected void addFlagsResponses(final ImapSession session, final SelectedMailbox selected, final ImapProcessor.Responder responder, boolean useUid, MessageRange messageSet, MessageManager mailbox, MailboxSession mailboxSession) throws MailboxException {

        final MessageResultIterator it = mailbox.getMessages(messageSet, FetchGroupImpl.MINIMAL,  mailboxSession);
        while (it.hasNext()) {
            MessageResult mr = it.next();
            final long uid = mr.getUid();
            int msn = selected.msn(uid);
            if (msn == SelectedMailbox.NO_SUCH_MESSAGE) {
                if (session.getLog().isDebugEnabled()) {
                    session.getLog().debug("No message found with uid " + uid + " in the uid<->msn mapping for mailbox " + selected.getPath().getFullName(mailboxSession.getPathDelimiter()) +" , this may be because it was deleted by a concurrent session. So skip it..");
                }  
                    

                // skip this as it was not found in the mapping
                // 
                // See IMAP-346
                continue;
            }

            boolean qresyncEnabled = EnableProcessor.getEnabledCapabilities(session).contains(ImapConstants.SUPPORTS_QRESYNC);
            boolean condstoreEnabled = EnableProcessor.getEnabledCapabilities(session).contains(ImapConstants.SUPPORTS_CONDSTORE);

            final Flags flags = mr.getFlags();
            final Long uidOut;
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
            if ((condstoreEnabled || qresyncEnabled) && mailbox.getMetaData(false, mailboxSession, FetchGroup.NO_COUNT).isModSeqPermanent()) {
                response = new FetchResponse(msn, flags, uidOut, mr.getModSeq(), null, null, null, null, null, null);
            } else {
                response = new FetchResponse(msn, flags, uidOut, null, null, null, null, null, null, null);
            }
            responder.respond(response);
        }
    }

    protected void condstoreEnablingCommand(ImapSession session, Responder responder, MetaData metaData, boolean sendHighestModSeq) {
        Set<String> enabled = EnableProcessor.getEnabledCapabilities(session);
        if (!enabled.contains(ImapConstants.SUPPORTS_CONDSTORE)) {
            if (sendHighestModSeq) {
                if (metaData.isModSeqPermanent()) {

                    final long highestModSeq = metaData.getHighestModSeq();

                    StatusResponse untaggedOk = getStatusResponseFactory().untaggedOk(HumanReadableText.HIGHEST_MOD_SEQ, ResponseCode.highestModSeq(highestModSeq));
                    responder.respond(untaggedOk);        
                }
            }
            enabled.add(ImapConstants.SUPPORTS_CONDSTORE);


        }
    }
    
    private MessageManager getMailbox(final ImapSession session, final SelectedMailbox selected) throws MailboxException {
        final MailboxManager mailboxManager = getMailboxManager();
        final MessageManager mailbox = mailboxManager.getMailbox(selected.getPath(), ImapSessionUtils.getMailboxSession(session));
        return mailbox;
    }

    private void addRecentResponses(final SelectedMailbox selected, final ImapProcessor.Responder responder) {
        final int recentCount = selected.recentCount();
        RecentResponse response = new RecentResponse(recentCount);
        responder.respond(response);
    }

    private void addExistsResponses(final ImapSession session, final SelectedMailbox selected, final ImapProcessor.Responder responder) {
        final long existsCount = selected.existsCount();
        final ExistsResponse response = new ExistsResponse(existsCount);
        responder.respond(response);
    }

    private void handleResponseException(final ImapProcessor.Responder responder, MailboxException e, final HumanReadableText message, ImapSession session) {
        session.getLog().info(message.toString());
        session.getLog().debug(message.toString(), e);
        // TODO: consider whether error message should be passed to the user
        final StatusResponse response = factory.untaggedNo(message);
        responder.respond(response);
    }

    protected void okComplete(final ImapCommand command, final String tag, final ImapProcessor.Responder responder) {
        final StatusResponse response = factory.taggedOk(tag, command, HumanReadableText.COMPLETED);
        responder.respond(response);
    }

    protected void okComplete(final ImapCommand command, final String tag, final ResponseCode code, final ImapProcessor.Responder responder) {
        final StatusResponse response = factory.taggedOk(tag, command, HumanReadableText.COMPLETED, code);
        responder.respond(response);
    }

    protected void no(final ImapCommand command, final String tag, final ImapProcessor.Responder responder, final HumanReadableText displayTextKey) {
        final StatusResponse response = factory.taggedNo(tag, command, displayTextKey);
        responder.respond(response);
    }

    protected void no(final ImapCommand command, final String tag, final ImapProcessor.Responder responder, final HumanReadableText displayTextKey, final StatusResponse.ResponseCode responseCode) {
        final StatusResponse response = factory.taggedNo(tag, command, displayTextKey, responseCode);
        responder.respond(response);
    }

    protected void taggedBad(final ImapCommand command, final String tag, final ImapProcessor.Responder responder, final HumanReadableText e) {
        StatusResponse response = factory.taggedBad(tag, command, e);

        responder.respond(response);
    }

    protected void bye(final ImapProcessor.Responder responder) {
        final StatusResponse response = factory.bye(HumanReadableText.BYE);
        responder.respond(response);
    }

    protected void bye(final ImapProcessor.Responder responder, final HumanReadableText key) {
        final StatusResponse response = factory.bye(key);
        responder.respond(response);
    }

    protected abstract void doProcess(final M message, ImapSession session, String tag, ImapCommand command, Responder responder);

    public MailboxPath buildFullPath(final ImapSession session, String mailboxName) {
        String namespace = null;
        String name = null;
        final MailboxSession mailboxSession = ImapSessionUtils.getMailboxSession(session);

        if (mailboxName == null || mailboxName.length() == 0) {
            return new MailboxPath("", "", "");
        }
        if (mailboxName.charAt(0) == MailboxConstants.NAMESPACE_PREFIX_CHAR) {
            int namespaceLength = mailboxName.indexOf(mailboxSession.getPathDelimiter());
            if (namespaceLength > -1) {
                namespace = mailboxName.substring(0, namespaceLength);
                if (mailboxName.length() > namespaceLength)
                    name = mailboxName.substring(++namespaceLength);
            } else {
                namespace = mailboxName;
            }
        } else {
            namespace = MailboxConstants.USER_NAMESPACE;
            name = mailboxName;
        }
        String user = null;
        // we only use the user as part of the MailboxPath if its a private user
        // namespace
        if (namespace.equals(MailboxConstants.USER_NAMESPACE)) {
            user = ImapSessionUtils.getUserName(session);
        }
        
        // use uppercase for INBOX
        //
        // See IMAP-349
        if (name.equalsIgnoreCase(MailboxConstants.INBOX)) {
            name = MailboxConstants.INBOX;
        }

        return new MailboxPath(namespace, user, name);
    }

    /**
     * Joins the elements of a mailboxPath together and returns them as a string
     * 
     * @param mailboxPath
     * @return
     */
    private String joinMailboxPath(MailboxPath mailboxPath, char delimiter) {
        StringBuffer sb = new StringBuffer("");
        if (mailboxPath.getNamespace() != null && !mailboxPath.getNamespace().equals("")) {
            sb.append(mailboxPath.getNamespace());
        }
        if (mailboxPath.getUser() != null && !mailboxPath.getUser().equals("")) {
            if (sb.length() > 0)
                sb.append(delimiter);
            sb.append(mailboxPath.getUser());
        }
        if (mailboxPath.getName() != null && !mailboxPath.getName().equals("")) {
            if (sb.length() > 0)
                sb.append(delimiter);
            sb.append(mailboxPath.getName());
        }
        return sb.toString();
    }

    protected String mailboxName(final boolean relative, final MailboxPath path, final char delimiter) {
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

    protected MessageManager getSelectedMailbox(final ImapSession session) throws MailboxException {
        MessageManager result;
        final SelectedMailbox selectedMailbox = session.getSelected();
        if (selectedMailbox == null) {
            result = null;
        } else {
            final MailboxManager mailboxManager = getMailboxManager();
            result = mailboxManager.getMailbox(selectedMailbox.getPath(), ImapSessionUtils.getMailboxSession(session));
        }
        return result;
    }

    /**
     * Return a {@link MessageRange} for the given values. If the MessageRange
     * can not be generated a {@link MailboxException} will get thrown
     * 
     * @param selected
     * @param range
     * @param useUids
     * @return range or null
     * @throws MailboxException
     */
    protected MessageRange messageRange(SelectedMailbox selected, IdRange range, boolean useUids) throws MessageRangeException {
        long lowVal = range.getLowVal();
        long highVal = range.getHighVal();

        if (useUids == false) {
            // Take care of "*" and "*:*" values by return the last message in
            // the mailbox. See IMAP-289
            if (lowVal == Long.MAX_VALUE && highVal == Long.MAX_VALUE) {
                highVal = selected.getLastUid();
                if (highVal == SelectedMailbox.NO_SUCH_MESSAGE) {
                    throw new MessageRangeException("Mailbox is empty");
                }
                return MessageRange.one(highVal);
            }

            if (lowVal != Long.MIN_VALUE) {
                lowVal = selected.uid((int) lowVal);
                if (lowVal == SelectedMailbox.NO_SUCH_MESSAGE)
                    throw new MessageRangeException("No message found with msn " + lowVal);
            } else {
                lowVal = selected.getFirstUid();
                if (lowVal == SelectedMailbox.NO_SUCH_MESSAGE) {
                    throw new MessageRangeException("Mailbox is empty");
                }
            }
            if (highVal != Long.MAX_VALUE) {
                highVal = selected.uid((int) highVal);
                if (highVal == SelectedMailbox.NO_SUCH_MESSAGE)
                    throw new MessageRangeException("No message found with msn " + highVal);
            } else {
                highVal = selected.getLastUid();
                if (highVal == SelectedMailbox.NO_SUCH_MESSAGE) {
                    throw new MessageRangeException("Mailbox is empty");
                }
            }
            
        } else {
            if (selected.existsCount() <= 0) {
                return null;
            }
            // Take care of "*" and "*:*" values by return the last message in
            // the mailbox. See IMAP-289
            if (lowVal == Long.MAX_VALUE && highVal == Long.MAX_VALUE) {
                return MessageRange.one(selected.getLastUid());
            } else if (highVal == Long.MAX_VALUE && selected.getLastUid() < lowVal) {
                // Sequence uid ranges which use *:<uid-higher-then-last-uid>
                // MUST return at least the highest uid in the mailbox
                // See IMAP-291
                return MessageRange.one(selected.getLastUid());
            } 
        }
        MessageRange mRange = MessageRange.range(lowVal, highVal);
        return mRange;
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
     * @throws MessageRangeException
     */
    protected MessageRange normalizeMessageRange(SelectedMailbox selected, MessageRange range) throws MessageRangeException {
        Type rangeType = range.getType();
        long start;
        long end;

        switch (rangeType) {
        case ONE:
            return range;
        case ALL:
            start = selected.getFirstUid();
            end = selected.getLastUid();
            return MessageRange.range(start, end);
        case RANGE:
            start = range.getUidFrom();
            if (start < 1 || start == Long.MAX_VALUE || start < selected.getFirstUid()) {
                start = selected.getFirstUid();
            }
            end = range.getUidTo();
            if (end < 1 || end == Long.MAX_VALUE || end > selected.getLastUid()) {
                end = selected.getLastUid();
            }
            return MessageRange.range(start, end);
        case FROM:
            start = range.getUidFrom();
            if (start < 1 || start == Long.MAX_VALUE || start < selected.getFirstUid()) {
                start = selected.getFirstUid();
            }

            end = selected.getLastUid();
            return MessageRange.range(start, end);
        default:
            throw new MessageRangeException("Unknown message range type: " + rangeType);
        }
    }
    
    
    /**
     * Send VANISHED responses if needed. 
     * 
     * @param session
     * @param mailbox
     * @param ranges
     * @param changedSince
     * @param metaData
     * @param responder
     * @throws MailboxException
     */
    protected void respondVanished(MailboxSession session, MessageManager mailbox, List<MessageRange> ranges, long changedSince, MetaData metaData, Responder responder) throws MailboxException {
        // RFC5162 4.2. Server Implementations Storing Minimal State
        //  
        //      A server that stores the HIGHESTMODSEQ value at the time of the last
        //      EXPUNGE can omit the VANISHED response when a client provides a
        //      MODSEQ value that is equal to, or higher than, the current value of
        //      this datum, that is, when there have been no EXPUNGEs.
        //
        //      A client providing message sequence match data can reduce the scope
        //      as above.  In the case where there have been no expunges, the server
        //      can ignore this data.
        if (metaData.getHighestModSeq() > changedSince) {
            SearchQuery searchQuery = new SearchQuery();
            NumericRange[] nRanges = new NumericRange[ranges.size()];
            Set<Long> vanishedUids = new HashSet<Long>();
            for (int i = 0; i < ranges.size(); i++) {
                MessageRange r = ranges.get(i);
                NumericRange nr;
                if (r.getType() == Type.ONE) {
                    nr = new NumericRange(r.getUidFrom());
                } else {
                    nr = new NumericRange(r.getUidFrom(), r.getUidTo());
                }
                long from = nr.getLowValue();
                long to = nr.getHighValue();
                while(from <= to) {
                    vanishedUids.add(from++);
                }
                nRanges[i] = nr;
                
            }
            searchQuery.andCriteria(SearchQuery.uid(nRanges));
            searchQuery.andCriteria(SearchQuery.modSeqGreaterThan(changedSince));
            Iterator<Long> uids = mailbox.search(searchQuery, session);
            while(uids.hasNext()) {
                vanishedUids.remove(uids.next());
            }
            IdRange[] vanishedIdRanges = idRanges(MessageRange.toRanges(vanishedUids));
            responder.respond(new VanishedResponse(vanishedIdRanges, true));
        }
        
        
    }
    
    
    // TODO: Do we need to handle wildcards here ?
    protected IdRange[] idRanges(Collection<MessageRange> mRanges) {
        IdRange[] idRanges = new IdRange[mRanges.size()];
        Iterator<MessageRange> mIt = mRanges.iterator();
        int i = 0;
        while(mIt.hasNext()) {
            MessageRange mr = mIt.next();
            IdRange ir;
            if (mr.getType() == Type.ONE) {
                ir = new IdRange(mr.getUidFrom());
            } else {
                ir = new IdRange(mr.getUidFrom(), mr.getUidTo());
            }
            idRanges[i++] = ir;
        }
        return idRanges;
    }

}
