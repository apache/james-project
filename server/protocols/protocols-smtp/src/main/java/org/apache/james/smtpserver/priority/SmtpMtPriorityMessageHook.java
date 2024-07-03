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

package org.apache.james.smtpserver.priority;

import static org.apache.james.protocols.api.ProtocolSession.State.Transaction;
import static org.apache.james.queue.api.MailPrioritySupport.LOW_PRIORITY;
import static org.apache.james.smtpserver.priority.SmtpMtPriorityParameterHook.MT_PRIORITY_ATTACHMENT_KEY;

import java.util.Optional;

import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.queue.api.MailPrioritySupport;
import org.apache.james.smtpserver.JamesMessageHook;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;

public class SmtpMtPriorityMessageHook implements JamesMessageHook {
    @Override
    public HookResult onMessage(SMTPSession session, Mail mail) {
        Optional<Integer> priority = session.getAttachment(MT_PRIORITY_ATTACHMENT_KEY, Transaction);
        priority.ifPresent(value -> mail.setAttribute(toMailPrioritySupportAttributeValue(value)));
        return HookResult.DECLINED;
    }

    private Attribute toMailPrioritySupportAttributeValue(Integer priorityValue) {
        if (priorityValue < LOW_PRIORITY) {
            return new Attribute(MailPrioritySupport.MAIL_PRIORITY, AttributeValue.of(LOW_PRIORITY));
        }
        return new Attribute(MailPrioritySupport.MAIL_PRIORITY, AttributeValue.of(priorityValue));
    }
}
