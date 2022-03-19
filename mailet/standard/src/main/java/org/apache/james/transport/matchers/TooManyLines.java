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
import org.apache.mailet.base.MailetUtil;

import com.google.common.collect.ImmutableList;

/**
 * This matcher matches emails that have too many lines. This allows better rejection of emails when, for instance, MIME4J
 * is configured with a limited number of lines.
 *
 * <p>The example below will match mail with more than 10000 lines</p>
 *
 * <pre><code>
 * &lt;mailet match=&quot;TooManyLines=10000&quot; class=&quot;&lt;any-class&gt;&quot;/&gt;
 * </code></pre>
 */
public class TooManyLines extends GenericMatcher {

    private int maximumLineCount;

    @Override
    public void init() throws MessagingException {
        maximumLineCount = MailetUtil.getInitParameterAsStrictlyPositiveInteger(getCondition());
    }

    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        if (mail.getMessage() == null) {
            return ImmutableList.of();
        }

        if (mail.getMessage().getLineCount() > maximumLineCount) {
            return ImmutableList.copyOf(mail.getRecipients());
        }

        return ImmutableList.of();
    }
}

