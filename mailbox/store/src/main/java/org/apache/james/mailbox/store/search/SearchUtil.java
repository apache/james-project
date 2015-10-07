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

import java.nio.charset.Charset;
import java.util.Locale;

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
 * 
 *
 */
public class SearchUtil {

    private final static String FWD_PARENS = "(fwd)";
    private final static String SUBJ_FWD_HDR = "[fwd:";
    private final static String SUBJ_FWD_TRL = "]";
    private final static String RE = "re";
    private final static String FWD = "fwd";
    private final static String FW = "fw";
    private final static char WS = ' ';
    private final static char OPEN_SQUARE_BRACKED = '[';
    private final static char CLOSE_SQUARE_BRACKED = ']';
    private final static char COLON = ':';
    
    private final static Charset UTF8 = Charset.forName("UTF8");

    
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
     * @param mailbox
     * @return display
     */
    public static String getDisplayAddress(Mailbox mailbox) {
        String display = mailbox.getName();
        if (display == null || display.length() < 1) {
            String localPart = mailbox.getLocalPart();
            String domainPart = mailbox.getDomain();
            if (domainPart != null && domainPart.length() > 0 ) {
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
     * @param headerValue
     * @return display
     */
    public static String getDisplayAddress(String headerValue) {
        AddressList addressList = LenientAddressParser.DEFAULT.parseAddressList(MimeUtil.unfold(headerValue));
        if (addressList != null && addressList.isEmpty() == false) {
            Address address = addressList.get(0);
            if (address instanceof Mailbox) {
                return getDisplayAddress((Mailbox) address);
            } else if (address instanceof Group) {
                Group group = (Group) address;
                if (group != null) {
                    MailboxList mList = group.getMailboxes();
                    if (mList != null && mList.isEmpty() == false) {
                        return getDisplayAddress(mList.get(0));
                    }
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
     * @param mailbox
     * @return addrMailbox
     */
    public static String getMailboxAddress(Mailbox mailbox) {
        return mailbox.getLocalPart();
    }
    
    
    /**
     * Parse the headerValue and delegate to {@link #getMailboxAddress(Mailbox)}
     * 
     * If no mailbox name is found an empty String is returned
     * 
     * @param headerValue
     * @return mailbox
     */
    public static String getMailboxAddress(String headerValue) {
        AddressList aList = LenientAddressParser.DEFAULT.parseAddressList(headerValue);
        for (int i = 0; i < aList.size(); i++) {
            Address address = aList.get(i);
            if (address instanceof Mailbox) {
                Mailbox m = (Mailbox) address;
                String mailboxName = m.getLocalPart();
                if (mailboxName == null) {
                    mailboxName = "";
                }
                return mailboxName;
            } else if (address instanceof Group) {
                MailboxList mList = ((Group) address).getMailboxes();
                for (int a = 0; a < mList.size();) {
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
     *
     * @param subject
     * @return baseSubject
     */
    public static String getBaseSubject(String subject) {
            
            //   (1) Convert any RFC 2047 encoded-words in the subject to [UTF-8]
            //    as described in "Internationalization Considerations".
            //    Convert all tabs and continuations to space.  Convert all
            //    multiple spaces to a single space.
            String decodedSubject = MimeUtil.unfold(DecoderUtil.decodeEncodedWords(subject, DecodeMonitor.SILENT));
            decodedSubject = new String(decodedSubject.getBytes(UTF8), UTF8);

            // replace all tabs with spaces and replace multiple spaces with one space
            decodedSubject = decodedSubject.replaceAll("\t", " ").replaceAll("( ){2,}", " ");
            
            
            while (true) {
                int decodedSubjectLength = decodedSubject.length();
                while (true) {
                    //    (2) Remove all trailing text of the subject that matches the
                    //    subj-trailer ABNF; repeat until no more matches are possible.
                    String subj = removeSubTrailers(decodedSubject);
                    if (decodedSubjectLength > subj.length()) {
                        decodedSubject = subj;
                        decodedSubjectLength = decodedSubject.length();
                    } else {
                        break;
                    }

                }
                
                while (true) {
                    boolean matchedInner = false;

                    //    (3) Remove all prefix text of the subject that matches the subj-
                    //    leader ABNF.
                    decodedSubjectLength = decodedSubject.length();
                    decodedSubject = removeSubjLeaders(decodedSubject);
                    if (decodedSubjectLength > decodedSubject.length()) {
                        matchedInner = true;
                        decodedSubjectLength = decodedSubject.length();

                    }

                    //    (4) If there is prefix text of the subject that matches the subj-
                    //    blob ABNF, and removing that prefix leaves a non-empty subj-
                    //    base, then remove the prefix text.
                    decodedSubjectLength = decodedSubject.length();
                    String subj = removeBlob(decodedSubject);

                    // check if it will leave a non-empty subject
                    if (subj.length() > 0) {
                        decodedSubject = subj;
                        if (decodedSubjectLength > decodedSubject.length()) {
                            matchedInner = true;
                            decodedSubjectLength = decodedSubject.length();

                        }

                    }
                    // (5) Repeat (3) and (4) until no matches remain.
                    if (!matchedInner) {
                        // no more matches so break the loop 
                        break;
                    } 
                }
                String lowcaseSubject = decodedSubject.toLowerCase(Locale.US);
                
                if (lowcaseSubject.startsWith(SUBJ_FWD_HDR) && lowcaseSubject.endsWith(SUBJ_FWD_TRL)) {
                    //    (6) If the resulting text begins with the subj-fwd-hdr ABNF and
                    //    ends with the subj-fwd-trl ABNF, remove the subj-fwd-hdr and
                    //    subj-fwd-trl and repeat from step (2).
                    decodedSubject = decodedSubject.substring(SUBJ_FWD_HDR.length(), decodedSubject.length() - SUBJ_FWD_TRL.length());
                    decodedSubjectLength = decodedSubject.length();
                } else {
                    break;
                }
               
            }
            // (7) The resulting text is the "base subject" used in the SORT.
            return decodedSubject;
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
     *     
     * @param subject
     * @return sub
     */
    private static String removeSubjectBlob(String subject) {
        String subj = subject;
        while(subj.charAt(0) == OPEN_SQUARE_BRACKED) {
            int length = subj.length();
            subj = removeBlob(subject);
            int i = 0;
            if (subj.length() > 0 && subj.charAt(i) == CLOSE_SQUARE_BRACKED) {
                i++;
            } else {
                return subject;
            }
            while (subj.charAt(i) == WS) {
                i++;
            }
            subj = subj.substring(i);
            if (length == subj.length()) {
                return subj;
            }
        }
        return subj;
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
     *     
     * @param subject
     * @return sub
     */
    private static String removeSubjLeaders(String subject) {
        int subString = 0;
        while (subject.charAt(subString) == WS) {
            subString++;
        }
        if (subString > 0) {
            // check if we have matched WSP
            return subject.substring(subString);
        } else {

            String subj = removeSubjectBlob(subject);

            String lowCaseSubj = subj.toLowerCase(Locale.US);
            if (lowCaseSubj.startsWith(RE)) {
                subString = RE.length();
            } else if (lowCaseSubj.startsWith(FWD)) {
                subString = FWD.length();
            } else if (lowCaseSubj.startsWith(FW)) {
                subString = FW.length();
            } else {
                return subject;
            }
            while (subj.charAt(subString) == WS) {
                subString++;
            }

            /*
             * subj = removeSubjectBlob(subj.substring(subString)); if
             * (subj.endsWith(String.valueOf(CLOSE_SQUARE_BRACKED))) { subString
             * = 1; } else { subString = 0; }
             */

            if (subj.charAt(subString) == COLON) {
                subString++;
            } else {
                return subject;
            }

            while (subj.charAt(subString) == WS) {
                subString++;
            }
            return subj.substring(subString);
        }
    }

    
    /**
     * remove the remove_subj_trailers
     * 
     *    subj-trailer    = "(fwd)" / WSP
     *  
     *  
     * @param decodedSubject
     * * @return sub
     */
    private static String removeSubTrailers(String decodedSubject) {
        int subStringStart = 0;
        int subStringEnd = decodedSubject.length();

        int originalSize = decodedSubject.length();
        int curPos = originalSize -1;
        while(true) {
            char c = decodedSubject.charAt(curPos--);
            if (c == WS) {
                subStringEnd--;
            } else {
                if (subStringEnd > FWD_PARENS.length() && decodedSubject.endsWith(FWD_PARENS)) {
                    subStringEnd -= FWD_PARENS.length();
                } 
                break;
            }
        }
        decodedSubject = decodedSubject.substring(subStringStart, subStringEnd);
        return decodedSubject;
    }
    
    /**
     * Remove all blobchars
     * 
     *     BLOBCHAR = %x01-5a / %x5c / %x5e-7f
     *     ; any CHAR except '[' and ']' 
     *     
     * @param subject
     * @return subj
     */
    private static String removeBlob(String subject) {
        int i = 0;
        char lastChar = Character.UNASSIGNED;
        for (int a = 0; a < subject.length(); a++) {
            char c = subject.charAt(a);
            lastChar = c;
            if (( a != 0  && c == OPEN_SQUARE_BRACKED) || c == CLOSE_SQUARE_BRACKED) {
                break;
            }
            i++;
        }

        if (lastChar != CLOSE_SQUARE_BRACKED) {
            return subject;
        } else {
            // the lastChar was a ] so increase the count before substring
            i++;
            return subject.substring(i);
        }

    }


}
