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

package org.apache.james.queue.pulsar;

import static org.apache.james.queue.api.Mails.defaultMail;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.james.backends.pulsar.DockerPulsarExtension;
import org.apache.james.backends.pulsar.PulsarClients;
import org.apache.james.backends.pulsar.PulsarConfiguration;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.Store;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.junit.categories.Unstable;
import org.apache.james.queue.api.DelayedMailQueueContract;
import org.apache.james.queue.api.DelayedManageableMailQueueContract;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueContract;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory;
import org.apache.james.queue.api.MailQueueMetricContract;
import org.apache.james.queue.api.MailQueueMetricExtension;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.api.ManageableMailQueueContract;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.apache.james.server.blob.deduplication.PassThroughBlobStore;
import org.apache.mailet.Mail;
import org.apache.mailet.base.MailAddressFixture;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.github.fge.lambdas.Throwing;
import com.sksamuel.pulsar4s.ConsumerMessage;

import akka.actor.ActorSystem;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.jdk.javaapi.OptionConverters;

@Tag(Unstable.TAG)
@ExtendWith(DockerPulsarExtension.class)
public class PulsarMailQueueTest implements MailQueueContract, MailQueueMetricContract, ManageableMailQueueContract, DelayedMailQueueContract, DelayedManageableMailQueueContract {

    int MAX_CONCURRENCY = 10;
    PulsarMailQueue mailQueue;

    private HashBlobId.Factory blobIdFactory;
    private Store<MimeMessage, MimeMessagePartsId> mimeMessageStore;
    private MailQueueItemDecoratorFactory factory;
    private MailQueueName mailQueueName;
    private MailQueueMetricExtension.MailQueueMetricTestSystem metricTestSystem;
    private PulsarConfiguration pulsarConfiguration;
    private PulsarClients pulsarClients;
    private ActorSystem system;
    private MemoryBlobStoreDAO memoryBlobStore;

    @BeforeEach
    void setUp(DockerPulsarExtension.DockerPulsar pulsar, MailQueueMetricExtension.MailQueueMetricTestSystem metricTestSystem) {
        this.metricTestSystem = metricTestSystem;
        blobIdFactory = new HashBlobId.Factory();

        memoryBlobStore = new MemoryBlobStoreDAO();
        PassThroughBlobStore blobStore = new PassThroughBlobStore(memoryBlobStore, BucketName.DEFAULT, blobIdFactory);
        MimeMessageStore.Factory mimeMessageStoreFactory = new MimeMessageStore.Factory(blobStore);
        mimeMessageStore = mimeMessageStoreFactory.mimeMessageStore();
        factory = new RawMailQueueItemDecoratorFactory();
        mailQueueName = MailQueueName.of(RandomStringUtils.randomAlphabetic(10));
        system = ActorSystem.apply();
        mailQueue = newInstance(pulsar);
    }

    @AfterEach
    void tearDown() throws Exception {
        mailQueue.close();
        system.terminate();
        pulsarClients.stop();
    }

    @Override
    public void awaitRemove() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public MailQueue getMailQueue() {
        return mailQueue;
    }

    @Override
    public int getMailQueueMaxConcurrency() {
        return MAX_CONCURRENCY;
    }

    @Override
    public ManageableMailQueue getManageableMailQueue() {
        return mailQueue;
    }

    public PulsarMailQueue newInstance(DockerPulsarExtension.DockerPulsar pulsar) {
        pulsarConfiguration = pulsar.getConfiguration();
        pulsarClients = PulsarClients.create(pulsarConfiguration);
        int enqueueBufferSize = 10;
        int requeueBufferSize = 10;
        return new PulsarMailQueue(
                new PulsarMailQueueConfiguration(mailQueueName, pulsarConfiguration, MAX_CONCURRENCY, enqueueBufferSize, requeueBufferSize),
                pulsarClients,
                blobIdFactory,
                mimeMessageStore,
                factory,
                metricTestSystem.getMetricFactory(),
                metricTestSystem.getSpyGaugeRegistry(),
                system);
    }

    @Disabled("JAMES-3700 We need to define a deadletter policy for the Pulsar MailQueue")
    @Test
    void badMessagesShouldNotAlterDelivery(DockerPulsarExtension.DockerPulsar pulsar) throws Exception {
        new JavaClient(pulsar.getConfiguration().brokerUri(),
                String.format("persistent://%s/James-%s", pulsar.getConfiguration().namespace().asString(), mailQueueName.asString()))
                .send("BAD").get();

        getMailQueue().enQueue(defaultMail()
                .name("name")
                .build());

        MailQueue.MailQueueItem mail = Flux.from(getMailQueue().deQueue()).onErrorResume(e -> Mono.empty()).take(1).single().block();
        assertThat(mail.getMail().getName()).isEqualTo("name");
    }

    @Disabled("JAMES-3700 We need to define a deadletter policy for the Pulsar MailQueue")
    @Test
    void badMessagesShouldBeMovedToADeadLetterTopic(DockerPulsarExtension.DockerPulsar pulsar) throws Exception {
        new JavaClient(pulsar.getConfiguration().brokerUri(),
                String.format("persistent://%s/James-%s", pulsar.getConfiguration().namespace().asString(), mailQueueName.asString()))
                .send("BAD").get();

        getMailQueue().enQueue(defaultMail()
                .name("name")
                .build());

        try {
            Flux.from(getMailQueue().deQueue()).take(1).single().block();
        } catch (Exception e) {
            // Expected to fail
        }
        Optional<String> deadletterMessage = OptionConverters.toJava(new JavaClient(pulsar.getConfiguration().brokerUri(),
                        String.format("persistent://%s/James-%s/dead-letter", pulsar.getConfiguration().namespace().asString(), mailQueueName.asString()))
                        .consumeOne())
                .map(ConsumerMessage::value);
        assertThat(deadletterMessage).contains("BAD");
    }

    @Test
    void ensureThatDeletionDoNotDeleteFutureEmailsWithTwoInstancesOfMailQueue(DockerPulsarExtension.DockerPulsar pulsar) throws MessagingException, InterruptedException {
        PulsarMailQueue secondQueue = newInstance(pulsar);

        IntStream.range(0, 50).forEach(Throwing.intConsumer(i ->
                enQueue(defaultMail()
                        .name("name" + i)
                        .build())));

        Awaitility.await().untilAsserted(() -> assertThat(getManageableMailQueue().getSize()).isEqualTo(50L));
        Awaitility.await().untilAsserted(() -> assertThat(secondQueue.getSize()).isEqualTo(50L));

        getManageableMailQueue().remove(ManageableMailQueue.Type.Recipient, MailAddressFixture.RECIPIENT1.asString());

        enQueue(defaultMail()
                .name("namez")
                .build());

        Awaitility.await().untilAsserted(() ->
                assertThat(Flux.merge(Flux.from(secondQueue.deQueue()), Flux.from(getManageableMailQueue().deQueue())).blockFirst().getMail().getName())
                        .isEqualTo("namez"));
    }

    @Test
    void ensureThatDeletionApplyOnBrowsingBothInstancesWithTwoInstancesOfMailQueue(DockerPulsarExtension.DockerPulsar pulsar) throws MessagingException, InterruptedException {
        PulsarMailQueue secondQueue = newInstance(pulsar);

        IntStream.range(0, 50).forEach(Throwing.intConsumer(i ->
                enQueue(defaultMail()
                        .name("name" + i)
                        .build())));

        Awaitility.await().untilAsserted(() -> assertThat(getManageableMailQueue().getSize()).isEqualTo(50L));
        Awaitility.await().untilAsserted(() -> assertThat(secondQueue.getSize()).isEqualTo(50L));

        getManageableMailQueue().remove(ManageableMailQueue.Type.Recipient, MailAddressFixture.RECIPIENT1.asString());

        enQueue(defaultMail()
                .name("namez")
                .build());

        assertThat(secondQueue.browse()).toIterable()
                .extracting(mail -> mail.getMail().getName())
                .containsExactly("namez");

        assertThat(getManageableMailQueue().browse()).toIterable()
                .extracting(mail -> mail.getMail().getName())
                .containsExactly("namez");
    }

    @Test
    void queueShouldRemoveMailFromStoreOnAcknowledgedDequeue() throws Exception {
        String expectedName = "name";
        enQueue(defaultMail()
                .name(expectedName)
                .build());

        MailQueue.MailQueueItem mailQueueItem = Flux.from(getMailQueue().deQueue()).blockFirst();
        mailQueueItem.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS);

        assertThat(mailQueueItem.getMail().getName())
                .isEqualTo(expectedName);

        Awaitility.await().untilAsserted(this::assertThatStoreIsEmpty);
    }

    @Test
    void removeShouldRemoveMailFromStoreWhenFilteredOut() throws Exception {
        enQueue(defaultMail()
                .name("name1")
                .build());
        enQueue(defaultMail()
                .name("name2")
                .build());
        enQueue(defaultMail()
                .name("name3")
                .build());

        //this won't delete the mail from the store until we try a dequeue
        getManageableMailQueue().remove(ManageableMailQueue.Type.Name, "name2");

        awaitRemove();

        assertThat(getManageableMailQueue().browse())
                .toIterable()
                .extracting(ManageableMailQueue.MailQueueItemView::getMail)
                .extracting(Mail::getName)
                .containsExactly("name1", "name3");

        Flux.from(getMailQueue().deQueue()).take(2).doOnNext(Throwing.consumer(x -> x.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS))).blockLast();
        Awaitility.await().untilAsserted(this::assertThatStoreIsEmpty);
    }

    private void assertThatStoreIsEmpty() {
        var blobIds = Flux.from(memoryBlobStore.listBlobs(BucketName.DEFAULT))
                .map(Objects::toString)
                .collectList()
                .defaultIfEmpty(List.of())
                .block();
        assertThat(blobIds).isEmpty();
    }

    @Disabled("this guarantee is too strong for Pulsar implementation and doesn't match any domain requirement")
    @Override
    public void flushShouldPreserveBrowseOrder() {
    }

    @Test
    public void browseShouldReturnEmptyWhenSingleDequeueMessageEvenWhenStoreIsGuaranteedEmpty() throws Exception {
        enQueue(defaultMail()
                .name("name")
                .build());

        MailQueue.MailQueueItem mailQueueItem = Flux.from(getMailQueue().deQueue()).blockFirst();
        mailQueueItem.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS);

        Awaitility.await().untilAsserted(this::assertThatStoreIsEmpty);

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();

        assertThat(items)
                .toIterable()
                .isEmpty();
    }

}
