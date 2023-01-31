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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.mailrepository.api.Protocol;
import org.apache.james.mailrepository.memory.MailRepositoryStoreConfiguration;
import org.apache.james.mailrepository.memory.MemoryMailRepository;
import org.apache.james.mailrepository.memory.MemoryMailRepositoryStore;
import org.apache.james.mailrepository.memory.MemoryMailRepositoryUrlStore;
import org.apache.james.mailrepository.memory.SimpleMailRepositoryLoader;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.apache.james.queue.memory.MemoryMailQueueFactory;
import org.apache.james.util.MimeMessageUtil;
import org.apache.james.util.streams.Limit;
import org.apache.james.webadmin.service.ReprocessingService.Configuration;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.consumers.ConsumerChainer;
import com.google.common.collect.ImmutableList;

class ReprocessingServiceTest {
    private static final String MEMORY_PROTOCOL = "memory";
    private static final MailRepositoryPath PATH = MailRepositoryPath.from("path");
    private static final String NAME_1 = "key-1";
    private static final String NAME_2 = "key-2";
    private static final String NAME_3 = "key-3";
    private static final MailKey KEY_1 = new MailKey(NAME_1);
    private static final MailKey KEY_2 = new MailKey(NAME_2);
    private static final MailKey KEY_3 = new MailKey(NAME_3);
    private static final MailQueueName SPOOL = MailQueueName.of("spool");
    private static final Consumer<MailKey> NOOP_CONSUMER = key -> { };
    private static final Optional<String> NO_TARGET_PROCESSOR = Optional.empty();
    private static final Optional<Integer> NO_MAX_RETRIES = Optional.empty();
    private static final byte[] MESSAGE_BYTES = "header: value \r\n".getBytes(UTF_8);
    public static final boolean CONSUME = true;

    private ReprocessingService reprocessingService;
    private MemoryMailRepositoryStore mailRepositoryStore;
    private MailQueueFactory<? extends ManageableMailQueue> queueFactory;
    private FakeMail mail1;
    private FakeMail mail2;
    private FakeMail mail3;

    @BeforeEach
    void setUp() throws Exception {
        mailRepositoryStore = createMemoryMailRepositoryStore();

        queueFactory = new MemoryMailQueueFactory(new RawMailQueueItemDecoratorFactory());
        reprocessingService = new ReprocessingService(
            queueFactory,
            new MailRepositoryStoreService(mailRepositoryStore));

        queueFactory.createQueue(SPOOL);

        mail1 = FakeMail.builder()
            .name(NAME_1)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build();

        mail2 = FakeMail.builder()
            .name(NAME_2)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build();

        mail3 = FakeMail.builder()
            .name(NAME_3)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes(MESSAGE_BYTES))
            .build();
    }

    @Test
    void reprocessingOneShouldEnqueueMail() throws Exception {
        MailRepository repository = mailRepositoryStore.select(MailRepositoryUrl.fromPathAndProtocol(PATH, MEMORY_PROTOCOL));
        repository.store(mail1);
        repository.store(mail2);
        repository.store(mail3);

        reprocessingService.reprocess(PATH, KEY_2, new ReprocessingService.Configuration(SPOOL, NO_TARGET_PROCESSOR, Optional.empty(), CONSUME, Limit.unlimited()));

        assertThat(queueFactory.getQueue(SPOOL).get().browse())
            .toIterable()
            .extracting(item -> item.getMail().getName())
            .containsOnly(NAME_2);
    }

    @Test
    void reprocessingOneShouldRemoveMailFromRepository() throws Exception {
        MailRepository repository = mailRepositoryStore.select(MailRepositoryUrl.fromPathAndProtocol(PATH, MEMORY_PROTOCOL));
        repository.store(mail1);
        repository.store(mail2);
        repository.store(mail3);

        reprocessingService.reprocess(PATH, KEY_2, new ReprocessingService.Configuration(SPOOL, NO_TARGET_PROCESSOR, Optional.empty(), CONSUME, Limit.unlimited()));

        assertThat(repository.list()).toIterable()
            .containsOnly(KEY_1, KEY_3);
    }

    @Test
    void reprocessingShouldEmptyRepository() throws Exception {
        MailRepository repository = mailRepositoryStore.select(MailRepositoryUrl.fromPathAndProtocol(PATH, MEMORY_PROTOCOL));
        repository.store(mail1);
        repository.store(mail2);
        repository.store(mail3);

        reprocessingService.reprocessAll(PATH, new Configuration(SPOOL, NO_TARGET_PROCESSOR, NO_MAX_RETRIES, CONSUME, Limit.unlimited()), NOOP_CONSUMER).block();

        assertThat(repository.list()).toIterable()
            .isEmpty();
    }

    @Test
    void reprocessingShouldEnqueueAllMails() throws Exception {
        MailRepository repository = mailRepositoryStore.select(MailRepositoryUrl.fromPathAndProtocol(PATH, MEMORY_PROTOCOL));
        repository.store(mail1);
        repository.store(mail2);
        repository.store(mail3);

        reprocessingService.reprocessAll(PATH, new Configuration(SPOOL, NO_TARGET_PROCESSOR, NO_MAX_RETRIES, CONSUME, Limit.unlimited()), NOOP_CONSUMER).block();

        assertThat(queueFactory.getQueue(SPOOL).get().browse())
            .toIterable()
            .extracting(item -> item.getMail().getName())
            .containsOnly(NAME_1, NAME_2, NAME_3);
    }

    @Test
    void reprocessingShouldSupportMaxRetries() throws Exception {
        MailRepository repository = mailRepositoryStore.select(MailRepositoryUrl.fromPathAndProtocol(PATH, MEMORY_PROTOCOL));
        mail1.setAttribute(new Attribute(AttributeName.of("mailRepository-reprocessing"), AttributeValue.of(1)));
        repository.store(mail1);
        mail2.setAttribute(new Attribute(AttributeName.of("mailRepository-reprocessing"), AttributeValue.of(2)));
        repository.store(mail2);
        mail3.setAttribute(new Attribute(AttributeName.of("mailRepository-reprocessing"), AttributeValue.of(3)));
        repository.store(mail3);

        Optional<Integer> maxRetries = Optional.of(2);
        reprocessingService.reprocessAll(PATH, new Configuration(SPOOL, NO_TARGET_PROCESSOR, maxRetries, CONSUME, Limit.unlimited()), NOOP_CONSUMER).block();

        assertThat(queueFactory.getQueue(SPOOL).get().browse())
            .toIterable()
            .extracting(item -> item.getMail().getName())
            .containsOnly(NAME_1);
    }

    @Test
    void reprocessingShouldCombineMaxRetriesAndLimit() throws Exception {
        MailRepository repository = mailRepositoryStore.select(MailRepositoryUrl.fromPathAndProtocol(PATH, MEMORY_PROTOCOL));
        mail1.setAttribute(new Attribute(AttributeName.of("mailRepository-reprocessing"), AttributeValue.of(3)));
        repository.store(mail1);
        mail2.setAttribute(new Attribute(AttributeName.of("mailRepository-reprocessing"), AttributeValue.of(2)));
        repository.store(mail2);
        mail3.setAttribute(new Attribute(AttributeName.of("mailRepository-reprocessing"), AttributeValue.of(1)));
        repository.store(mail3);

        Optional<Integer> maxRetries = Optional.of(2);
        reprocessingService.reprocessAll(PATH, new Configuration(SPOOL, NO_TARGET_PROCESSOR, maxRetries, CONSUME, Limit.limit(2)), NOOP_CONSUMER).block();

        assertThat(queueFactory.getQueue(SPOOL).get().browse())
            .toIterable()
            .extracting(item -> item.getMail().getName())
            .containsOnly(NAME_3);
    }

    @Test
    void reprocessingShouldSetRetries() throws Exception {
        MailRepository repository = mailRepositoryStore.select(MailRepositoryUrl.fromPathAndProtocol(PATH, MEMORY_PROTOCOL));
        repository.store(mail1);

        reprocessingService.reprocessAll(PATH, new Configuration(SPOOL, NO_TARGET_PROCESSOR, NO_MAX_RETRIES, CONSUME, Limit.unlimited()), NOOP_CONSUMER).block();

        assertThat(queueFactory.getQueue(SPOOL).get().browse())
            .toIterable()
            .extracting(item -> (int) item.getMail().getAttribute(AttributeName.of("mailRepository-reprocessing")).get().getValue().getValue())
            .containsOnly(1);
    }

    @Test
    void reprocessingShouldIncrementRetries() throws Exception {
        MailRepository repository = mailRepositoryStore.select(MailRepositoryUrl.fromPathAndProtocol(PATH, MEMORY_PROTOCOL));
        mail1.setAttribute(new Attribute(AttributeName.of("mailRepository-reprocessing"), AttributeValue.of(1)));
        repository.store(mail1);

        reprocessingService.reprocessAll(PATH, new Configuration(SPOOL, NO_TARGET_PROCESSOR, NO_MAX_RETRIES, CONSUME, Limit.unlimited()), NOOP_CONSUMER).block();

        assertThat(queueFactory.getQueue(SPOOL).get().browse())
            .toIterable()
            .extracting(item -> (int) item.getMail().getAttribute(AttributeName.of("mailRepository-reprocessing")).get().getValue().getValue())
            .containsOnly(2);
    }

    @Test
    void reprocessingShouldNotFailOnConcurrentDeletion() throws Exception {
        MailRepository repository = mailRepositoryStore.select(MailRepositoryUrl.fromPathAndProtocol(PATH, MEMORY_PROTOCOL));
        repository.store(mail1);
        repository.store(mail2);
        repository.store(mail3);

        AtomicBoolean shouldPerformRemove = new AtomicBoolean(true);
        ConsumerChainer<MailKey> concurrentRemoveConsumer = Throwing.consumer(key -> {
            if (shouldPerformRemove.get()) {
                shouldPerformRemove.set(false);

                MailKey toRemove = ImmutableList.of(NAME_1, NAME_2, NAME_3)
                    .stream()
                    .map(MailKey::new)
                    .filter(candidateForRemoval -> !candidateForRemoval.equals(key))
                    .findFirst()
                    .get();

                repository.remove(toRemove);
            }
        });

        reprocessingService.reprocessAll(PATH, new ReprocessingService.Configuration(SPOOL, NO_TARGET_PROCESSOR, NO_MAX_RETRIES, CONSUME, Limit.unlimited()), concurrentRemoveConsumer).block();

        assertThat(queueFactory.getQueue(SPOOL).get().browse())
            .toIterable()
            .hasSize(2);
    }

    private MemoryMailRepositoryStore createMemoryMailRepositoryStore() throws Exception {
        MemoryMailRepositoryUrlStore urlStore = new MemoryMailRepositoryUrlStore();
        MailRepositoryStoreConfiguration configuration = MailRepositoryStoreConfiguration.forItems(
            new MailRepositoryStoreConfiguration.Item(
                ImmutableList.of(new Protocol("memory")),
                MemoryMailRepository.class.getName(),
                new BaseHierarchicalConfiguration()));

        MemoryMailRepositoryStore mailRepositoryStore = new MemoryMailRepositoryStore(urlStore, new SimpleMailRepositoryLoader(), configuration);
        mailRepositoryStore.init();
        return mailRepositoryStore;
    }

}