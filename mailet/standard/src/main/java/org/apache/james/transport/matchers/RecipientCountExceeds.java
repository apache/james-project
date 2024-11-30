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

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * Matches mail with more recipients than the configured limit.
 *
 * Eg:
 *
 * <mailet match="RecipientCountExceeds=36" class="Null"/>
 */
public class RecipientCountExceeds extends GenericMatcher {
    private static final int DEFAULT_RECIPIENT_COUNT = 50;

    private int recipientCount = DEFAULT_RECIPIENT_COUNT;

    @Override
    public void init() throws MessagingException {
        int count = Integer.parseInt(getMatcherConfig().getCondition());
        Preconditions.checkArgument(count > 0, "Argument must be a strictly positive integer");
        recipientCount = count;
    }

    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        if (mail.getRecipients().size() > recipientCount) {
            return mail.getRecipients();
        }
        return ImmutableList.of();
    }
}
