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

package org.apache.james.mailrepository.cassandra;

import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;
import static org.apache.james.util.ReactorUtils.publishIfPresent;

import java.util.Iterator;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.blob.api.Store;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.mailrepository.cassandra.CassandraMailRepositoryMailDaoV2.MailDTO;
import org.apache.james.server.core.MailImpl;
import org.apache.james.server.core.MimeMessageWrapper;
import org.apache.james.util.AuditTrail;
import org.apache.mailet.Mail;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Mono;

public class CassandraMailRepository implements MailRepository {
    private final MailRepositoryUrl url;
    private final CassandraMailRepositoryKeysDAO keysDAO;
    private final CassandraMailRepositoryMailDaoV2 mailDAO;
    private final Store<MimeMessage, MimeMessagePartsId> mimeMessageStore;

    @Inject
    CassandraMailRepository(MailRepositoryUrl url,
                            CassandraMailRepositoryKeysDAO keysDAO,
                            CassandraMailRepositoryMailDaoV2 mailDAO,
                            MimeMessageStore.Factory mimeMessageStoreFactory) {
        this.url = url;
        this.keysDAO = keysDAO;
        this.mailDAO = mailDAO;
        this.mimeMessageStore = mimeMessageStoreFactory.mimeMessageStore();
    }

    @Override
    public MailKey store(Mail mail) throws MessagingException {
        MailKey mailKey = MailKey.forMail(mail);

        return mimeMessageStore.save(mail.getMessage())
            .flatMap(parts -> mailDAO.store(url, mail,
                parts.getHeaderBlobId(),
                parts.getBodyBlobId()))
            .then(keysDAO.store(url, mailKey))
            .doOnSuccess(Throwing.consumer(any -> AuditTrail.entry()
                .protocol("mailrepository")
                .action("store")
                .parameters(Throwing.supplier(() -> ImmutableMap.of("mailId", mail.getName(),
                    "mimeMessageId", Optional.ofNullable(mail.getMessage())
                        .map(Throwing.function(MimeMessage::getMessageID))
                        .orElse(""),
                    "url", url.asString(),
                    "error", Optional.ofNullable(mail.getErrorMessage()).orElse(""),
                    "sender", mail.getMaybeSender().asString(),
                    "recipients", StringUtils.join(mail.getRecipients()))))
                .log("CassandraMailRepository stored mail.")))
            .thenReturn(mailKey)
            .block();
    }

    @Override
    public Iterator<MailKey> list() {
        return keysDAO.list(url)
            .toIterable()
            .iterator();
    }

    @Override
    public Mail retrieve(MailKey key) {
        return mailDAO.read(url, key)
            .handle(publishIfPresent())
            .flatMap(this::toMail)
            .blockOptional()
            .orElse(null);
    }

    private Mono<Mail> toMail(MailDTO mailDTO) {
        MimeMessagePartsId parts = blobIds(mailDTO);

        return mimeMessageStore.read(parts)
            .map(Throwing.function(mimeMessage -> {
                MailImpl mail = mailDTO.getMailBuilder()
                    .build();

                if (mimeMessage instanceof MimeMessageWrapper) {
                    mail.setMessageNoCopy((MimeMessageWrapper) mimeMessage);
                } else {
                    mail.setMessage(mimeMessage);
                }
                return mail;
            }));
    }

    private MimeMessagePartsId blobIds(MailDTO mailDTO) {
        return MimeMessagePartsId.builder()
                .headerBlobId(mailDTO.getHeaderBlobId())
                .bodyBlobId(mailDTO.getBodyBlobId())
                .build();
    }

    @Override
    public void remove(MailKey key) {
        removeAsync(key).block();
    }

    private Mono<Void> removeAsync(MailKey key) {
        return mailDAO.read(url, key)
            .flatMap(maybeMailDTO ->
                keysDAO.remove(url, key)
                    .then(mailDAO.remove(url, key))
                    .then(deleteBlobs(maybeMailDTO)));
    }

    private Mono<Void> deleteBlobs(Optional<MailDTO> maybeMailDTO) {
        return Mono.justOrEmpty(maybeMailDTO)
            .flatMap(mailDTO -> Mono.from(mimeMessageStore.delete(blobIds(mailDTO))));
    }


    @Override
    public long size() {
        return keysDAO.getCount(url).block();
    }

    @Override
    public Publisher<Long> sizeReactive() {
        return keysDAO.getCount(url);
    }

    @Override
    public void removeAll() {
        keysDAO.list(url)
            .flatMap(this::removeAsync, DEFAULT_CONCURRENCY)
            .then()
            .block();
    }
}
