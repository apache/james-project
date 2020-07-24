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

import static org.apache.james.util.ReactorUtils.publishIfPresent;

import java.util.Collection;
import java.util.Iterator;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.blob.api.Store;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.mailrepository.cassandra.CassandraMailRepositoryMailDaoAPI.MailDTO;
import org.apache.mailet.Mail;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraMailRepository implements MailRepository {
    private final MailRepositoryUrl url;
    private final CassandraMailRepositoryKeysDAO keysDAO;
    private final CassandraMailRepositoryCountDAO countDAO;
    private final CassandraMailRepositoryMailDaoAPI mailDAO;
    private final Store<MimeMessage, MimeMessagePartsId> mimeMessageStore;

    @Inject
    CassandraMailRepository(MailRepositoryUrl url, CassandraMailRepositoryKeysDAO keysDAO,
                            CassandraMailRepositoryCountDAO countDAO, CassandraMailRepositoryMailDaoAPI mailDAO,
                            MimeMessageStore.Factory mimeMessageStoreFactory) {
        this.url = url;
        this.keysDAO = keysDAO;
        this.countDAO = countDAO;
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
            .flatMap(this::increaseSizeIfStored)
            .thenReturn(mailKey)
            .block();
    }

    private Mono<Void> increaseSizeIfStored(Boolean isStored) {
        if (isStored) {
            return countDAO.increment(url);
        }
        return Mono.empty();
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
        MimeMessagePartsId parts = MimeMessagePartsId.builder()
            .headerBlobId(mailDTO.getHeaderBlobId())
            .bodyBlobId(mailDTO.getBodyBlobId())
            .build();

        return mimeMessageStore.read(parts)
            .map(mimeMessage -> mailDTO.getMailBuilder()
                .mimeMessage(mimeMessage)
                .build());
    }

    @Override
    public void remove(Mail mail) {
        removeAsync(MailKey.forMail(mail)).block();
    }

    @Override
    public void remove(Collection<Mail> toRemove) {
        Flux.fromIterable(toRemove)
            .map(MailKey::forMail)
            .flatMap(this::removeAsync)
            .then()
            .block();
    }

    @Override
    public void remove(MailKey key) {
        removeAsync(key).block();
    }

    private Mono<Void> removeAsync(MailKey key) {
        return keysDAO.remove(url, key)
            .flatMap(this::decreaseSizeIfDeleted)
            .then(mailDAO.remove(url, key));
    }

    private Mono<Void> decreaseSizeIfDeleted(Boolean isDeleted) {
        if (isDeleted) {
            return countDAO.decrement(url);
        }
        return Mono.empty();
    }

    @Override
    public long size() {
        return countDAO.getCount(url).block();
    }

    @Override
    public void removeAll() {
        keysDAO.list(url)
            .flatMap(this::removeAsync)
            .then()
            .block();
    }
}
