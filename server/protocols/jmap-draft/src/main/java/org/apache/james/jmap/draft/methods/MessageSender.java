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

import org.apache.james.jmap.draft.model.MessageViewFactory;
import org.apache.james.jmap.draft.send.MailMetadata;
import org.apache.james.jmap.draft.send.MailSpool;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.server.core.Envelope;
import org.apache.james.server.core.MailImpl;
import org.apache.james.server.core.MimeMessageCopyOnWriteProxy;
import org.apache.james.server.core.MimeMessageInputStreamSource;
import org.apache.james.server.core.MimeMessageSource;
import org.apache.mailet.Mail;

import com.google.common.annotations.VisibleForTesting;

public class MessageSender {
    private final MailSpool mailSpool;

    @Inject
    public MessageSender(MailSpool mailSpool) {
        this.mailSpool = mailSpool;
    }

    public void sendMessage(MessageViewFactory.MetaDataWithContent message,
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
    static Mail buildMail(MessageViewFactory.MetaDataWithContent message, Envelope envelope) throws MessagingException {
        String name = message.getMessageId().serialize();
        return MailImpl.builder()
            .name(name)
            .sender(envelope.getFrom().asOptional().orElseThrow(() -> new RuntimeException("Sender is mandatory")))
            .addRecipients(envelope.getRecipients())
            .mimeMessage(toMimeMessage(name, message.getContent()))
            .build();
    }

    private static MimeMessage toMimeMessage(String name, InputStream inputStream) throws MessagingException {
        MimeMessageSource source = new MimeMessageInputStreamSource(name, inputStream);
        // if MimeMessageCopyOnWriteProxy throws an error in the constructor we
        // have to manually care disposing our source.
        try {
            return new MimeMessageCopyOnWriteProxy(source);
        } catch (MessagingException e) {
            LifecycleUtil.dispose(source);
            throw e;
        }
    }

    public void sendMessage(MessageId messageId,
                            Mail mail,
                            MailboxSession session) throws MessagingException {
        MailMetadata metadata = new MailMetadata(messageId, session.getUser().asString());
        mailSpool.send(mail, metadata);
    }
}
