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

package org.apache.james.webadmin.service;

import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;

import javax.inject.Inject;
import javax.mail.MessagingException;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.util.streams.Iterators;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

public class ReprocessingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReprocessingService.class);

    public static class MissingKeyException extends RuntimeException {
        MissingKeyException(MailKey key) {
            super(key.asString() + " can not be found");
        }
    }

    public static class Configuration {
        private final MailQueueName mailQueueName;
        private final Optional<String> targetProcessor;
        private final boolean consume;

        public Configuration(MailQueueName mailQueueName, Optional<String> targetProcessor, boolean consume) {
            this.mailQueueName = mailQueueName;
            this.targetProcessor = targetProcessor;
            this.consume = consume;
        }

        public MailQueueName getMailQueueName() {
            return mailQueueName;
        }

        public Optional<String> getTargetProcessor() {
            return targetProcessor;
        }

        public boolean isConsume() {
            return consume;
        }
    }

    static class Reprocessor implements Closeable {
        private final MailQueue mailQueue;
        private final Configuration configuration;

        Reprocessor(MailQueue mailQueue, Configuration configuration) {
            this.mailQueue = mailQueue;
            this.configuration = configuration;
        }

        private void reprocess(MailRepository repository, Mail mail, MailKey key) {
            try {
                configuration.getTargetProcessor().ifPresent(mail::setState);
                mailQueue.enQueue(mail);
                if (configuration.isConsume()) {
                    repository.remove(key);
                }
            } catch (Exception e) {
                throw new RuntimeException("Error encountered while reprocessing mail " + mail.getName(), e);
            } finally {
                LifecycleUtil.dispose(mail);
            }
        }

        @Override
        public void close() {
            try {
                mailQueue.close();
            } catch (IOException e) {
                LOGGER.debug("error closing queue", e);
            }
        }
    }

    private final MailQueueFactory<?> mailQueueFactory;
    private final MailRepositoryStoreService mailRepositoryStoreService;

    @Inject
    public ReprocessingService(MailQueueFactory<?> mailQueueFactory,
                               MailRepositoryStoreService mailRepositoryStoreService) {
        this.mailQueueFactory = mailQueueFactory;
        this.mailRepositoryStoreService = mailRepositoryStoreService;
    }

    public void reprocessAll(MailRepositoryPath path, Configuration configuration, Consumer<MailKey> keyListener) throws MailRepositoryStore.MailRepositoryStoreException, MessagingException {
        try (Reprocessor reprocessor = new Reprocessor(getMailQueue(configuration.getMailQueueName()), configuration)) {
            mailRepositoryStoreService
                .getRepositories(path)
                .forEach(Throwing.consumer((MailRepository repository) ->
                    Iterators.toStream(repository.list())
                        .peek(keyListener)
                        .forEach(Throwing.consumer(key ->
                                Optional.ofNullable(repository.retrieve(key))
                                        .ifPresent(mail -> reprocessor.reprocess(repository, mail, key))
                        ))
                ));
        }
    }

    public void reprocess(MailRepositoryPath path, MailKey key, Configuration configuration) throws MailRepositoryStore.MailRepositoryStoreException, MessagingException {
        try (Reprocessor reprocessor = new Reprocessor(getMailQueue(configuration.getMailQueueName()), configuration)) {
            Pair<MailRepository, Mail> mailPair = mailRepositoryStoreService
                .getRepositories(path)
                .map(Throwing.function(repository -> Pair.of(repository, Optional.ofNullable(repository.retrieve(key)))))
                .filter(pair -> pair.getRight().isPresent())
                .map(pair -> Pair.of(pair.getLeft(), pair.getRight().get()))
                .findFirst()
                .orElseThrow(() -> new MissingKeyException(key));

            reprocessor.reprocess(mailPair.getKey(), mailPair.getValue(), key);
        }
    }

    private MailQueue getMailQueue(MailQueueName targetQueue) {
        return mailQueueFactory.getQueue(targetQueue)
            .orElseThrow(() -> new RuntimeException("Can not find queue " + targetQueue.asString()));
    }
}
