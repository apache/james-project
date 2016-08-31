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
import java.util.Set;

import javax.mail.MessagingException;
import javax.mail.internet.AddressException;

import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.GenericMatcher;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class SenderIs extends GenericMatcher {

    private Set<MailAddress> senders;

    @VisibleForTesting
    public Set<MailAddress> getSenders() {
        return senders;
    }

    public void init() throws javax.mail.MessagingException {
        if (Strings.isNullOrEmpty(getCondition())) {
            throw new MessagingException("SenderIs should have at least one address as parameter");
        }
        senders = FluentIterable.from(Splitter.on(", ").split(getCondition())).transform(new Function<String, MailAddress>() {
            public MailAddress apply(String s) {
                try {
                    return new MailAddress(s);
                } catch (AddressException e) {
                    throw Throwables.propagate(e);
                }
            }
        }).toSet();
        if (senders.size() < 1) {
            throw new MessagingException("SenderIs should have at least one address as parameter");
        }
    }

    public Collection<MailAddress> match(Mail mail) {
        if (senders.contains(mail.getSender())) {
            return mail.getRecipients();
        } else {
            return null;
        }
    }
}
