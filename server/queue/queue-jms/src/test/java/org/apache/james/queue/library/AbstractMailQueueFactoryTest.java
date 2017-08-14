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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.concurrent.TimeUnit;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.mailet.Mail;
import org.junit.Before;
import org.junit.Test;

public class AbstractMailQueueFactoryTest {
    private static final String QUEUE_1 = "queue1";
    private static final String QUEUE_2 = "queue2";
    private static final String QUEUE_3 = "queue3";

    private AbstractMailQueueFactory abstractMailQueueFactory;
    private MBeanServer mBeanServer;

    @Before
    public void setUp() {
        mBeanServer = mock(MBeanServer.class);
        abstractMailQueueFactory = new AbstractMailQueueFactory() {
            @Override
            protected MailQueue createMailQueue(String name) {
                return new ManageableMailQueue() {

                    @Override
                    public void enQueue(Mail mail, long delay, TimeUnit unit) throws MailQueueException {

                    }

                    @Override
                    public void enQueue(Mail mail) throws MailQueueException {

                    }

                    @Override
                    public MailQueueItem deQueue() throws MailQueueException {
                        return null;
                    }

                    @Override
                    public long getSize() throws MailQueueException {
                        return 0;
                    }

                    @Override
                    public long flush() throws MailQueueException {
                        return 0;
                    }

                    @Override
                    public long clear() throws MailQueueException {
                        return 0;
                    }

                    @Override
                    public long remove(Type type, String value) throws MailQueueException {
                        return 0;
                    }

                    @Override
                    public MailQueueIterator browse() throws MailQueueException {
                        return null;
                    }
                };
            }
        };
        abstractMailQueueFactory.setMbeanServer(mBeanServer);
    }

    @Test
    public void destroyShouldRegisterManageableQueues() throws Exception {
        abstractMailQueueFactory.getQueue(QUEUE_1);
        verify(mBeanServer).registerMBean(any(MailQueue.class), eq(new ObjectName(AbstractMailQueueFactory.MBEAN_NAME_QUEUE_PREFIX + QUEUE_1)));
    }

    @Test
    public void destroyShouldUnregisterAllRegisterQueue() throws Exception {
        abstractMailQueueFactory.getQueue(QUEUE_1);
        abstractMailQueueFactory.getQueue(QUEUE_2);
        abstractMailQueueFactory.getQueue(QUEUE_3);
        abstractMailQueueFactory.destroy();
        verify(mBeanServer).unregisterMBean(eq(new ObjectName(AbstractMailQueueFactory.MBEAN_NAME_QUEUE_PREFIX + QUEUE_1)));
        verify(mBeanServer).unregisterMBean(eq(new ObjectName(AbstractMailQueueFactory.MBEAN_NAME_QUEUE_PREFIX + QUEUE_2)));
        verify(mBeanServer).unregisterMBean(eq(new ObjectName(AbstractMailQueueFactory.MBEAN_NAME_QUEUE_PREFIX + QUEUE_3)));
    }

    @Test
    public void unregisterMBeanShouldWork() throws Exception {
        abstractMailQueueFactory.getQueue(QUEUE_1);
        abstractMailQueueFactory.unregisterMBean(AbstractMailQueueFactory.MBEAN_NAME_QUEUE_PREFIX + QUEUE_1);
        verify(mBeanServer).unregisterMBean(eq(new ObjectName(AbstractMailQueueFactory.MBEAN_NAME_QUEUE_PREFIX + QUEUE_1)));
    }

    @Test
    public void destroyShouldNotBeStoppedByExceptions() throws Exception {
        abstractMailQueueFactory.getQueue(QUEUE_1);
        abstractMailQueueFactory.getQueue(QUEUE_2);
        abstractMailQueueFactory.getQueue(QUEUE_3);
        doThrow(InstanceNotFoundException.class)
            .doNothing()
            .when(mBeanServer)
            .unregisterMBean(new ObjectName(AbstractMailQueueFactory.MBEAN_NAME_QUEUE_PREFIX + QUEUE_1));
        abstractMailQueueFactory.destroy();
        verify(mBeanServer).unregisterMBean(eq(new ObjectName(AbstractMailQueueFactory.MBEAN_NAME_QUEUE_PREFIX + QUEUE_1)));
        verify(mBeanServer).unregisterMBean(eq(new ObjectName(AbstractMailQueueFactory.MBEAN_NAME_QUEUE_PREFIX + QUEUE_2)));
        verify(mBeanServer).unregisterMBean(eq(new ObjectName(AbstractMailQueueFactory.MBEAN_NAME_QUEUE_PREFIX + QUEUE_3)));
    }

}
