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

package org.apache.james.jmap.draft.methods;

import java.io.InputStream;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.jmap.draft.model.message.view.MessageFullViewFactory.MetaDataWithContent;
import org.apache.james.jmap.draft.send.MailMetadata;
import org.apache.james.jmap.draft.send.MailSpool;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.server.core.Envelope;
import org.apache.james.server.core.MailImpl;
import org.apache.james.server.core.MimeMessageInputStreamSource;
import org.apache.james.server.core.MimeMessageSource;
import org.apache.james.server.core.MimeMessageWrapper;
import org.apache.mailet.Mail;

import com.google.common.annotations.VisibleForTesting;

public class MessageSender {
    private final MailSpool mailSpool;

    @Inject
    public MessageSender(MailSpool mailSpool) {
        this.mailSpool = mailSpool;
    }

    public void sendMessage(MetaDataWithContent message,
                            Envelope envelope,
                            MailboxSession session) throws MessagingException {
        Mail mail = buildMail(message, envelope);
        try {
            sendMessage(message.getMessageId(), mail, session);
        } finally {
            LifecycleUtil.dispose(mail);
        }
    }

    @VisibleForTesting
    static Mail buildMail(MetaDataWithContent message, Envelope envelope) throws MessagingException {
        String name = message.getMessageId().serialize();
        MailImpl mail = MailImpl.builder()
            .name(name)
            .sender(envelope.getFrom().asOptional().orElseThrow(() -> new RuntimeException("Sender is mandatory")))
            .addRecipients(envelope.getRecipients())
            .build();
        mail.setMessageContent(new MimeMessageInputStreamSource(name, message.getContent()));
        return mail;
    }

    public void sendMessage(MessageId messageId,
                            Mail mail,
                            MailboxSession session) throws MessagingException {
        MailMetadata metadata = new MailMetadata(messageId, session.getUser().asString());
        mailSpool.send(mail, metadata);
    }
}
