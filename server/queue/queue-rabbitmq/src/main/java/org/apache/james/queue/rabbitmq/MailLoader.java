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

package org.apache.james.queue.rabbitmq;

import java.util.function.Function;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.Store;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.server.core.MailImpl;
import org.apache.james.server.core.MimeMessageWrapper;
import org.apache.mailet.Mail;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Mono;

class MailLoader {
    private final Store<MimeMessage, MimeMessagePartsId> mimeMessageStore;
    private final BlobId.Factory blobIdFactory;

    MailLoader(Store<MimeMessage, MimeMessagePartsId> mimeMessageStore, BlobId.Factory blobIdFactory) {
        this.mimeMessageStore = mimeMessageStore;
        this.blobIdFactory = blobIdFactory;
    }

    Mono<MailWithEnqueueId> load(MailReferenceDTO dto) {
        return Mono.fromCallable(() -> dto.toMailReference(blobIdFactory))
            .flatMap(mailReference -> buildMail(mailReference)
                .map(mail -> new MailWithEnqueueId(
                    mailReference.getEnqueueId(),
                    mail,
                    mailReference.getPartsId())));
    }

    private Mono<Mail> buildMail(MailReference mailReference) {
        return mimeMessageStore.read(mailReference.getPartsId())
            .flatMap(mimeMessage -> buildMailWithMessageReference(mailReference, mimeMessage));
    }

    private Mono<Mail> buildMailWithMessageReference(MailReference mailReference, MimeMessage mimeMessage) {
        Function<Mail, Mono<Object>> setMessage = mail ->
            Mono.fromRunnable(Throwing.runnable(() -> {
                if (mimeMessage instanceof MimeMessageWrapper && mail instanceof MailImpl) {
                    MailImpl mailImpl = (MailImpl) mail;
                    mailImpl.setMessageNoCopy((MimeMessageWrapper) mimeMessage);
                } else {
                    mail.setMessage(mimeMessage);
                }
            }).sneakyThrow())
                .onErrorResume(AddressException.class, e -> Mono.error(new MailQueue.MailQueueException("Failed to parse mail address", e)))
                .onErrorResume(MessagingException.class, e -> Mono.error(new MailQueue.MailQueueException("Failed to generate mime message", e)));

        return Mono.just(mailReference.getMail())
            .flatMap(mail -> setMessage.apply(mail)
                .thenReturn(mail));
    }
}
