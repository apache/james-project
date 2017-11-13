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

package org.apache.james.jmap.methods;

import java.io.IOException;

import javax.inject.Inject;
import javax.mail.MessagingException;

import org.apache.james.jmap.model.Message;
import org.apache.james.jmap.model.MessageFactory;
import org.apache.james.jmap.send.MailFactory;
import org.apache.james.jmap.send.MailMetadata;
import org.apache.james.jmap.send.MailSpool;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.mailet.Mail;

public class MessageSender {
    private final MailSpool mailSpool;
    private final MailFactory mailFactory;

    @Inject
    public MessageSender(MailSpool mailSpool, MailFactory mailFactory) {
        this.mailSpool = mailSpool;
        this.mailFactory = mailFactory;
    }

    public void sendMessage(Message jmapMessage,
                            MessageFactory.MetaDataWithContent message,
                            MailboxSession session) throws MailboxException, MessagingException {
        validateUserIsInSenders(jmapMessage, session);
        Mail mail = buildMessage(message, jmapMessage);
        try {
            MailMetadata metadata = new MailMetadata(jmapMessage.getId(), session.getUser().getUserName());
            mailSpool.send(mail, metadata);
        } finally {
            LifecycleUtil.dispose(mail);
        }
    }

    private Mail buildMessage(MessageFactory.MetaDataWithContent message, Message jmapMessage) throws MessagingException {
        try {
            return mailFactory.build(message, jmapMessage);
        } catch (IOException e) {
            throw new MessagingException("error building message to send", e);
        }
    }

    private void validateUserIsInSenders(Message message, MailboxSession session) throws MailboxSendingNotAllowedException {
        String allowedSender = session.getUser().getUserName();
        if (!isAllowedFromAddress(message, allowedSender)) {
            throw new MailboxSendingNotAllowedException(allowedSender);
        }
    }

    private boolean isAllowedFromAddress(Message message, String allowedFromMailAddress) {
        return message.getFrom()
            .map(draftEmailer -> draftEmailer.getEmail()
                .map(allowedFromMailAddress::equals)
                .orElse(false))
            .orElse(false);
    }
}
