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

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.blob.api.Store;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.util.CompletableFutureUtil;
import org.apache.james.util.FluentFutureStream;
import org.apache.mailet.Mail;

import com.github.fge.lambdas.Throwing;

public class CassandraMailRepository implements MailRepository {

    private final MailRepositoryUrl url;
    private final CassandraMailRepositoryKeysDAO keysDAO;
    private final CassandraMailRepositoryCountDAO countDAO;
    private final CassandraMailRepositoryMailDAO mailDAO;
    private final Store<MimeMessage, MimeMessagePartsId> mimeMessageStore;

    public CassandraMailRepository(MailRepositoryUrl url, CassandraMailRepositoryKeysDAO keysDAO,
                                   CassandraMailRepositoryCountDAO countDAO, CassandraMailRepositoryMailDAO mailDAO,
                                   Store<MimeMessage, MimeMessagePartsId> mimeMessageStore) {
        this.url = url;
        this.keysDAO = keysDAO;
        this.countDAO = countDAO;
        this.mailDAO = mailDAO;
        this.mimeMessageStore = mimeMessageStore;
    }

    @Override
    public MailKey store(Mail mail) throws MessagingException {
        MailKey mailKey = MailKey.forMail(mail);

        mimeMessageStore.save(mail.getMessage())
            .thenCompose(Throwing.function(parts -> mailDAO.store(url, mail,
                parts.getHeaderBlobId(),
                parts.getBodyBlobId())))
            .thenCompose(any -> keysDAO.store(url, mailKey))
            .thenCompose(this::increaseSizeIfStored)
            .join();

        return mailKey;
    }

    private CompletionStage<Void> increaseSizeIfStored(Boolean isStored) {
        if (isStored) {
            return countDAO.increment(url);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Iterator<MailKey> list() {
        return keysDAO.list(url)
            .join()
            .iterator();
    }

    @Override
    public Mail retrieve(MailKey key) {
        return CompletableFutureUtil
            .unwrap(mailDAO.read(url, key)
                .thenApply(optional -> optional.map(this::toMail)))
            .join()
            .orElse(null);
    }

    private CompletableFuture<Mail> toMail(CassandraMailRepositoryMailDAO.MailDTO mailDTO) {
        MimeMessagePartsId parts = MimeMessagePartsId.builder()
            .headerBlobId(mailDTO.getHeaderBlobId())
            .bodyBlobId(mailDTO.getBodyBlobId())
            .build();

        return mimeMessageStore.read(parts)
            .thenApply(mimeMessage -> mailDTO.getMailBuilder()
                .mimeMessage(mimeMessage)
                .build());
    }

    @Override
    public void remove(Mail mail) {
        removeAsync(MailKey.forMail(mail)).join();
    }

    @Override
    public void remove(Collection<Mail> toRemove) {
        FluentFutureStream.of(toRemove.stream()
            .map(MailKey::forMail)
            .map(this::removeAsync))
            .join();
    }

    @Override
    public void remove(MailKey key) {
        removeAsync(key).join();
    }

    private CompletableFuture<Void> removeAsync(MailKey key) {
        return keysDAO.remove(url, key)
            .thenCompose(this::decreaseSizeIfDeleted)
            .thenCompose(any -> mailDAO.remove(url, key));
    }

    private CompletionStage<Void> decreaseSizeIfDeleted(Boolean isDeleted) {
        if (isDeleted) {
            return countDAO.decrement(url);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public long size() {
        return countDAO.getCount(url).join();
    }

    @Override
    public void removeAll() {
        keysDAO.list(url)
            .thenCompose(stream -> FluentFutureStream.of(stream.map(this::removeAsync))
                .completableFuture())
            .join();
    }

    @Override
    public boolean lock(MailKey key) {
        return false;
    }

    @Override
    public boolean unlock(MailKey key) {
        return false;
    }
}
