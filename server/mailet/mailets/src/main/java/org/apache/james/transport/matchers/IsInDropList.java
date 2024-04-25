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

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.droplists.api.DropList;
import org.apache.james.droplists.api.OwnerScope;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

import com.google.common.collect.ImmutableList;

/**
 * This matcher that checks if a mail sender is permitted based on their status in the DropList.
 *
 * <p>Implements the match method to check if the sender of the incoming mail is not listed in the DropList.
 * If the sender is not found in the DropList, the matcher will return all recipients of the mail for which the sender is allowed.</p>
 * </p>Note:</p>
 * managing DropLists can be accomplished through <a href="http://james.apache.org/server/manage-webadmin.html">WebAdmin</a>.
 **/
public class IsInDropList extends GenericMatcher {

    private final DropList dropList;

    @Inject
    public IsInDropList(DropList dropList) {
        this.dropList = dropList;
    }

    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        return mail.getRecipients()
            .stream()
            .filter(recipient -> isRecipientAllowed(mail, recipient))
            .collect(ImmutableList.toImmutableList());
    }

    private boolean isRecipientAllowed(Mail mail, MailAddress recipient) {
        MailAddress sender = mail.getMaybeSender().get();
        return isAllowed(OwnerScope.GLOBAL, recipient.asString(), sender) &&
            isAllowed(OwnerScope.DOMAIN, recipient.getDomain().asString(), sender) &&
            isAllowed(OwnerScope.USER, recipient.asString(), sender);
    }

    private boolean isAllowed(OwnerScope ownerScope, String owner, MailAddress sender) {
        DropList.Status status = dropList.query(ownerScope, owner, sender).block();
        return status == DropList.Status.ALLOWED;
    }
}