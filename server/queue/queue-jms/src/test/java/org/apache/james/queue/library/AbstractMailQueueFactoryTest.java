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

package org.apache.james.queue.library;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.james.queue.api.MailQueueName;
import org.apache.james.queue.api.ManageableMailQueue;
import org.junit.Before;
import org.junit.Test;

public class AbstractMailQueueFactoryTest {
    private static final MailQueueName QUEUE_1 = MailQueueName.of("queue1");
    private static final MailQueueName QUEUE_2 = MailQueueName.of("queue2");
    private static final MailQueueName QUEUE_3 = MailQueueName.of("queue3");

    private AbstractMailQueueFactory<?> abstractMailQueueFactory;
    private MBeanServer mBeanServer;

    @Before
    public void setUp() {
        mBeanServer = mock(MBeanServer.class);
        abstractMailQueueFactory = new AbstractMailQueueFactory<ManageableMailQueue>() {
            @Override
            protected ManageableMailQueue createCacheableMailQueue(MailQueueName name) {
                return mock(ManageableMailQueue.class);
            }
        };
        abstractMailQueueFactory.setMbeanServer(mBeanServer);
    }

    @Test
    public void destroyShouldRegisterManageableQueues() throws Exception {
        abstractMailQueueFactory.createQueue(QUEUE_1);
        verify(mBeanServer).registerMBean(any(MailQueueManagement.class), eq(new ObjectName(AbstractMailQueueFactory.MBEAN_NAME_QUEUE_PREFIX + QUEUE_1.asString())));
    }

    @Test
    public void destroyShouldUnregisterAllRegisterQueue() throws Exception {
        abstractMailQueueFactory.createQueue(QUEUE_1);
        abstractMailQueueFactory.createQueue(QUEUE_2);
        abstractMailQueueFactory.createQueue(QUEUE_3);
        abstractMailQueueFactory.destroy();
        verify(mBeanServer).unregisterMBean(eq(new ObjectName(AbstractMailQueueFactory.MBEAN_NAME_QUEUE_PREFIX + QUEUE_1.asString())));
        verify(mBeanServer).unregisterMBean(eq(new ObjectName(AbstractMailQueueFactory.MBEAN_NAME_QUEUE_PREFIX + QUEUE_2.asString())));
        verify(mBeanServer).unregisterMBean(eq(new ObjectName(AbstractMailQueueFactory.MBEAN_NAME_QUEUE_PREFIX + QUEUE_3.asString())));
    }

    @Test
    public void unregisterMBeanShouldWork() throws Exception {
        abstractMailQueueFactory.createQueue(QUEUE_1);
        abstractMailQueueFactory.unregisterMBean(AbstractMailQueueFactory.MBEAN_NAME_QUEUE_PREFIX + QUEUE_1.asString());
        verify(mBeanServer).unregisterMBean(eq(new ObjectName(AbstractMailQueueFactory.MBEAN_NAME_QUEUE_PREFIX + QUEUE_1.asString())));
    }

    @Test
    public void destroyShouldNotBeStoppedByExceptions() throws Exception {
        abstractMailQueueFactory.createQueue(QUEUE_1);
        abstractMailQueueFactory.createQueue(QUEUE_2);
        abstractMailQueueFactory.createQueue(QUEUE_3);
        doThrow(InstanceNotFoundException.class)
            .doNothing()
            .when(mBeanServer)
            .unregisterMBean(new ObjectName(AbstractMailQueueFactory.MBEAN_NAME_QUEUE_PREFIX + QUEUE_1.asString()));
        abstractMailQueueFactory.destroy();
        verify(mBeanServer).unregisterMBean(eq(new ObjectName(AbstractMailQueueFactory.MBEAN_NAME_QUEUE_PREFIX + QUEUE_1.asString())));
        verify(mBeanServer).unregisterMBean(eq(new ObjectName(AbstractMailQueueFactory.MBEAN_NAME_QUEUE_PREFIX + QUEUE_2.asString())));
        verify(mBeanServer).unregisterMBean(eq(new ObjectName(AbstractMailQueueFactory.MBEAN_NAME_QUEUE_PREFIX + QUEUE_3.asString())));
    }

}
