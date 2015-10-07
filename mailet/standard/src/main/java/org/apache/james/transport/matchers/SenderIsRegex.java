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

import java.util.Collection;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.mail.MessagingException;

/**
 * <P>Matches mails that are sent by a sender whose address matches a regular expression.</P>
 * <P>Is equivalent to the {@link RecipientIsRegex} matcher but matching on the sender.</P>
 * <P>Configuration string: a regular expression.</P>
 * <PRE><CODE>
 * &lt;mailet match=&quot;SenderIsRegex=&lt;regular-expression&gt;&quot; class=&quot;&lt;any-class&gt;&quot;&gt;
 * </CODE></PRE>
 * <P>The example below will match any sender in the format user@log.anything</P>
 * <PRE><CODE>
 * &lt;mailet match=&quot;SenderIsRegex=(.*)@log\.(.*)&quot; class=&quot;&lt;any-class&gt;&quot;&gt;
 * &lt;/mailet&gt;
 * </CODE></PRE>
 * <P>Another example below will match any sender having some variations of the string
 * <I>mp3</I> inside the username part.</P>
 * <PRE><CODE>
 * &lt;mailet match=&quot;SenderIsRegex=(.*)(mp3|emmepitre)(.*)@&quot; class=&quot;&lt;any-class&gt;&quot;&gt;
 * &lt;/mailet&gt;
 * </CODE></PRE>
 *
 * @version CVS $Revision$ $Date$
 * @since 2.2.0
 */
public class SenderIsRegex extends GenericMatcher {
    Pattern pattern   = null;

    public void init() throws MessagingException {
        String patternString = getCondition();
        if ((patternString == null) || (patternString.equals(""))) {
            throw new MessagingException("Pattern is missing");
        }
        
        patternString = patternString.trim();
        try {
            pattern = Pattern.compile(patternString);
        } catch(PatternSyntaxException mpe) {
            throw new MessagingException("Malformed pattern: " + patternString, mpe);
        }
    }

    public Collection<MailAddress> match(Mail mail) {
        MailAddress mailAddress = mail.getSender();
        if (mailAddress == null) {
            return null;
        }
        String senderString = mailAddress.toString();
        if (pattern.matcher(senderString).matches()) {
            return mail.getRecipients();
        }
        return null;
    }
}
