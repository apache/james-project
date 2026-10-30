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
package org.apache.james.mailbox.store.search;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.codec.DecoderUtil;
import org.apache.james.mime4j.dom.address.Address;
import org.apache.james.mime4j.dom.address.AddressList;
import org.apache.james.mime4j.dom.address.Group;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.dom.address.MailboxList;
import org.apache.james.mime4j.field.address.LenientAddressParser;
import org.apache.james.mime4j.util.MimeUtil;

/**
 * Utility class which helps with extracting of data for searches
 */
public class SearchUtil {

    private static final String FWD_PARENS = "(fwd)";
    private static final String SUBJ_FWD_HDR = "[fwd:";
    private static final String SUBJ_FWD_TRL = "]";
    private static final String RE = "re";
    private static final String FWD = "fwd";
    private static final String FW = "fw";
    private static final char WS = ' ';
    private static final char NO_BREAK_SPACE = (char) 0xA0;
    private static final char OPEN_SQUARE_BRACKED = '[';
    private static final char CLOSE_SQUARE_BRACKED = ']';
    private static final char COLON = ':';
    private static final int MAX_RAW_BYTES = 32766;

    /**
     * Return the DISPLAY ADDRESS for the given {@link Mailbox}. 
     * 
     * See rfc5957 3. DISPLAY Sort Value for an Address for the details
     *
     *     For the purposes of the sort criteria defined in this document, the
     *     sort value for an [IMAP] address structure is defined as follows:
     * <p>
     *      o If the address structure's [IMAP] addr-name is non-NIL, apply the
     *        procedure from [RFC5255], Section 4.6.  (That is, decode any
     *        RFC2047] encoded-words and convert the resulting character string
     *        into a charset valid for the currently active [RFC4790] collation,
     *        with a default of UTF-8.)  If the resulting octet string is not
     *        the empty string, use it as the sort value for the address.
     * </p>
     * <p>
     *      o  Otherwise, if the address structure's [IMAP] addr-mailbox and
     *         [IMAP] addr-host are both non-NIL, the sort value for the address
     *         is addr-mailbox@addr-host.
     * </p>
     * <p>
     *      o  Otherwise, if the address structure's [IMAP] addr-mailbox is non-
     *         NIL, the sort value for the address is its addr-mailbox.
     * </p>
     * <p>
     *      o  If none of the above conditions are met, the sort value for the
     *         address is the empty string.
     * </p>
     * @return display
     */
    public static String getDisplayAddress(Mailbox mailbox) {
        String display = mailbox.getName();
        if (display == null || display.length() < 1) {
            String localPart = mailbox.getLocalPart();
            String domainPart = mailbox.getDomain();
            if (domainPart != null && domainPart.length() > 0) {
                return localPart + "@" + domainPart;
            } else {
                return localPart;
            }
        }
        return display;
    }
    
    
    /**
     * Parse the headerValue and delegate to {@link #getDisplayAddress(Mailbox)}
     * 
     * If no display address is found an empty String is returned
     *
     * @return display
     */
    public static String getDisplayAddress(String headerValue) {
        AddressList addressList = LenientAddressParser.DEFAULT.parseAddressList(MimeUtil.unfold(headerValue));
        if (addressList != null && !addressList.isEmpty()) {
            Address address = addressList.get(0);
            if (address instanceof Mailbox) {
                return getDisplayAddress((Mailbox) address);
            } else if (address instanceof Group) {
                Group group = (Group) address;
                MailboxList mList = group.getMailboxes();
                if (mList != null && !mList.isEmpty()) {
                    return getDisplayAddress(mList.get(0));
                }
            }
        }
        
        return "";
    }
    
    /**
     * Return addr-mailbox of the first "From" address.
     * 
     * See RFC5256 and RFC3501
     * 
     * Which is in fact the LocalPart
     */
    public static String getMailboxAddress(Mailbox mailbox) {
        return mailbox.getLocalPart();
    }
    
    
    /**
     * Parse the headerValue and delegate to {@link #getMailboxAddress(Mailbox)}
     * 
     * If no mailbox name is found an empty String is returned
     *
     * @return mailbox
     */
    public static String getMailboxAddress(String headerValue) {
        AddressList aList = LenientAddressParser.DEFAULT.parseAddressList(headerValue);
        for (Address address : aList) {
            if (address instanceof Mailbox) {
                Mailbox m = (Mailbox) address;
                String mailboxName = m.getLocalPart();
                if (mailboxName == null) {
                    mailboxName = "";
                }
                return mailboxName;
            } else if (address instanceof Group) {
                MailboxList mList = ((Group) address).getMailboxes();
                for (int a = 0; a < mList.size(); ) {
                    String mailboxName = mList.get(a).getLocalPart();
                    if (mailboxName == null) {
                        mailboxName = "";
                    }
                    return mailboxName;
                }
            }
        }
        return "";
    }


    public static String getSerializedMessageIdIfSupportedByUnderlyingStorageOrNull(MailboxMessage mailboxMessage) {
        MessageId messageId = mailboxMessage.getMessageId();
        if (messageId != null && messageId.isSerializable()) {
            return messageId.serialize();
        }
        return null;
    }

    public static String getSerializedThreadIdIfSupportedByUnderlyingStorageOrNull(MailboxMessage mailboxMessage) {
        ThreadId threadId = mailboxMessage.getThreadId();
        if (threadId != null && threadId.isSerializable()) {
            return threadId.serialize();
        }
        return null;
    }
    
    
    /**
     * Extract the base subject from the given subject. 
     * 
     * See rfc5256 2.1 Base Subject
     * 
     * Subject sorting and threading use the "base subject", which has
     * specific subject artifacts removed.  Due to the complexity of these
     * artifacts, the formal syntax for the subject extraction rules is
     * ambiguous.  The following procedure is followed to determine the
     * "base subject", using the [ABNF] formal syntax rules described in
     * section 5:
     * <p>
     *    (1) Convert any RFC 2047 encoded-words in the subject to [UTF-8]
     *        as described in "Internationalization Considerations".
     *        Convert all tabs and continuations to space.  Convert all
     *        multiple spaces to a single space.
     * </p>
     * <p>
     *    (2) Remove all trailing text of the subject that matches the
     *        subj-trailer ABNF; repeat until no more matches are possible.
     * </p>
     * <p>
     *    (3) Remove all prefix text of the subject that matches the subj-
     *        leader ABNF.
     * </p>
     * <p>
     *    (4) If there is prefix text of the subject that matches the subj-
     *        blob ABNF, and removing that prefix leaves a non-empty subj-
     *        base, then remove the prefix text.
     * </p>
     * <p>
     *    (5) Repeat (3) and (4) until no matches remain.
     * </p>
     * Note: It is possible to defer step (2) until step (6), but this
     * requires checking for subj-trailer in step (4).
     * <br>
     * <p>
     *    (6) If the resulting text begins with the subj-fwd-hdr ABNF and
     *        ends with the subj-fwd-trl ABNF, remove the subj-fwd-hdr and
     *        subj-fwd-trl and repeat from step (2).
     * </p>
     * <p>
     *    (7) The resulting text is the "base subject" used in the SORT.
     * </p>
     *
     * @return baseSubject
     */
    public static String getBaseSubject(String subject) {
        //   (1) Convert any RFC 2047 encoded-words in the subject to [UTF-8]
        //    as described in "Internationalization Considerations".
        //    Convert all tabs and continuations to space.  Convert all
        //    multiple spaces to a single space.
        // replace all tabs with spaces and replace multiple spaces with one space
        String decodedSubject = normalizeSpace(
            MimeUtil.unfold(
                DecoderUtil.decodeEncodedWords(subject, DecodeMonitor.SILENT)));

        return new BaseSubjectExtractor(decodedSubject).extract();
    }

    /**
     * Unlike StringUtils::normalizeSpace, control characters are kept at both ends: they could otherwise
     * only be trimmed at the very start of the subject and not once a leader, a blob or a forward wrapper is removed.
     */
    private static String normalizeSpace(String subject) {
        StringBuilder result = new StringBuilder(subject.length());
        boolean pendingSpace = false;
        for (int i = 0; i < subject.length(); i++) {
            char c = subject.charAt(i);
            if (Character.isWhitespace(c) || c == NO_BREAK_SPACE) {
                pendingSpace = result.length() > 0;
            } else {
                if (pendingSpace) {
                    result.append(WS);
                    pendingSpace = false;
                }
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Works on the [start, end) window of the decoded subject and only copies the result once in order to
     * stay linear on subjects made of many leaders, blobs or trailers.
     */
    private static class BaseSubjectExtractor {
        private final String subject;
        private int start;
        private int end;
        // No bracket in [bracketSearchFrom, nextBracket), and nextBracket is a bracket or the subject length
        private int bracketSearchFrom = -1;
        private int nextBracket = -1;

        BaseSubjectExtractor(String subject) {
            this.subject = subject;
            this.start = 0;
            this.end = subject.length();
        }

        String extract() {
            while (true) {
                //    (2) Remove all trailing text of the subject that matches the
                //    subj-trailer ABNF; repeat until no more matches are possible.
                removeSubTrailers();

                while (true) {
                    boolean matchedInner = false;

                    //    (3) Remove all prefix text of the subject that matches the subj-
                    //    leader ABNF.
                    int afterLeaders = removeSubjLeaders();
                    if (afterLeaders > start) {
                        matchedInner = true;
                        start = afterLeaders;
                    }

                    //    (4) If there is prefix text of the subject that matches the subj-
                    //    blob ABNF, and removing that prefix leaves a non-empty subj-
                    //    base, then remove the prefix text.
                    int afterBlob = removeBlob(start);
                    // check if it will leave a non-empty subject
                    if (afterBlob < end && afterBlob > start) {
                        matchedInner = true;
                        start = afterBlob;
                    }

                    // (5) Repeat (3) and (4) until no matches remain.
                    if (!matchedInner) {
                        // no more matches so break the loop
                        break;
                    }
                }

                if (startsWithIgnoreCase(start, SUBJ_FWD_HDR) && endsWithIgnoreCase(SUBJ_FWD_TRL)) {
                    //    (6) If the resulting text begins with the subj-fwd-hdr ABNF and
                    //    ends with the subj-fwd-trl ABNF, remove the subj-fwd-hdr and
                    //    subj-fwd-trl and repeat from step (2).
                    start += SUBJ_FWD_HDR.length();
                    end -= SUBJ_FWD_TRL.length();
                } else {
                    break;
                }
            }
            // (7) The resulting text is the "base subject" used in the SORT.
            return subject.substring(start, end);
        }

        private boolean startsWithIgnoreCase(int from, String prefix) {
            return end - from >= prefix.length() && subject.regionMatches(true, from, prefix, 0, prefix.length());
        }

        private boolean endsWithIgnoreCase(String suffix) {
            return end - start >= suffix.length() && subject.regionMatches(true, end - suffix.length(), suffix, 0, suffix.length());
        }

        private void removeSubTrailers() {
            boolean removed = true;
            while (removed) {
                removed = removeSubTrailer();
            }
        }

        /**
         * Remove one subj-trailer
         *
         *    subj-trailer    = "(fwd)" / WSP
         *
         * @return true if a trailer was removed
         */
        private boolean removeSubTrailer() {
            if (end - start >= 2 && subject.charAt(end - 1) == WS) {
                while (end - 1 > start && subject.charAt(end - 1) == WS) {
                    end--;
                }
                return true;
            }
            if (end - start > FWD_PARENS.length() && subject.startsWith(FWD_PARENS, end - FWD_PARENS.length())) {
                end -= FWD_PARENS.length();
                return true;
            }
            return false;
        }

        /**
         * Remove the subj-leader
         *
         *     subj-leader = (*subj-blob subj-refwd) / WSP
         *     subj-blob = "[" *BLOBCHAR "]" *WSP
         *     subj-refwd = ("re" / ("fw" ["d"])) *WSP [subj-blob] ":"
         *
         *     BLOBCHAR = %x01-5a / %x5c / %x5e-7f
         *     ; any CHAR except '[' and ']'
         *
         * @return the start of the subject once the leader is removed
         */
        private int removeSubjLeaders() {
            int position = skipWhiteSpaces(start);
            if (position > start) {
                // check if we have matched WSP
                return position;
            }

            position = removeSubjectBlob();
            if (startsWithIgnoreCase(position, RE)) {
                position += RE.length();
            } else if (startsWithIgnoreCase(position, FWD)) {
                position += FWD.length();
            } else if (startsWithIgnoreCase(position, FW)) {
                position += FW.length();
            } else {
                return start;
            }
            position = skipWhiteSpaces(position);

            if (position < end && subject.charAt(position) == COLON) {
                position++;
            } else {
                return start;
            }
            return skipWhiteSpaces(position);
        }

        /**
         * Remove the subj-blob
         *
         *     subj-blob = "[" *BLOBCHAR "]" *WSP
         *     subj-refwd = ("re" / ("fw" ["d"])) *WSP [subj-blob] ":"
         *
         *     BLOBCHAR = %x01-5a / %x5c / %x5e-7f
         *     ; any CHAR except '[' and ']'
         *
         * @return the start of the subject once the blob is removed
         */
        private int removeSubjectBlob() {
            if (start >= end || subject.charAt(start) != OPEN_SQUARE_BRACKED) {
                return start;
            }
            int afterBlob = removeBlob(start);
            if (afterBlob < end && subject.charAt(afterBlob) == CLOSE_SQUARE_BRACKED) {
                return skipWhiteSpaces(afterBlob + 1);
            }
            return start;
        }

        /**
         * Remove all blobchars
         *
         *     BLOBCHAR = %x01-5a / %x5c / %x5e-7f
         *     ; any CHAR except '[' and ']'
         *
         * @return the position following the first ']' if the subject starts with '[' at from and no other '['
         * comes before that ']', from otherwise
         */
        private int removeBlob(int from) {
            if (from >= end || subject.charAt(from) != OPEN_SQUARE_BRACKED) {
                return from;
            }
            int bracket = nextBracket(from + 1);
            if (bracket < end && subject.charAt(bracket) == CLOSE_SQUARE_BRACKED) {
                return bracket + 1;
            }
            return from;
        }

        /**
         * Positions only grow, so caching the last lookup keeps the overall scan linear.
         */
        private int nextBracket(int from) {
            if (from >= bracketSearchFrom && from <= nextBracket) {
                return nextBracket;
            }
            int position = from;
            while (position < subject.length()
                && subject.charAt(position) != OPEN_SQUARE_BRACKED
                && subject.charAt(position) != CLOSE_SQUARE_BRACKED) {
                position++;
            }
            bracketSearchFrom = from;
            nextBracket = position;
            return position;
        }

        private int skipWhiteSpaces(int from) {
            int position = from;
            while (position < end && subject.charAt(position) == WS) {
                position++;
            }
            return position;
        }
    }

    public static Predicate<MessageId> distinct() {
        return new Predicate<MessageId>() {
            private final Set<MessageId> set = new HashSet<>();

            @Override
            public boolean test(MessageId input) {
                return set.add(input);
            }
        };
    }

    public static String removeGreaterThanCharactersAtBeginningOfLine(String text) {
        StringBuilder result = new StringBuilder();
        boolean isNewLine = false;

        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);

            if (current == '\n') {
                isNewLine = true;
                result.append(current);
            } else if (!isNewLine || current != '>') {
                result.append(current);
                isNewLine = false;
            }
        }

        return result.toString();
    }

    public static String truncateSubjectField(String subject) {
        if (subject == null) {
            return null;
        }

        byte[] subjectAsBytes = subject.getBytes(StandardCharsets.UTF_8);
        if (subjectAsBytes.length < MAX_RAW_BYTES) {
            return subject;
        }

        return new String(Arrays.copyOf(subjectAsBytes, MAX_RAW_BYTES), StandardCharsets.UTF_8);
    }
}
