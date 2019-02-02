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

package org.apache.james.transport.mailets;

import java.util.Optional;

import javax.mail.MessagingException;

import org.apache.james.queue.api.MailPrioritySupport;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

/**
 * This mailet sets the priority of the incoming mail.
 *
 * Example configuration:
 *
 * <mailet match="All" class="WithPriority">
 *     <priority>7</priority>
 * </mailet>
 */
public class WithPriority extends GenericMailet {

    private int priority;

    @Override
    public String getMailetInfo() {
        return "With Priority Mailet";
    }

    @Override
    public void init() throws MessagingException {
        priority = Optional.ofNullable(getInitParameter("priority", null))
                .map(Integer::valueOf)
                .orElseThrow(() -> new IllegalArgumentException("'priority' init parameter is compulsory"));

        if (priority < 0 || priority > 9) {
            throw new IllegalArgumentException("Invalid priority: Priority should be from 0 to 9");
        }
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        mail.setAttribute(MailPrioritySupport.MAIL_PRIORITY, priority);
    }
}
