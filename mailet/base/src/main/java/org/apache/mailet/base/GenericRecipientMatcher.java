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


package org.apache.mailet.base;

import java.util.ArrayList;
import java.util.Collection;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;

import com.google.common.collect.ImmutableList;

/**
 * GenericMatcher makes writing recipient based matchers easier. It provides
 * simple versions of the lifecycle methods init and destroy and of the methods
 * in the MatcherConfig interface. GenericMatcher also implements the log method,
 * declared in the MatcherContext interface.
 *
 * @version 1.0.0, 24/04/1999
 */
public abstract class GenericRecipientMatcher extends GenericMatcher {

    /**
     * Matches each recipient one by one through matchRecipient(MailAddress
     * recipient) method.  Handles splitting the recipients Collection
     * as appropriate.
     *
     * @param mail - the message and routing information to determine whether to match
     * @return Collection the Collection of MailAddress objects that have been matched
     */
    @Override
    public final Collection<MailAddress> match(Mail mail) throws MessagingException {
        Collection<MailAddress> matching = new ArrayList<>();
        for (MailAddress recipient : mail.getRecipients()) {
            if (matchRecipient(recipient)) {
                matching.add(recipient);
            }
        }
        return ImmutableList.copyOf(matching);
    }

    /**
     * Simple check to match exclusively on the email address (not
     * message information).
     *
     * @param recipient - the address to determine whether to match
     * @return boolean whether the recipient is a match
     */
    public abstract boolean matchRecipient(MailAddress recipient) throws MessagingException;
}
