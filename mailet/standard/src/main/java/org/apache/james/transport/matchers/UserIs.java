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

import java.util.Set;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.base.GenericRecipientMatcher;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;

/**
 * Matches mail where the user is contained in a configurable list.
 * @version 1.0.0, 24/04/1999
 */
public class UserIs extends GenericRecipientMatcher {

    Set<String> users;

    @Override
    public void init() throws MessagingException {
        if (Strings.isNullOrEmpty(getCondition())) {
            throw new MessagingException("UserIs should have a condition composed of a list of local parts of mail addresses");
        }
        users = ImmutableSet.copyOf(Splitter.on(", ").split(getCondition()));
        if (users.isEmpty()) {
            throw new MessagingException("UserIs should have at least a user specified");
        }
    }

    @Override
    public boolean matchRecipient(MailAddress recipient) {
        return users.contains(recipient.getLocalPart());
    }
}

