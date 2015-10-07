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
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.ImapRequestLineReader.CharacterValidator;
import org.apache.james.imap.decode.base.AbstractImapCommandParser;
import org.apache.james.imap.message.request.AbstractMailboxSelectionRequest;
import org.apache.james.protocols.imap.DecodingException;

public abstract class AbstractSelectionCommandParser extends AbstractImapCommandParser{
    private final static byte[] CONDSTORE = ImapConstants.SUPPORTS_CONDSTORE.getBytes();
    private final static byte[] QRESYNC = ImapConstants.SUPPORTS_QRESYNC.getBytes();

    public AbstractSelectionCommandParser(ImapCommand command) {
        super(command);
    }
    


    
    /**
     * @see
     * org.apache.james.imap.decode.base.AbstractImapCommandParser#decode(org.apache.james.imap.api.ImapCommand,
     * org.apache.james.imap.decode.ImapRequestLineReader, java.lang.String,
     * org.apache.james.imap.api.process.ImapSession)
     */
    protected ImapMessage decode(ImapCommand command, ImapRequestLineReader request, String tag, ImapSession session) throws DecodingException {
        final String mailboxName = request.mailbox();
        boolean condstore = false;
        Long lastKnownUidValidity = null;
        Long knownModSeq = null;
        IdRange[] uidSet = null;
        IdRange[] knownUidSet = null;
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
                int pos = 0;
                while (pos < CONDSTORE.length) {
                    if (CONDSTORE[pos++] != ImapRequestLineReader.cap(request.consume())) {
                        throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown option");
                    }
                }
                condstore = true;
                break;
            case 'Q':
                // It starts with Q so it should be QRESYNC
                request.consumeWord(new CharacterValidator() {
                    int pos = 0;

                    public boolean isValid(char chr) {
                        if (pos >= QRESYNC.length) {
                            return false;
                        } else {
                            return ImapRequestLineReader.cap(chr) == QRESYNC[pos++];
                        }
                    }
                });
                
                // Consume the SP
                request.consumeChar(' ');
                
                // Consume enclosing paren
                request.consumeChar('(');
                lastKnownUidValidity = request.number();
                
                // Consume the SP
                request.consumeChar(' ');
                knownModSeq = request.number(true);
                
                char nc = request.nextChar();
                if (nc == ' ') {
                    // All this stuff is now optional
                       
                    // Consume the SP
                    request.consumeChar(' ');
                    uidSet = request.parseIdRange();
                    
                    // Check for *
                    checkIdRanges(uidSet, false);
                    
                    nc = request.nextChar();
                    if (nc == ' ')  {
                        request.consumeChar(' ');
                        
                        // This is enclosed in () so remove (
                        request.consumeChar('(');
                        knownSequenceSet = request.parseIdRange();
                        request.consumeChar(' ');
                        knownUidSet = request.parseIdRange();
                       
                        // Check for * and check if its in ascending order
                        checkIdRanges(knownSequenceSet, true);
                        checkIdRanges(knownUidSet, true);
                        
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
        final ImapMessage result = createRequest(command, mailboxName, condstore, lastKnownUidValidity, knownModSeq, uidSet, knownUidSet, knownSequenceSet, tag);
        return result;
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
     * 
     * 
     * @param ranges
     * @param checkOrder
     * @throws DecodingException
     */
    private void checkIdRanges(IdRange[] ranges, boolean checkOrder) throws DecodingException {
        long last = 0;
        for (int i = 0; i < ranges.length; i++ ) {
            
            IdRange r = ranges[i];
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
     * Create a new {@link AbstractMailboxSelectionRequest} for the given arguments
     * 
     * @param command
     * @param mailboxName
     * @param condstore
     * @param lastKnownUidValidity
     * @param knownModSeq
     * @param uidSet
     * @param knownUidSet
     * @param knownSequenceSet
     * @param tag
     * @return request
     */
    protected abstract AbstractMailboxSelectionRequest createRequest(ImapCommand command, String mailboxName, boolean condstore, Long lastKnownUidValidity, Long knownModSeq, IdRange[] uidSet, IdRange[] knownUidSet, IdRange[] knownSequenceSet, String tag);
}
