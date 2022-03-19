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

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.transport.matchers.utils.MailAddressCollectionReader;
import org.apache.mailet.base.GenericRecipientMatcher;

import com.google.common.base.Strings;


/**
 * This matcher matches a specific recipient (in the envelope of the mail), passed as a condition to this matcher.
 *
 *
 * <p>The example below will match only the recipient user@domain</p>
 *
 * <pre><code>
 * &lt;mailet match=&quot;RecipientIs=user@domain&quot; class=&quot;&lt;any-class&gt;&quot;&gt;
 * &lt;/mailet&gt;
 * </code></pre>
 */
public class RecipientIs extends GenericRecipientMatcher {

    private Collection<Optional<MailAddress>> recipients;

    @Override
    public void init() throws MessagingException {
        if (Strings.isNullOrEmpty(getCondition())) {
            throw new MessagingException("RecipientIs should have a condition  composed of a list of mail addresses");
        }
        recipients = MailAddressCollectionReader.read(getCondition());
        if (recipients.isEmpty()) {
            throw new MessagingException("RecipientIs should have at least one address passed as a condition");
        }
    }

    @Override
    public boolean matchRecipient(MailAddress recipient) {
        return recipients.contains(Optional.of(recipient));
    }
}
