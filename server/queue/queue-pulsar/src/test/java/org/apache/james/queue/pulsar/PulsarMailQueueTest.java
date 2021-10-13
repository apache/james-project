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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.IntStream;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.james.backends.pulsar.DockerPulsarExtension;
import org.apache.james.backends.pulsar.PulsarConfiguration;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.Store;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
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
import org.apache.james.queue.pulsar.PulsarMailQueue;
import org.apache.james.server.blob.deduplication.PassThroughBlobStore;
import org.apache.mailet.base.MailAddressFixture;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

import akka.actor.ActorSystem;
import reactor.core.publisher.Flux;

@ExtendWith(DockerPulsarExtension.class)
public class PulsarMailQueueTest implements MailQueueContract, MailQueueMetricContract, ManageableMailQueueContract, DelayedMailQueueContract, DelayedManageableMailQueueContract {

    public static Logger logger = LoggerFactory.getLogger("org.apache.james");

    PulsarMailQueue mailQueue;

    private HashBlobId.Factory blobIdFactory;
    private Store<MimeMessage, MimeMessagePartsId> mimeMessageStore;
    private MailQueueItemDecoratorFactory factory;
    private MailQueueName mailQueueName;
    private MailQueueMetricExtension.MailQueueMetricTestSystem metricTestSystem;
    private PulsarConfiguration config;
    private ActorSystem system;

    @BeforeEach
    void setUp(DockerPulsarExtension.DockerPulsar pulsar, MailQueueMetricExtension.MailQueueMetricTestSystem metricTestSystem) {
        this.metricTestSystem = metricTestSystem;
        blobIdFactory = new HashBlobId.Factory();

        MemoryBlobStoreDAO memoryBlobStore = new MemoryBlobStoreDAO();
        PassThroughBlobStore blobStore = new PassThroughBlobStore(memoryBlobStore, BucketName.DEFAULT, blobIdFactory);
        MimeMessageStore.Factory mimeMessageStoreFactory = new MimeMessageStore.Factory(blobStore);
        mimeMessageStore = mimeMessageStoreFactory.mimeMessageStore();
        factory = new RawMailQueueItemDecoratorFactory();
        mailQueueName = MailQueueName.of(RandomStringUtils.randomAlphabetic(10));
        system = ActorSystem.apply();
        mailQueue = newInstance(pulsar);
    }

    @AfterEach
    void tearDown() {
        mailQueue.close();
        system.terminate();
    }

    @Override
    public void awaitRemove() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public MailQueue getMailQueue() {
        return mailQueue;
    }

    @Override
    public ManageableMailQueue getManageableMailQueue() {
        return mailQueue;
    }

    public PulsarMailQueue newInstance(DockerPulsarExtension.DockerPulsar pulsar) {
        config = pulsar.getConfiguration();
        return new PulsarMailQueue(
                mailQueueName,
                config,
                blobIdFactory,
                mimeMessageStore,
                factory,
                metricTestSystem.getMetricFactory(),
                metricTestSystem.getSpyGaugeRegistry(),
                system);
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

    @Disabled("this guarantee is too strong for Pulsar implementation and doesn't match any domain requirement")
    @Override
    public void flushShouldPreserveBrowseOrder() {
    }
}
