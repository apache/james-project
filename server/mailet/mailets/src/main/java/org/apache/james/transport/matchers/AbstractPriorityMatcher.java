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
import org.apache.james.queue.api.MailPrioritySupport;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;
import org.apache.mailet.base.MailetUtil;

import com.google.common.collect.ImmutableList;


public abstract class AbstractPriorityMatcher extends GenericMatcher {
    private final String priorityMatcherName;
    private int priority;

    protected AbstractPriorityMatcher(String priorityMatcherName) {
        this.priorityMatcherName = priorityMatcherName;
    }

    @Override
    public void init() throws MessagingException {
        priority = MailetUtil.getInitParameterAsStrictlyPositiveInteger(getCondition());
    }

    @Override
    public Collection<MailAddress> match(Mail mail) {
        return AttributeUtils.getValueAndCastFromMail(mail, MailPrioritySupport.MAIL_PRIORITY, Integer.class)
                .filter(this::priorityMatch)
                .map(any -> mail.getRecipients())
                .orElse(ImmutableList.of());
    }

    public abstract boolean priorityMatch(int mailPriorityValue);

    public Integer getPriority() {
        return priority;
    }

    public String getPriorityMatcherName() {
        return priorityMatcherName;
    }
}
