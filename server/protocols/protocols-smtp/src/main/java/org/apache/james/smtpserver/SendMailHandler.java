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

import javax.inject.Inject;
import javax.mail.MessagingException;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Queue the message
 */
public class SendMailHandler implements JamesMessageHook {
    private static final Logger LOGGER = LoggerFactory.getLogger(SendMailHandler.class);

    private final MailQueueFactory<?> queueFactory;
    private MailQueue queue;

    @Inject
    public SendMailHandler(MailQueueFactory<?> queueFactory) {
        this.queueFactory = queueFactory;
    }

    @Override
    public void init(Configuration config) {
        queue = queueFactory.createQueue(MailQueueFactory.SPOOL);
    }

    /**
     * Adds header to the message
     *
     */
    @Override
    public HookResult onMessage(SMTPSession session, Mail mail) {
        LOGGER.debug("sending mail");

        try {
            queue.enQueue(mail);
            LOGGER.info("Successfully spooled mail {} from {} on {} for {}", mail.getName(), mail.getMaybeSender(), session.getRemoteAddress().getAddress(), mail.getRecipients());
        } catch (MessagingException me) {
            LOGGER.error("Unknown error occurred while processing DATA.", me);
            return HookResult.builder()
                .hookReturnCode(HookReturnCode.denySoft())
                .smtpDescription(DSNStatus.getStatus(DSNStatus.TRANSIENT, DSNStatus.UNDEFINED_STATUS) + " Error processing message.")
                .build();
        }
        return HookResult.builder()
            .hookReturnCode(HookReturnCode.ok())
            .smtpDescription(DSNStatus.getStatus(DSNStatus.SUCCESS, DSNStatus.CONTENT_OTHER) + " Message received")
            .build();
    }

}
