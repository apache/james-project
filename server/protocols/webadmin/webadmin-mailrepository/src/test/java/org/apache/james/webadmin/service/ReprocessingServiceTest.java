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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.mailrepository.api.Protocol;
import org.apache.james.mailrepository.memory.MailRepositoryStoreConfiguration;
import org.apache.james.mailrepository.memory.MemoryMailRepository;
import org.apache.james.mailrepository.memory.MemoryMailRepositoryProvider;
import org.apache.james.mailrepository.memory.MemoryMailRepositoryStore;
import org.apache.james.mailrepository.memory.MemoryMailRepositoryUrlStore;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.apache.james.queue.memory.MemoryMailQueueFactory;
import org.apache.mailet.base.test.FakeMail;
import org.junit.Before;
import org.junit.Test;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.consumers.ConsumerChainer;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

public class ReprocessingServiceTest {
    private static final String MEMORY_PROTOCOL = "memory";
    private static final MailRepositoryPath PATH = MailRepositoryPath.from("path");
    private static final String NAME_1 = "key-1";
    private static final String NAME_2 = "key-2";
    private static final String NAME_3 = "key-3";
    private static final MailKey KEY_1 = new MailKey(NAME_1);
    private static final MailKey KEY_2 = new MailKey(NAME_2);
    private static final MailKey KEY_3 = new MailKey(NAME_3);
    private static final String SPOOL = "spool";
    private static final Consumer<MailKey> NOOP_CONSUMER = key -> { };
    private static final Optional<String> NO_TARGET_PROCESSOR = Optional.empty();

    private ReprocessingService reprocessingService;
    private MemoryMailRepositoryStore mailRepositoryStore;
    private MailQueueFactory<ManageableMailQueue> queueFactory;
    private FakeMail mail1;
    private FakeMail mail2;
    private FakeMail mail3;

    @Before
    public void setUp() throws Exception {
        mailRepositoryStore = createMemoryMailRepositoryStore();

        queueFactory = new MemoryMailQueueFactory(new RawMailQueueItemDecoratorFactory());
        reprocessingService = new ReprocessingService(
            queueFactory,
            new MailRepositoryStoreService(mailRepositoryStore));

        queueFactory.createQueue(SPOOL);

        mail1 = FakeMail.builder()
            .name(NAME_1)
            .build();

        mail2 = FakeMail.builder()
            .name(NAME_2)
            .build();

        mail3 = FakeMail.builder()
            .name(NAME_3)
            .build();
    }

    @Test
    public void reprocessingOneShouldEnqueueMail() throws Exception {
        MailRepository repository = mailRepositoryStore.select(MailRepositoryUrl.fromPathAndProtocol(PATH, MEMORY_PROTOCOL));
        repository.store(mail1);
        repository.store(mail2);
        repository.store(mail3);

        reprocessingService.reprocess(PATH, KEY_2, NO_TARGET_PROCESSOR, SPOOL);

        assertThat(queueFactory.getQueue(SPOOL).get()
            .browse())
            .extracting(item -> item.getMail().getName())
            .containsOnly(NAME_2);
    }

    @Test
    public void reprocessingOneShouldRemoveMailFromRepository() throws Exception {
        MailRepository repository = mailRepositoryStore.select(MailRepositoryUrl.fromPathAndProtocol(PATH, MEMORY_PROTOCOL));
        repository.store(mail1);
        repository.store(mail2);
        repository.store(mail3);

        reprocessingService.reprocess(PATH, KEY_2, NO_TARGET_PROCESSOR, SPOOL);

        assertThat(repository.list()).containsOnly(KEY_1, KEY_3);
    }

    @Test
    public void reprocessingShouldEmptyRepository() throws Exception {
        MailRepository repository = mailRepositoryStore.select(MailRepositoryUrl.fromPathAndProtocol(PATH, MEMORY_PROTOCOL));
        repository.store(mail1);
        repository.store(mail2);
        repository.store(mail3);

        reprocessingService.reprocessAll(PATH, NO_TARGET_PROCESSOR, SPOOL, NOOP_CONSUMER);

        assertThat(repository.list()).isEmpty();
    }

    @Test
    public void reprocessingShouldEnqueueAllMails() throws Exception {
        MailRepository repository = mailRepositoryStore.select(MailRepositoryUrl.fromPathAndProtocol(PATH, MEMORY_PROTOCOL));
        repository.store(mail1);
        repository.store(mail2);
        repository.store(mail3);

        reprocessingService.reprocessAll(PATH, NO_TARGET_PROCESSOR, SPOOL, NOOP_CONSUMER);

        assertThat(queueFactory.getQueue(SPOOL).get()
            .browse())
            .extracting(item -> item.getMail().getName())
            .containsOnly(NAME_1, NAME_2, NAME_3);
    }

    @Test
    public void reprocessingShouldNotFailOnConcurrentDeletion() throws Exception {
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

        reprocessingService.reprocessAll(PATH, NO_TARGET_PROCESSOR, SPOOL, concurrentRemoveConsumer);

        assertThat(queueFactory.getQueue(SPOOL).get()
            .browse())
            .hasSize(2);
    }

    private MemoryMailRepositoryStore createMemoryMailRepositoryStore() throws Exception {
        MemoryMailRepositoryUrlStore urlStore = new MemoryMailRepositoryUrlStore();
        MailRepositoryStoreConfiguration configuration = MailRepositoryStoreConfiguration.forItems(
            new MailRepositoryStoreConfiguration.Item(
                ImmutableList.of(new Protocol("memory")),
                MemoryMailRepository.class.getName(),
                new HierarchicalConfiguration()));

        MemoryMailRepositoryStore mailRepositoryStore = new MemoryMailRepositoryStore(urlStore, Sets.newHashSet(new MemoryMailRepositoryProvider()), configuration);
        mailRepositoryStore.init();
        return mailRepositoryStore;
    }

}