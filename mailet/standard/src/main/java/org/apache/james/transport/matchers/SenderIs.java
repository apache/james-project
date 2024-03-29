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
import java.util.Optional;
import java.util.Set;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.transport.matchers.utils.MailAddressCollectionReader;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;


/**
 * This matcher matches a specific sender, passed as a condition to this matcher.
 *
 *
 * <p>The example below will match mail with a sender being user@domain</p>
 *
 * <pre><code>
 * &lt;mailet match=&quot;SenderIs=user@domain&quot; class=&quot;&lt;any-class&gt;&quot;&gt;
 * &lt;/mailet&gt;
 * </code></pre>
 */
public class SenderIs extends GenericMatcher {

    private Set<Optional<MailAddress>> senders;

    @VisibleForTesting
    Set<Optional<MailAddress>> getSenders() {
        return senders;
    }

    @Override
    public void init() throws MessagingException {
        if (Strings.isNullOrEmpty(getCondition())) {
            throw new MessagingException("SenderIs should have at least one address as parameter");
        }
        senders = MailAddressCollectionReader.read(getCondition());
        if (senders.isEmpty()) {
            throw new MessagingException("SenderIs should have at least one address as parameter");
        }
    }

    @Override
    public Collection<MailAddress> match(Mail mail) {
        if (senders.contains(mail.getMaybeSender().asOptional())) {
            return mail.getRecipients();
        } else {
            return null;
        }
    }
}
