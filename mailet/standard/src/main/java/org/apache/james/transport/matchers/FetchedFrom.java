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

import javax.mail.internet.MimeMessage;
import java.util.Collection;

/**
 * Matches mail with a header set by Fetchpop X-fetched-from <br>
 * fetchpop sets X-fetched-by to the "name" of the fetchpop fetch task.<br>
 * This is used to match all mail fetched from a specific pop account.
 * Once the condition is met the header is stripped from the message to prevent looping if the mail is re-inserted into the spool.
 * 
 * $Id$
 */

public class FetchedFrom extends GenericMatcher {
    

    /*
     * (non-Javadoc)
     * @see org.apache.mailet.base.GenericMatcher#match(org.apache.mailet.Mail)
     */
    public Collection<MailAddress> match(Mail mail) throws javax.mail.MessagingException {
        MimeMessage message = mail.getMessage();
        String fetch = message.getHeader("X-fetched-from", null);
        if (fetch != null && fetch.equals(getCondition())) {
            mail.getMessage().removeHeader("X-fetched-from");
            return mail.getRecipients();
        }
        return null;
    }
}
