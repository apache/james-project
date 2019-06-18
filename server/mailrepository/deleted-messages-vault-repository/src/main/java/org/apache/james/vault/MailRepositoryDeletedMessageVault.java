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

package org.apache.james.vault;

import java.io.InputStream;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import javax.inject.Inject;
import javax.mail.MessagingException;

import org.apache.james.core.User;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.server.core.MimeMessageInputStream;
import org.apache.james.task.Task;
import org.apache.james.util.streams.Iterators;
import org.apache.james.vault.search.Query;
import org.apache.james.vault.utils.DeleteByQueryExecutor;
import org.apache.james.vault.utils.VaultGarbageCollectionTask;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class MailRepositoryDeletedMessageVault implements DeletedMessageVault {
    public static class Configuration {
        public static Configuration from(org.apache.commons.configuration.Configuration propertiesConfiguration) {
            return new Configuration(
                MailRepositoryUrl.from(propertiesConfiguration.getString("urlPrefix")));
        }

        private final MailRepositoryUrl urlPrefix;

        public Configuration(MailRepositoryUrl urlPrefix) {
            this.urlPrefix = urlPrefix;
        }
    }

    private final MailRepositoryStore mailRepositoryStore;
    private final RetentionConfiguration retentionConfiguration;
    private final Configuration configuration;
    private final MailConverter mailConverter;
    private final DeleteByQueryExecutor deleteByQueryExecutor;
    private final Clock clock;

    @Inject
    MailRepositoryDeletedMessageVault(MailRepositoryStore mailRepositoryStore, RetentionConfiguration retentionConfiguration, Configuration configuration, MailConverter mailConverter, Clock clock) {
        this.retentionConfiguration = retentionConfiguration;
        this.clock = clock;
        this.deleteByQueryExecutor = new DeleteByQueryExecutor(this);
        this.mailRepositoryStore = mailRepositoryStore;
        this.configuration = configuration;
        this.mailConverter = mailConverter;
    }

    @Override
    public Publisher<Void> append(User user, DeletedMessage deletedMessage, InputStream inputStream) {
        Preconditions.checkNotNull(user);
        Preconditions.checkNotNull(deletedMessage);
        Preconditions.checkNotNull(inputStream);

        MailRepository mailRepository = repositoryForUser(user);

        return Mono.fromCallable(() -> mailRepository.store(mailConverter.toMail(deletedMessage, inputStream)))
            .subscribeOn(Schedulers.elastic())
            .then();
    }

    @Override
    public Publisher<Void> delete(User user, MessageId messageId) {
        Preconditions.checkNotNull(user);
        Preconditions.checkNotNull(messageId);

        MailRepository mailRepository = repositoryForUser(user);
        MailKey mailKey = new MailKey(messageId.serialize());

        return Mono.fromRunnable(Throwing.runnable(() -> mailRepository.remove(mailKey)))
            .subscribeOn(Schedulers.elastic())
            .then();
    }

    @Override
    public Publisher<InputStream> loadMimeMessage(User user, MessageId messageId) {
        Preconditions.checkNotNull(user);
        Preconditions.checkNotNull(messageId);

        MailRepository mailRepository = repositoryForUser(user);
        MailKey mailKey = new MailKey(messageId.serialize());

        return Mono.fromCallable(() -> mailRepository.retrieve(mailKey))
            .subscribeOn(Schedulers.elastic())
            .map(Throwing.function(mail -> new MimeMessageInputStream(mail.getMessage())));
    }

    @Override
    public Publisher<DeletedMessage> search(User user, Query query) {
        Preconditions.checkNotNull(user);
        Preconditions.checkNotNull(query);

        MailRepository mailRepository = repositoryForUser(user);

        try {
            return Iterators.toFlux(mailRepository.list())
                .publishOn(Schedulers.elastic())
                .map(Throwing.function(mailRepository::retrieve))
                .publishOn(Schedulers.parallel())
                .map(mailConverter::fromMail)
                .filter(query.toPredicate());
        } catch (MessagingException e) {
            return Mono.error(e);
        }
    }

    @Override
    public Publisher<User> usersWithVault() {
        return Flux.fromStream(mailRepositoryStore.getUrls()
            .filter(this::isVault)
            .map(this::userForRepository));
    }

    private boolean isVault(MailRepositoryUrl url) {
        return url.hasPrefix(configuration.urlPrefix);
    }

    private User userForRepository(MailRepositoryUrl url) {
        Preconditions.checkArgument(isVault(url));

        List<String> parts = url.getPath().parts();

        return User.fromUsername(Iterables.getLast(parts));
    }

    @Override
    public Task deleteExpiredMessagesTask() {
        ZonedDateTime now = ZonedDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        ZonedDateTime beginningOfRetentionPeriod = now.minus(retentionConfiguration.getRetentionPeriod());

        return new VaultGarbageCollectionTask(
            deleteByQueryExecutor,
            beginningOfRetentionPeriod);
    }

    private MailRepository repositoryForUser(User user) {
        MailRepositoryUrl mailRepositoryUrl = configuration.urlPrefix.subUrl(user.asString());

        try {
            return mailRepositoryStore.select(mailRepositoryUrl);
        } catch (MailRepositoryStore.MailRepositoryStoreException e) {
            throw new RuntimeException(e);
        }
    }
}
