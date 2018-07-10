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
package org.apache.james.queue.activemq;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.commons.io.FileUtils;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.queue.api.DelayedManageableMailQueueContract;
import org.apache.james.queue.api.DelayedPriorityMailQueueContract;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.api.PriorityManageableMailQueueContract;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.apache.james.queue.jms.BrokerExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(BrokerExtension.class)
@Tag(BrokerExtension.STATISTICS)
public class ActiveMQMailQueueBlobTest implements DelayedManageableMailQueueContract, DelayedPriorityMailQueueContract, PriorityManageableMailQueueContract {

    static final String BASE_DIR = "file://target/james-test";
    static final boolean USE_BLOB = true;

    ActiveMQMailQueue mailQueue;
    MyFileSystem fileSystem;

    @BeforeEach
    public void setUp(BrokerService broker) throws Exception {
        fileSystem = new MyFileSystem();
        ActiveMQConnectionFactory connectionFactory = createConnectionFactory();
        FileSystemBlobTransferPolicy policy = new FileSystemBlobTransferPolicy();
        policy.setFileSystem(fileSystem);
        policy.setDefaultUploadUrl(BASE_DIR);
        connectionFactory.setBlobTransferPolicy(policy);

        RawMailQueueItemDecoratorFactory mailQueueItemDecoratorFactory = new RawMailQueueItemDecoratorFactory();
        NoopMetricFactory metricFactory = new NoopMetricFactory();
        String queueName = BrokerExtension.generateRandomQueueName(broker);
        mailQueue = new ActiveMQMailQueue(connectionFactory, mailQueueItemDecoratorFactory, queueName, USE_BLOB, metricFactory);
    }

    @AfterEach
    public void tearDown() throws Exception {
        mailQueue.dispose();
        fileSystem.destroy();
    }

    @Override
    public MailQueue getMailQueue() {
        return mailQueue;
    }

    @Override
    public ManageableMailQueue getManageableMailQueue() {
        return mailQueue;
    }

    @Test
    @Override
    @Disabled("JAMES-2295 Disabled as test was dead-locking")
    public void dequeueCanBeChainedBeforeAck() {

    }

    @Test
    @Override
    @Disabled("JAMES-2295 Disabled as test was dead-locking")
    public void dequeueCouldBeInterleavingWithOutOfOrderAck() {

    }

    @Test
    @Override
    @Disabled("JAMES-2296 Not handled by JMS mailqueue. Only single recipient per-recipient removal works")
    public void removeByRecipientShouldRemoveSpecificEmailWhenMultipleRecipients() {

    }

    @Test
    @Override
    @Disabled("JAMES-2308 Flushing JMS mail queue randomly re-order them" +
        "Random test failing around 1% of the time")
    public void flushShouldPreserveBrowseOrder() {

    }

    @Test
    @Override
    @Disabled("JAMES-2309 Long overflow in JMS delays")
    public void enqueueWithVeryLongDelayShouldDelayMail(ExecutorService executorService) {

    }

    @Test
    @Override
    @Disabled("JAMES-2312 JMS clear mailqueue can ommit some messages" +
        "Random test failing around 1% of the time")
    public void clearShouldRemoveAllElements() {

    }

    protected ActiveMQConnectionFactory createConnectionFactory() {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("vm://localhost?create=false");

        FileSystemBlobTransferPolicy policy = new FileSystemBlobTransferPolicy();
        policy.setFileSystem(new MyFileSystem());
        policy.setDefaultUploadUrl(BASE_DIR);
        factory.setBlobTransferPolicy(policy);

        return factory;
    }

    public static final class MyFileSystem implements FileSystem {
        private static final Logger LOGGER = LoggerFactory.getLogger(MyFileSystem.class);

        @Override
        public InputStream getResource(String url) throws IOException {
            return null;
        }

        @Override
        public File getFile(String fileURL) throws FileNotFoundException {
            if (fileURL.startsWith("file://")) {
                return new File(fileURL.substring("file://".length()));

            } else if (fileURL.startsWith("file:/")) {
                return new File(fileURL.substring("file:".length()));

            }
            throw new FileNotFoundException();
        }

        @Override
        public File getBasedir() throws FileNotFoundException {
            throw new FileNotFoundException();
        }

        public void destroy() throws FileNotFoundException {
            try {
                FileUtils.forceDelete(getFile(BASE_DIR));
            } catch (FileNotFoundException e) {
                LOGGER.info("No file specified");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
