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

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.base.AutomaticallySentMailDetector;
import org.apache.mailet.base.GenericMatcher;

import javax.inject.Inject;
import javax.mail.MessagingException;
import java.util.Collection;
import java.util.Collections;

/**
 * <p>Matches if mail is from a mailing list.</p>
 * <p>Implements the match method to check if the incoming mail is from a mailing list.
 * If the mail is from a mailing list, then returns all the recipients of the mail.</p>
 */

public class IsFromMailingList extends GenericMatcher {

    /**
     * Used to detect automatically sent mails.
     */
    private final AutomaticallySentMailDetector automaticallySentMailDetector;

    /**
     * Constructor for IsFromMailingList.
     * @param automaticallySentMailDetector Mail detector.
     */
    @Inject
    public IsFromMailingList(AutomaticallySentMailDetector automaticallySentMailDetector) {
        this.automaticallySentMailDetector = automaticallySentMailDetector;
    }

    /**
     * Checks if the incoming mail is from a mailing list and returns all the recipients of the mail.
     * @param mail Mail to be matched.
     * @throws MessagingException if there is a problem while matching the mail.
     * @return Collection of MailAddress if matches, else an empty Collection.
     */
    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        if (automaticallySentMailDetector.isMailingList(mail)) {
            return mail.getRecipients();
        }
        return Collections.emptyList();
    }
}
