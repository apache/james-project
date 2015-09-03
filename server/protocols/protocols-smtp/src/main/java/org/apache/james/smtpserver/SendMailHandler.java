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

import java.util.Collection;

import javax.inject.Inject;
import javax.inject.Named;
import javax.mail.MessagingException;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;

/**
 * Queue the message
 */
public class SendMailHandler implements JamesMessageHook {

    private MailQueue queue;
    private MailQueueFactory queueFactory;

    @Inject
    public void setMailQueueFactory(@Named("mailqueuefactory") MailQueueFactory queueFactory) {
        this.queueFactory = queueFactory;
    }

    public void init() {

    }

    @Override
    public void init(Configuration config) throws ConfigurationException {
        queue = queueFactory.getQueue(MailQueueFactory.SPOOL);
    }

    @Override
    public void destroy() {

    }

    /**
     * Adds header to the message
     *
     */
    public HookResult onMessage(SMTPSession session, Mail mail) {
       
        session.getLogger().debug("sending mail");

        try {
            queue.enQueue(mail);
            Collection<MailAddress> theRecipients = mail.getRecipients();
            String recipientString = "";
            if (theRecipients != null) {
                recipientString = theRecipients.toString();
            }
            if (session.getLogger().isInfoEnabled()) {
                String infoBuffer = "Successfully spooled mail " + mail.getName() + " from " + mail.getSender() + " on " + session.getRemoteAddress().getAddress().toString() + " for " + recipientString;
                session.getLogger().info(infoBuffer.toString());
            }
        } catch (MessagingException me) {
            session.getLogger().error("Unknown error occurred while processing DATA.", me);
            return new HookResult(HookReturnCode.DENYSOFT, DSNStatus.getStatus(DSNStatus.TRANSIENT, DSNStatus.UNDEFINED_STATUS) + " Error processing message.");
        }
        
        return new HookResult(HookReturnCode.OK, DSNStatus.getStatus(DSNStatus.SUCCESS, DSNStatus.CONTENT_OTHER) + " Message received");
    
    }

}
