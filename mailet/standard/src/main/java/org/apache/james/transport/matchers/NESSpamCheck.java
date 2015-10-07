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



package org.apache.james.transport.matchers;

import java.util.regex.PatternSyntaxException;

import javax.mail.MessagingException;

import org.apache.mailet.base.RFC2822Headers;

/**
 * This is based on a sample filter.cfg for a Netscape Mail Server to stop
 * spam.
 *
 */
public class NESSpamCheck extends GenericRegexMatcher {
    protected Object NESPatterns[][] = {{RFC2822Headers.RECEIVED, "GAA.*-0600.*EST"},
    {RFC2822Headers.RECEIVED, "XAA.*-0700.*EDT"},
    {RFC2822Headers.RECEIVED, "xxxxxxxxxxxxxxxxxxxxx"},
    {RFC2822Headers.RECEIVED, "untrace?able"},
    {RFC2822Headers.RECEIVED, "from (baby|bewellnet|kllklk) "},
    {RFC2822Headers.TO, "Friend@public\\.com"},
    {RFC2822Headers.TO, "user@the[-_]internet"},
    {RFC2822Headers.DATE, "/[0-9]+/.+[AP]M.+Time"},
    {RFC2822Headers.SUBJECT, "^\\(?ADV?[:;)]"},
    {RFC2822Headers.MESSAGE_ID, "<>"},
    {RFC2822Headers.MESSAGE_ID_VARIATION, "<>"},
    {RFC2822Headers.MESSAGE_ID_VARIATION, "<(419\\.43|989\\.28)"},
    {"X-MimeOLE", "MimeOLE V[^0-9]"},
            //Added 20-Jun-1999.  Appears to be broken spamware.
    {"MIME-Version", "1.0From"},
            //Added 28-July-1999.  Check X-Mailer for spamware.
    {"X-Mailer", "DiffondiCool"},
    {"X-Mailer", "Emailer Platinum"},
    {"X-Mailer", "eMerge"},
    {"X-Mailer", "Crescent Internet Tool"},
            //Added 4-Apr-2000.  Check X-Mailer for Cybercreek Avalanche
    {"X-Mailer", "Avalanche"},
            //Added 21-Oct-1999.  Subject contains 20 or more consecutive spaces
    {"Subject", "                    "},
            //Added 31-Mar-2000.  Invalid headers from MyGuestBook.exe CGI spamware
    {"MessageID", "<.+>"},
    {"X-References", "0[A-Z0-9]+, 0[A-Z0-9]+$"},
    {"X-Other-References", "0[A-Z0-9]+$"},
    {"X-See-Also", "0[A-Z0-9]+$"},
            //Updated 28-Apr-1999.  Check for "Sender", "Resent-From", or "Resent-By"
            // before "X-UIDL".  If found, then exit.
    {RFC2822Headers.SENDER, ".+"},
    {RFC2822Headers.RESENT_FROM, ".+"},
    {"Resent-By", ".+"},
            //Updated 19-May-1999.  Check for "X-Mozilla-Status" before "X-UIDL".
    {"X-Mozilla-Status", ".+"},
            //Updated 20-Jul-1999.  Check for "X-Mailer: Internet Mail Service"
            // before "X-UIDL".
    {"X-Mailer", "Internet Mail Service"},
            //Updated 25-Oct-1999.  Check for "X-ID" before "X-UIDL".
    {"X-ID", ".+"},
            //X-UIDL is a POP3 header that should normally not be seen
    {"X-UIDL", ".*"},
            //Some headers are valid only for the Pegasus Mail client.  So first check
            //for Pegasus header and exit if found.  If not found, check for
            //invalid headers: "Comments: Authenticated sender", "X-PMFLAGS" and "X-pmrqc".
    {"X-mailer", "Pegasus"},
            //Added 27-Aug-1999.  Pegasus now uses X-Mailer instead of X-mailer.
    {"X-Mailer", "Pegasus"},
            //Added 25-Oct-1999.  Check for X-Confirm-Reading-To.
    {"X-Confirm-Reading-To", ".+"},
            //Check for invalid Pegasus headers
    {RFC2822Headers.COMMENTS, "Authenticated sender"},
    {"X-PMFLAGS", ".*"},
    {"X-Pmflags", ".*"},
    {"X-pmrqc", ".*"},
    {"Host-From:envonly", ".*"}};

    public void init() throws MessagingException {
        //No condition passed... just compile a bunch of regular expressions
        try {
            compile(NESPatterns);
        } catch(PatternSyntaxException mp) {
            throw new MessagingException("Could not initialize NES patterns", mp);
        }
    }
}
