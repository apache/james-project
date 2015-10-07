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
import java.util.Collection;
import java.util.Locale;

/**
 * Checks whether the message (entire message, not just content) is greater
 * than a certain number of bytes.  You can use 'k' and 'm' as optional postfixes.
 * In other words, "1m" is the same as writing "1024k", which is the same as
 * "1048576".
 *
 */
public class SizeGreaterThan extends GenericMatcher {

    int cutoff = 0;

    /*
     * (non-Javadoc)
     * @see org.apache.mailet.base.GenericMatcher#init()
     */
    public void init() throws MessagingException {
        String amount = getCondition();
        
        if (amount != null) {
            amount = amount.trim().toLowerCase(Locale.US);
        } else {
            throw new MessagingException("Please configure an amount");
        }
        try {
            if (amount.endsWith("k")) {
                amount = amount.substring(0, amount.length() - 1);
                cutoff = Integer.parseInt(amount) * 1024;
            } else if (amount.endsWith("m")) {
                amount = amount.substring(0, amount.length() - 1);
                cutoff = Integer.parseInt(amount) * 1024 * 1024;
            } else {
                cutoff = Integer.parseInt(amount);
            }
        } catch (NumberFormatException e) {
            throw new MessagingException("Invalid amount: " + amount);
        }
    }

    /*
     * (non-Javadoc)
     * @see org.apache.mailet.base.GenericMatcher#match(org.apache.mailet.Mail)
     */
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        if (mail.getMessageSize() > cutoff) {
            return mail.getRecipients();
        } else {
            return null;
        }
    }
}
