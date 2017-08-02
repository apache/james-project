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

package org.apache.james.smtpserver;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.queue.api.MailPrioritySupport;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;

/**
 * Handler which set a configured {@link Mail} priority for the mail.
 * 
 * if the {@link Mail} has more then one recipient, then the highest priority
 * (which was found) is set
 */
public class MailPriorityHandler implements JamesMessageHook, ProtocolHandler {

    private final Map<String, Integer> prioMap = new HashMap<>();

    /**
     * @see
     * org.apache.james.smtpserver.JamesMessageHook#onMessage(org.apache.james.protocols.smtp.SMTPSession,
     *  org.apache.mailet.Mail)
     */
    public HookResult onMessage(SMTPSession session, Mail mail) {
        Iterator<MailAddress> rcpts = mail.getRecipients().iterator();

        Integer p = null;

        while (rcpts.hasNext()) {
            String domain = rcpts.next().getDomain();
            Integer prio;
            if (domain != null) {
                prio = prioMap.get(domain);
                if (prio != null) {
                    if (p == null || prio > p) {
                        p = prio;
                    }

                    // already the highest priority
                    if (p == MailPrioritySupport.HIGH_PRIORITY)
                        break;
                }
            }
        }

        // set the priority if one was found
        if (p != null)
            mail.setAttribute(MailPrioritySupport.MAIL_PRIORITY, p);
        return new HookResult(HookReturnCode.DECLINED);
    }

    @Override
    public void init(Configuration config) throws ConfigurationException {
        List<HierarchicalConfiguration> entries = ((HierarchicalConfiguration)config).configurationsAt("priorityEntries.priorityEntry");
        for (HierarchicalConfiguration prioConf : entries) {
            String domain = prioConf.getString("domain");
            int prio = prioConf.getInt("priority", MailPrioritySupport.NORMAL_PRIORITY);
            if (prio > MailPrioritySupport.HIGH_PRIORITY || prio < MailPrioritySupport.LOW_PRIORITY) {
                throw new ConfigurationException("configured priority must be >= " + MailPrioritySupport.LOW_PRIORITY + " and <= " + MailPrioritySupport.HIGH_PRIORITY);
            }
            prioMap.put(domain, prio);
        }        
    }

    @Override
    public void destroy() {
        // nothing to do
    }

}
