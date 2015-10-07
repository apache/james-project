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
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import javax.mail.Flags.Flag;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.request.DayMonthYear;
import org.apache.james.imap.api.message.request.SearchKey;
import org.apache.james.imap.api.message.request.SearchOperation;
import org.apache.james.imap.api.message.request.SearchResultOption;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SearchResUtil;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.message.request.SearchRequest;
import org.apache.james.imap.message.response.ESearchResponse;
import org.apache.james.imap.message.response.SearchResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.MetaData;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MessageRangeException;
import org.apache.james.mailbox.model.FetchGroupImpl;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResultIterator;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.AddressType;
import org.apache.james.mailbox.model.SearchQuery.Criterion;
import org.apache.james.mailbox.model.SearchQuery.DateResolution;

public class SearchProcessor extends AbstractMailboxProcessor<SearchRequest> implements CapabilityImplementingProcessor {

    protected final static String SEARCH_MODSEQ = "SEARCH_MODSEQ";
    private final static List<String> CAPS = Collections.unmodifiableList(Arrays.asList("WITHIN", "ESEARCH", "SEARCHRES"));
    
    public SearchProcessor(final ImapProcessor next, final MailboxManager mailboxManager, final StatusResponseFactory factory) {
        super(SearchRequest.class, next, mailboxManager, factory);
    }

    /**
     * @see
     * org.apache.james.imap.processor.AbstractMailboxProcessor
     * #doProcess(org.apache.james.imap.api.message.request.ImapRequest,
     * org.apache.james.imap.api.process.ImapSession, java.lang.String,
     * org.apache.james.imap.api.ImapCommand,
     * org.apache.james.imap.api.process.ImapProcessor.Responder)
     */
    protected void doProcess(SearchRequest request, ImapSession session, String tag, ImapCommand command, Responder responder) {
        final SearchOperation operation = request.getSearchOperation();
        final SearchKey searchKey = operation.getSearchKey();
        final boolean useUids = request.isUseUids();
        List<SearchResultOption> resultOptions = operation.getResultOptions();

        try {

            final MessageManager mailbox = getSelectedMailbox(session);

            final SearchQuery query = toQuery(searchKey, session);
            MailboxSession msession = ImapSessionUtils.getMailboxSession(session);
            final Iterator<Long> it = mailbox.search(query, msession);
            
            final Collection<Long> results = new TreeSet<Long>();
            final Collection<Long> uids = new TreeSet<Long>();
            
            while (it.hasNext()) {
                final long uid = it.next();
                final Long number;
                if (useUids) {
                    number = uid;
                } else {
                    final int msn = session.getSelected().msn(uid);
                    number = (long) msn;
                }
                if (number == SelectedMailbox.NO_SUCH_MESSAGE == false)
                    results.add(number);
                
                uids.add(uid);
            }
            
            // Check if the search did contain the MODSEQ searchkey. If so we need to include the highest mod in the response.
            //
            // See RFC4551: 3.4. MODSEQ Search Criterion in SEARCH
            final Long highestModSeq;
            if (session.getAttribute(SEARCH_MODSEQ) != null) {
                MetaData metaData = mailbox.getMetaData(false, msession , MessageManager.MetaData.FetchGroup.NO_COUNT);
                highestModSeq = findHighestModSeq(msession, mailbox, MessageRange.toRanges(uids), metaData.getHighestModSeq());
                
                // Enable CONDSTORE as this is a CONDSTORE enabling command
                condstoreEnablingCommand(session, responder,  metaData, true);                
                
            } else {
                highestModSeq = null;
            }
            final long[] ids = toArray(results);

            final ImapResponseMessage response;
            if (resultOptions == null || resultOptions.isEmpty()) {
                response = new SearchResponse(ids, highestModSeq);
            } else {
                IdRange[] idRanges;
                List<Long> idList = new ArrayList<Long>(ids.length);
                for ( int i = 0; i < ids.length; i++) {
                    idList.add(ids[i]);
                }
                List<MessageRange> ranges = MessageRange.toRanges(idList);
                idRanges = new IdRange[ranges.size()];
                for (int i = 0 ; i <ranges.size(); i++) {
                    MessageRange range = ranges.get(i);
                    idRanges[i] = new IdRange(range.getUidFrom(), range.getUidTo());
                }
                
                boolean esearch = false;
                for (int i = 0; i < resultOptions.size(); i++) {
                    if (SearchResultOption.SAVE != resultOptions.get(i)) {
                        esearch = true;
                        break;
                    }
                }
                
                if (esearch) {
                    long min = -1;
                    long max = -1;
                    long count = ids.length;

                    if (ids.length > 0) {
                        min = ids[0];
                        max = ids[ids.length -1];
                    } 
                   
                    
                    // Save the sequence-set for later usage. This is part of SEARCHRES 
                    if (resultOptions.contains(SearchResultOption.SAVE)) {
                        if (resultOptions.contains(SearchResultOption.ALL) || resultOptions.contains(SearchResultOption.COUNT)) {
                            // if the options contain ALL or COUNT we need to save the complete sequence-set
                            SearchResUtil.saveSequenceSet(session, idRanges);
                        } else {
                            List<IdRange> savedRanges = new ArrayList<IdRange>();
                            if (resultOptions.contains(SearchResultOption.MIN)) {
                                // Store the MIN
                                savedRanges.add(new IdRange(min));  
                            } 
                            if (resultOptions.contains(SearchResultOption.MAX)) {
                                // Store the MAX
                                savedRanges.add(new IdRange(max));
                            }
                            SearchResUtil.saveSequenceSet(session, savedRanges.toArray(new IdRange[0]));
                        }
                    }
                    response = new ESearchResponse(min, max, count, idRanges, highestModSeq, tag, useUids, resultOptions);
                } else {
                    // Just save the returned sequence-set as this is not SEARCHRES + ESEARCH
                    SearchResUtil.saveSequenceSet(session, idRanges);
                    response = new SearchResponse(ids, highestModSeq);

                }
            }

            responder.respond(response);

            boolean omitExpunged = (!useUids);
            unsolicitedResponses(session, responder, omitExpunged, useUids);
            okComplete(command, tag, responder);
        } catch (MessageRangeException e) {
            if (session.getLog().isDebugEnabled()) {
                session.getLog().debug("Search failed in mailbox " + session.getSelected().getPath() + " because of an invalid sequence-set ", e);
            }
            taggedBad(command, tag, responder, HumanReadableText.INVALID_MESSAGESET);
        } catch (MailboxException e) {
            if (session.getLog().isInfoEnabled()) {
                session.getLog().info("Search failed in mailbox " + session.getSelected().getPath(), e);
            }
            no(command, tag, responder, HumanReadableText.SEARCH_FAILED);
            
            if (resultOptions.contains(SearchResultOption.SAVE)) {
                // Reset the saved sequence-set on a BAD response if the SAVE option was used.
                //
                // See RFC5182 2.1.Normative Description of the SEARCHRES Extension
                SearchResUtil.resetSavedSequenceSet(session);
            }
        } finally {
            session.setAttribute(SEARCH_MODSEQ, null);
        }
    }
    
    private long[] toArray(final Collection<Long> results) {
        final Iterator<Long> it = results.iterator();
        final int length = results.size();
        long[] ids = new long[length];
        for (int i = 0; i < length; i++) {
            ids[i] = ((Long) it.next()).longValue();
        }
        return ids;
    }

    /**
     * Find the highest mod-sequence number in the given {@link MessageRange}'s.
     * 
     * @param session
     * @param mailbox
     * @param ranges
     * @param currentHighest
     * @return highestModSeq
     * @throws MailboxException
     */
    private Long findHighestModSeq(final MailboxSession session, MessageManager mailbox, List<MessageRange> ranges, final long currentHighest) throws MailboxException {
        Long highestModSeq = null;
        
        // Reverse loop over the ranges as its more likely that we find a match at the end
        int size = ranges.size();
        for (int i = size -1 ; i > 0; i--) {
            MessageResultIterator results = mailbox.getMessages(ranges.get(i), FetchGroupImpl.MINIMAL, session);
            while(results.hasNext()) {
                long modSeq = results.next().getModSeq();
                if (highestModSeq == null || modSeq > highestModSeq) {
                    highestModSeq = modSeq;
                }
                if (highestModSeq == currentHighest) {
                    return highestModSeq;
                }
            }
            
        }
        return highestModSeq;
    }


    private SearchQuery toQuery(final SearchKey key, final ImapSession session) throws MessageRangeException {
        final SearchQuery result = new SearchQuery();
        final SelectedMailbox selected = session.getSelected();
        if (selected != null) {
            result.addRecentMessageUids(selected.getRecent());
        }
        final SearchQuery.Criterion criterion = toCriterion(key, session);
        result.andCriteria(criterion);
        return result;
    }

    private SearchQuery.Criterion toCriterion(final SearchKey key, final ImapSession session) throws MessageRangeException {
        final int type = key.getType();
        final DayMonthYear date = key.getDate();
        switch (type) {
        case SearchKey.TYPE_ALL:
            return SearchQuery.all();
        case SearchKey.TYPE_AND:
            return and(key.getKeys(), session);
        case SearchKey.TYPE_ANSWERED:
            return SearchQuery.flagIsSet(Flag.ANSWERED);
        case SearchKey.TYPE_BCC:
            return SearchQuery.address(AddressType.Bcc, key.getValue());
        case SearchKey.TYPE_BEFORE:
            return SearchQuery.internalDateBefore(date.toDate(), DateResolution.Day);
        case SearchKey.TYPE_BODY:
            return SearchQuery.bodyContains(key.getValue());
        case SearchKey.TYPE_CC:
            return SearchQuery.address(AddressType.Cc, key.getValue());
        case SearchKey.TYPE_DELETED:
            return SearchQuery.flagIsSet(Flag.DELETED);
        case SearchKey.TYPE_DRAFT:
            return SearchQuery.flagIsSet(Flag.DRAFT);
        case SearchKey.TYPE_FLAGGED:
            return SearchQuery.flagIsSet(Flag.FLAGGED);
        case SearchKey.TYPE_FROM:
            return SearchQuery.address(AddressType.From, key.getValue());
        case SearchKey.TYPE_HEADER:
            String value = key.getValue();
            // Check if header exists if the value is empty. See IMAP-311
            if (value == null || value.length() == 0) {
                return SearchQuery.headerExists(key.getName());
            } else {
                return SearchQuery.headerContains(key.getName(), value);
            }
        case SearchKey.TYPE_KEYWORD:
            return SearchQuery.flagIsSet(key.getValue());
        case SearchKey.TYPE_LARGER:
            return SearchQuery.sizeGreaterThan(key.getSize());
        case SearchKey.TYPE_NEW:
            return SearchQuery.and(SearchQuery.flagIsSet(Flag.RECENT), SearchQuery.flagIsUnSet(Flag.SEEN));
        case SearchKey.TYPE_NOT:
            return not(key.getKeys(), session);
        case SearchKey.TYPE_OLD:
            return SearchQuery.flagIsUnSet(Flag.RECENT);
        case SearchKey.TYPE_ON:
            return SearchQuery.internalDateOn(date.toDate(), DateResolution.Day);
        case SearchKey.TYPE_OR:
            return or(key.getKeys(), session);
        case SearchKey.TYPE_RECENT:
            return SearchQuery.flagIsSet(Flag.RECENT);
        case SearchKey.TYPE_SEEN:
            return SearchQuery.flagIsSet(Flag.SEEN);
        case SearchKey.TYPE_SENTBEFORE:
            return SearchQuery.headerDateBefore(ImapConstants.RFC822_DATE, date.toDate(), DateResolution.Day);
        case SearchKey.TYPE_SENTON:
            return SearchQuery.headerDateOn(ImapConstants.RFC822_DATE, date.toDate(), DateResolution.Day);
        case SearchKey.TYPE_SENTSINCE:
            // Include the date which is used as search param. See IMAP-293
            Criterion onCrit = SearchQuery.headerDateOn(ImapConstants.RFC822_DATE, date.toDate(), DateResolution.Day);
            Criterion afterCrit = SearchQuery.headerDateAfter(ImapConstants.RFC822_DATE, date.toDate(), DateResolution.Day);
            return SearchQuery.or(onCrit, afterCrit);
        case SearchKey.TYPE_SEQUENCE_SET:
            return sequence(key.getSequenceNumbers(), session, true);
        case SearchKey.TYPE_SINCE:
            // Include the date which is used as search param. See IMAP-293
            return SearchQuery.or(SearchQuery.internalDateOn(date.toDate(), DateResolution.Day), SearchQuery.internalDateAfter(date.toDate(), DateResolution.Day));
        case SearchKey.TYPE_SMALLER:
            return SearchQuery.sizeLessThan(key.getSize());
        case SearchKey.TYPE_SUBJECT:
            return SearchQuery.headerContains(ImapConstants.RFC822_SUBJECT, key.getValue());
        case SearchKey.TYPE_TEXT:
            return SearchQuery.mailContains(key.getValue());
        case SearchKey.TYPE_TO:
            return SearchQuery.address(AddressType.To, key.getValue());
        case SearchKey.TYPE_UID:
            return sequence(key.getSequenceNumbers(), session, false);
        case SearchKey.TYPE_UNANSWERED:
            return SearchQuery.flagIsUnSet(Flag.ANSWERED);
        case SearchKey.TYPE_UNDELETED:
            return SearchQuery.flagIsUnSet(Flag.DELETED);
        case SearchKey.TYPE_UNDRAFT:
            return SearchQuery.flagIsUnSet(Flag.DRAFT);
        case SearchKey.TYPE_UNFLAGGED:
            return SearchQuery.flagIsUnSet(Flag.FLAGGED);
        case SearchKey.TYPE_UNKEYWORD:
            return SearchQuery.flagIsUnSet(key.getValue());
        case SearchKey.TYPE_UNSEEN:
            return SearchQuery.flagIsUnSet(Flag.SEEN);
        case SearchKey.TYPE_OLDER:
            Date withinDate = createWithinDate(key);
            return SearchQuery.or(SearchQuery.internalDateOn(withinDate, DateResolution.Second), SearchQuery.internalDateBefore(withinDate, DateResolution.Second));
        case SearchKey.TYPE_YOUNGER:
            Date withinDate2 = createWithinDate(key);
            return SearchQuery.or(SearchQuery.internalDateOn(withinDate2, DateResolution.Second), SearchQuery.internalDateAfter(withinDate2, DateResolution.Second));
        case SearchKey.TYPE_MODSEQ: 
            session.setAttribute(SEARCH_MODSEQ, true);
            long modSeq = key.getModSeq();
            return SearchQuery.or(SearchQuery.modSeqEquals(modSeq), SearchQuery.modSeqGreaterThan(modSeq));
        default:
            session.getLog().warn("Ignoring unknown search key.");
            return SearchQuery.all();
        }
    }
    private Date createWithinDate(SearchKey key) {
        long seconds = key.getSeconds();
        long res = System.currentTimeMillis() - seconds * 1000;
        return new Date(res);
    }

    /**
     * Create a {@link Criterion} for the given sequence-sets. This include special handling which is needed for SEARCH to not return a BAD response on a invalid message-set. 
     * See IMAP-292 for more details.
     * 
     * 
     * @param sequenceNumbers
     * @param session
     * @param msn
     * @return crit
     * @throws MessageRangeException
     */
    private Criterion sequence(IdRange[] sequenceNumbers, final ImapSession session, boolean msn) throws MessageRangeException {
        final int length = sequenceNumbers.length;
        final List<SearchQuery.NumericRange> ranges = new ArrayList<SearchQuery.NumericRange>();
        final SelectedMailbox selected = session.getSelected();
        boolean useUids = !msn;

        // First of check if we have any messages in the mailbox
        // if not we don't need to go through all of this
        if (selected.existsCount() > 0) {
            for (int i = 0; i < length; i++) {
                final IdRange range = sequenceNumbers[i];

                long lowVal = range.getLowVal();
                long highVal = range.getHighVal();
                if (useUids) {
                    // Take care of "*" and "*:*" values by return the last
                    // message in
                    // the mailbox. See IMAP-289
                    if (lowVal == Long.MAX_VALUE && highVal == Long.MAX_VALUE) {
                        ranges.add(new SearchQuery.NumericRange(selected.getLastUid()));
                    } else if (highVal == Long.MAX_VALUE && selected.getLastUid() < lowVal) {
                        // Sequence uid ranges which use
                        // *:<uid-higher-then-last-uid>
                        // MUST return at least the highest uid in the mailbox
                        // See IMAP-291
                        ranges.add(new SearchQuery.NumericRange(selected.getLastUid()));
                    } else {
                        ranges.add(new SearchQuery.NumericRange(lowVal, highVal));
                    }
                } else {
                    // Take care of "*" and "*:*" values by return the last
                    // message in
                    // the mailbox. See IMAP-289
                    if (lowVal == Long.MAX_VALUE && highVal == Long.MAX_VALUE) {
                        highVal = selected.getLastUid();

                        ranges.add(new SearchQuery.NumericRange(highVal));
                    } else {
                        if (lowVal != Long.MIN_VALUE) {
                            lowVal = selected.uid((int) lowVal);
                        } else {
                            lowVal = selected.getFirstUid();
                        }

                        // The lowVal should never be
                        // SelectedMailbox.NO_SUCH_MESSAGE but we check for it
                        // just to be safe
                        if (lowVal != SelectedMailbox.NO_SUCH_MESSAGE) {
                            if (highVal != Long.MAX_VALUE) {
                                highVal = selected.uid((int) highVal);
                                if (highVal == SelectedMailbox.NO_SUCH_MESSAGE) {
                                    // we requested a message with a MSN higher
                                    // then
                                    // the current msg count. So just use the
                                    // highest uid as max
                                    highVal = selected.getLastUid();
                                }
                            } else {
                                highVal = selected.getLastUid();
                            }
                            ranges.add(new SearchQuery.NumericRange(lowVal, highVal));
                        }
                    }
                }
            }
        }

        Criterion crit = SearchQuery.uid(ranges.toArray(new SearchQuery.NumericRange[0]));
        return crit;
    }

    private Criterion or(List<SearchKey> keys, final ImapSession session) throws MessageRangeException {
        final SearchKey keyOne = keys.get(0);
        final SearchKey keyTwo = keys.get(1);
        final Criterion criterionOne = toCriterion(keyOne, session);
        final Criterion criterionTwo = toCriterion(keyTwo, session);
        final Criterion result = SearchQuery.or(criterionOne, criterionTwo);
        return result;
    }

    private Criterion not(List<SearchKey> keys, final ImapSession session) throws MessageRangeException {
        final SearchKey key = keys.get(0);
        final Criterion criterion = toCriterion(key, session);
        final Criterion result = SearchQuery.not(criterion);
        return result;
    }

    private Criterion and(List<SearchKey> keys, final ImapSession session) throws MessageRangeException {
        final int size = keys.size();
        final List<Criterion> criteria = new ArrayList<Criterion>(size);
        for (final SearchKey key : keys) {
            final Criterion criterion = toCriterion(key, session);
            criteria.add(criterion);
        }
        final Criterion result = SearchQuery.and(criteria);
        return result;
    }

    /**
     * @see org.apache.james.imap.processor.CapabilityImplementingProcessor
     * #getImplementedCapabilities(org.apache.james.imap.api.process.ImapSession)
     */
    public List<String> getImplementedCapabilities(ImapSession session) {
        return CAPS;
    }
}
