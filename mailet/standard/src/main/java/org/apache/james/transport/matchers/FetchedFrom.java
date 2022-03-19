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

import java.util.Collection;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

import com.google.common.base.Objects;

/**
 * Matches mail with a header set by Fetchpop X-fetched-from <br>
 * fetchpop sets X-fetched-by to the "name" of the fetchpop fetch task.<br>
 * This is used to match all mail fetched from a specific pop account.
 * Once the condition is met the header is stripped from the message to prevent looping if the mail is re-inserted into the spool.
 * 
 * $Id$
 */

public class FetchedFrom extends GenericMatcher {

    public static final String X_FETCHED_FROM = "X-fetched-from";

    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        MimeMessage message = mail.getMessage();
        String fetchHeaderValue = message.getHeader(X_FETCHED_FROM, null);
        if (Objects.equal(fetchHeaderValue, getCondition())) {
            mail.getMessage().removeHeader(X_FETCHED_FROM);
            return mail.getRecipients();
        }
        return null;
    }
}
