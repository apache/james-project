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
package org.apache.james.imap.decode.parser;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.UidRange;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.ImapRequestLineReader.StringMatcherCharacterValidator;
import org.apache.james.imap.decode.base.AbstractImapCommandParser;
import org.apache.james.imap.message.request.AbstractMailboxSelectionRequest;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.UidValidity;

public abstract class AbstractSelectionCommandParser extends AbstractImapCommandParser {
    private static final String CONDSTORE = ImapConstants.SUPPORTS_CONDSTORE.asString();
    private static final String QRESYNC = ImapConstants.SUPPORTS_QRESYNC.asString();

    public AbstractSelectionCommandParser(ImapCommand command, StatusResponseFactory statusResponseFactory) {
        super(command, statusResponseFactory);
    }
    
    @Override
    protected ImapMessage decode(ImapRequestLineReader request, Tag tag, ImapSession session) throws DecodingException {
        final String mailboxName = request.mailbox();
        boolean condstore = false;
        UidValidity lastKnownUidValidity = null;
        Long knownModSeq = null;
        UidRange[] uidSet = null;
        UidRange[] knownUidSet = null;
        IdRange[] knownSequenceSet = null;
        
        char c = Character.UNASSIGNED;
        try {
            c = request.nextWordChar();
        } catch (DecodingException e) {
            // This is expected if the request has no options like CONDSTORE and QRESYNC
        }
        
        // Ok an option was found
        if (c == '(') {
            request.consume();
            
            int n = ImapRequestLineReader.cap(request.nextChar());
            switch (n) {
            case 'C':
                // It starts with C so it should be CONDSTORE
                request.consumeWord(StringMatcherCharacterValidator.ignoreCase(CONDSTORE));
                condstore = true;
                break;
            case 'Q':
                // It starts with Q so it should be QRESYNC
                request.consumeWord(StringMatcherCharacterValidator.ignoreCase(QRESYNC));
                
                // Consume the SP
                request.consumeChar(' ');
                
                // Consume enclosing paren
                request.consumeChar('(');
                long uidValidityAsNumber = request.number();
                lastKnownUidValidity = sanitizeUidValidity(uidValidityAsNumber);

                // Consume the SP
                request.consumeChar(' ');
                knownModSeq = request.number(true);
                
                char nc = request.nextChar();
                if (nc == ' ') {
                    // All this stuff is now optional
                       
                    // Consume the SP
                    request.consumeChar(' ');
                    uidSet = request.parseUidRange();
                    
                    // Check for *
                    checkUidRanges(uidSet, false);
                    
                    nc = request.nextChar();
                    if (nc == ' ')  {
                        request.consumeChar(' ');
                        
                        // This is enclosed in () so remove (
                        request.consumeChar('(');
                        knownSequenceSet = request.parseIdRange();
                        request.consumeChar(' ');
                        knownUidSet = request.parseUidRange();
                       
                        // Check for * and check if its in ascending order
                        checkIdRanges(knownSequenceSet, true);
                        checkUidRanges(knownUidSet, true);
                        
                        // This is enclosed in () so remove )
                        request.consumeChar(')');
                    }
                }
                request.consumeChar(')');

                break;
            default:
                throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown option");
            }

            request.consumeChar(')');

        }

        request.eol();
        return createRequest(mailboxName, condstore, lastKnownUidValidity, knownModSeq, uidSet, knownUidSet, knownSequenceSet, tag);
    }

    private UidValidity sanitizeUidValidity(long uidValidityAsNumber) {
        if (UidValidity.isValid(uidValidityAsNumber)) {
            return UidValidity.ofValid(uidValidityAsNumber);
        } else {
            // The UidValidity cached by the client is invalid
            // We know that the backend will regenerate it
            // Hence we force the mismatch
            // QRSYNC command will be ignored
            return UidValidity.random();
        }
    }

    /**
     * Check if the {@link IdRange}'s are formatted like stated in the QRESYNC RFC.
     * 
     * From RFC5162:
     * 
     *  known-uids             =  sequence-set
     *                          ;; sequence of UIDs, "*" is not allowed
     *
     *  known-sequence-set     =  sequence-set
     *                          ;; set of message numbers corresponding to
     *                          ;; the UIDs in known-uid-set, in ascending order.
     *                          ;; * is not allowed.
     *                          
     *  known-uid-set       =  sequence-set
     *                          ;; set of UIDs corresponding to the messages in
     *                          ;; known-sequence-set, in ascending order.
     *                          ;; * is not allowed.
     */
    private void checkIdRanges(IdRange[] ranges, boolean checkOrder) throws DecodingException {
        long last = 0;
        for (IdRange r : ranges) {

            long low = r.getLowVal();
            long high = r.getHighVal();
            if (low == Long.MAX_VALUE || high == Long.MAX_VALUE) {
                throw new DecodingException(HumanReadableText.INVALID_MESSAGESET, "* is not allowed in the sequence-set");
            }
            if (checkOrder) {
                if (low < last) {
                    throw new DecodingException(HumanReadableText.INVALID_MESSAGESET, "Sequence-set must be in ascending order");
                } else {
                    last = high;
                }
            }
        }
    }
    
    /**
     * Check if the {@link IdRange}'s are formatted like stated in the QRESYNC RFC.
     * 
     * From RFC5162:
     * 
     *  known-uids             =  sequence-set
     *                          ;; sequence of UIDs, "*" is not allowed
     *
     *  known-sequence-set     =  sequence-set
     *                          ;; set of message numbers corresponding to
     *                          ;; the UIDs in known-uid-set, in ascending order.
     *                          ;; * is not allowed.
     *                          
     *  known-uid-set       =  sequence-set
     *                          ;; set of UIDs corresponding to the messages in
     *                          ;; known-sequence-set, in ascending order.
     *                          ;; * is not allowed.
     */
    private void checkUidRanges(UidRange[] ranges, boolean checkOrder) throws DecodingException {
        MessageUid last = MessageUid.MIN_VALUE;
        for (UidRange r : ranges) {

            MessageUid low = r.getLowVal();
            MessageUid high = r.getHighVal();
            if (low.equals(MessageUid.MAX_VALUE) || high.equals(MessageUid.MAX_VALUE)) {
                throw new DecodingException(HumanReadableText.INVALID_MESSAGESET, "* is not allowed in the sequence-set");
            }
            if (checkOrder) {
                if (low.compareTo(last) < 0) {
                    throw new DecodingException(HumanReadableText.INVALID_MESSAGESET, "Sequence-set must be in ascending order");
                } else {
                    last = high;
                }
            }
        }
    }
    
    /**
     * Create a new {@link AbstractMailboxSelectionRequest} for the given arguments
     */
    protected abstract AbstractMailboxSelectionRequest createRequest(String mailboxName, boolean condstore, UidValidity lastKnownUidValidity, Long knownModSeq, UidRange[] uidSet, UidRange[] knownUidSet, IdRange[] knownSequenceSet, Tag tag);
}
