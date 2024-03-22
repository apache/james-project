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

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;

import org.apache.james.jmap.draft.model.message.view.MessageFullViewFactory.MetaDataWithContent;
import org.apache.james.jmap.draft.send.MailMetadata;
import org.apache.james.jmap.draft.send.MailSpool;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.server.core.Envelope;
import org.apache.james.server.core.MailImpl;
import org.apache.james.server.core.MimeMessageSource;
import org.apache.mailet.Mail;

import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class MessageSender {
    public static class MessageMimeMessageSource implements MimeMessageSource {
        private final String id;
        private final MetaDataWithContent message;

        public MessageMimeMessageSource(String id, MetaDataWithContent message) {
            this.id = id;
            this.message = message;
        }

        @Override
        public String getSourceId() {
            return id;
        }

        @Override
        public InputStream getInputStream() {
            return message.getContent();
        }

        @Override
        public long getMessageSize() {
            return message.getSize();
        }
    }

    private final MailSpool mailSpool;

    @Inject
    public MessageSender(MailSpool mailSpool) {
        this.mailSpool = mailSpool;
    }

    public Mono<Void> sendMessage(MetaDataWithContent message, Envelope envelope, MailboxSession session) {
        return Mono.usingWhen(Mono.fromCallable(() -> buildMail(message, envelope)),
            mail -> sendMessage(message.getMessageId(), mail, session),
            mail -> Mono.fromRunnable(() -> LifecycleUtil.dispose(mail)).subscribeOn(Schedulers.boundedElastic()));
    }

    @VisibleForTesting
    static Mail buildMail(MetaDataWithContent message, Envelope envelope) throws MessagingException {
        String name = message.getMessageId().serialize();
        MailImpl mail = MailImpl.builder()
            .name(name)
            .sender(envelope.getFrom().asOptional().orElseThrow(() -> new RuntimeException("Sender is mandatory")))
            .addRecipients(envelope.getRecipients())
            .build();
        mail.setMessageContent(new MessageMimeMessageSource(name, message));
        return mail;
    }

    public Mono<Void> sendMessage(MessageId messageId, Mail mail, MailboxSession session) {
        MailMetadata metadata = new MailMetadata(messageId, session.getUser().asString());
        return mailSpool.send(mail, metadata);
    }
}
