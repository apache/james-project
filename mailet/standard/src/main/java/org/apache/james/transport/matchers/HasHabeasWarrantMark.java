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

import org.apache.mailet.base.GenericMatcher;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.util.Collection;

/**
 * <p>This matcher tests for the Hebeas Warrant Mark.
 * For details see: http://www.hebeas.com</p>
 *
 * <p>Usage: Place this matcher</p>
 * <pre><code>
 * &lt;mailet match="HasHabeasWarrantMark" class="ToProcessor"&gt;
 *     &lt;processor&gt; transport &lt;/processor&gt;
 * &lt;/mailet&gt;
 * </code></pre>
 * <p>in the root processs before the DNSRBL block lists (the InSpammerBlacklist matcher).</p>
 *
 * <p>Because the Habeas Warrant Mark is copyright material, I have asked for and
 * received the following explicit statement from Habeas:</p>
 * <pre>
 * -----------------------------------
 * From: Lindsey Pettit [mailto:support@habeas.com]
 * Sent: Sunday, September 29, 2002 5:51
 * To: Noel J. Bergman
 * Subject: RE: Habeas and Apache James
 *
 * Dear Noel,
 * 
 * > I guess that since your Warrant Mark is copyright, I should ask for
 * > something from you to explicitly authorize that Hebeas will permit
 * > this code to be included and distributed as part of Apache James
 * > under the Apache Software License.  As we have established, the use
 * > of the Habeas Warrant Mark for filtering is not restricted, but I
 * > would like something to confirm that, so that Apache will be happy.
 *
 * I can hereby confirm to you that there is no license necessary in 
 * order to use the Habeas mark for filtering.  That said, however, we 
 * do insist that it not ever be used as a basis for rejecting email which 
 * bears the Habeas mark.
 * -----------------------------------
 * </pre>
 */

public class HasHabeasWarrantMark extends GenericMatcher
{
    public static final String[][] warrantMark =
    {
        { "X-Habeas-SWE-1", "winter into spring" }, 
        { "X-Habeas-SWE-2", "brightly anticipated" }, 
        { "X-Habeas-SWE-3", "like Habeas SWE (tm)" }, 
        { "X-Habeas-SWE-4", "Copyright 2002 Habeas (tm)" }, 
        { "X-Habeas-SWE-5", "Sender Warranted Email (SWE) (tm). The sender of this" }, 
        { "X-Habeas-SWE-6", "email in exchange for a license for this Habeas" }, 
        { "X-Habeas-SWE-7", "warrant mark warrants that this is a Habeas Compliant" }, 
        { "X-Habeas-SWE-8", "Message (HCM) and not spam. Please report use of this" }, 
        { "X-Habeas-SWE-9", "mark in spam to <http://www.habeas.com/report/>." }, 
    };

    public Collection<MailAddress> match(Mail mail) throws MessagingException
    {
        MimeMessage message = mail.getMessage();

        //Loop through all the patterns
        for (String[] aWarrantMark : warrantMark)
            try {
                String headerName = aWarrantMark[0];                      //Get the header name
                String requiredValue = aWarrantMark[1];                   //Get the required value
                String headerValue = message.getHeader(headerName, null);   //Get the header value(s)

                // We want an exact match, so only test the first value.
                // If there are multiple values, the header may be
                // (illegally) forged.  I'll leave it as an exercise to
                // others if they want to detect and report potentially
                // forged headers.

                if (!(requiredValue.equals(headerValue))) return null;
            } catch (Exception e) {
                log(e.toString());
                return null;            //if we get an exception, don't validate the mark
            }

        // If we get here, all headers are present and match.
        return mail.getRecipients();
    }

    /*
     * Returns information about the matcher, such as author, version, and copyright.
     * <p>
     * The string that this method returns should be plain text and not markup
     * of any kind (such as HTML, XML, etc.).
     *
     * @return a String containing matcher information
     */

    public String getMatcherInfo()
    {
        return "Habeas Warrant Mark Matcher (see http://www.habeas.com for details).";
    }
}

