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

import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.Store;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.queue.api.MailQueue;
import org.apache.mailet.Mail;

class MailLoader {
    private final Store<MimeMessage, MimeMessagePartsId> mimeMessageStore;
    private final BlobId.Factory blobIdFactory;

    MailLoader(Store<MimeMessage, MimeMessagePartsId> mimeMessageStore, BlobId.Factory blobIdFactory) {
        this.mimeMessageStore = mimeMessageStore;
        this.blobIdFactory = blobIdFactory;
    }

    Pair<EnQueueId, Mail> load(MailReferenceDTO dto) throws MailQueue.MailQueueException {
        try {
            MailReference mailReference = dto.toMailReference(blobIdFactory);

            Mail mail = mailReference.getMail();
            MimeMessage mimeMessage = mimeMessageStore.read(mailReference.getPartsId()).block();
            mail.setMessage(mimeMessage);
            return Pair.of(mailReference.getEnQueueId(), mail);
        } catch (AddressException e) {
            throw new MailQueue.MailQueueException("Failed to parse mail address", e);
        } catch (MessagingException e) {
            throw new MailQueue.MailQueueException("Failed to generate mime message", e);
        }
    }
}
