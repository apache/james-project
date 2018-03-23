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

import javax.inject.Inject;
import javax.mail.MessagingException;

import org.apache.james.jmap.model.Envelope;
import org.apache.james.jmap.model.MessageFactory;
import org.apache.james.jmap.send.MailFactory;
import org.apache.james.jmap.send.MailMetadata;
import org.apache.james.jmap.send.MailSpool;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MessageId;
import org.apache.mailet.Mail;

public class MessageSender {
    private final MailSpool mailSpool;
    private final MailFactory mailFactory;

    @Inject
    public MessageSender(MailSpool mailSpool, MailFactory mailFactory) {
        this.mailSpool = mailSpool;
        this.mailFactory = mailFactory;
    }

    public void sendMessage(MessageFactory.MetaDataWithContent message,
                            Envelope envelope,
                            MailboxSession session) throws MessagingException {
        Mail mail = mailFactory.build(message, envelope);
        try {
            sendMessage(message.getMessageId(), mail, session);
        } finally {
            LifecycleUtil.dispose(mail);
        }
    }

    public void sendMessage(MessageId messageId,
                            Mail mail,
                            MailboxSession session) throws MessagingException {
        MailMetadata metadata = new MailMetadata(messageId, session.getUser().getUserName());
        mailSpool.send(mail, metadata);
    }
}
