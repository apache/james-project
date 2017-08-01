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

package org.apache.james.mailbox.store.event.distributed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.publisher.Topic;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Sets;

public class DistantMailboxPathRegisterTest {

    private static final MailboxPath MAILBOX_PATH = new MailboxPath("namespace", "user", "name");
    private static final MailboxPath NEW_MAILBOX_PATH = new MailboxPath("namespace_new", "user_new", "name_new");
    private static final String TOPIC = "topic";

    private DistantMailboxPathRegisterMapper mockedMapper;
    private DistantMailboxPathRegister register;

    @Before
    public void setUp() {
        mockedMapper = mock(DistantMailboxPathRegisterMapper.class);
        register = new DistantMailboxPathRegister(mockedMapper, 1);
    }

    @Test(expected = MailboxException.class)
    public void doRenameShouldThrowIfTryingToRenameNonExistingPath() throws Exception {
        register.doRename(MAILBOX_PATH, NEW_MAILBOX_PATH);
        verifyNoMoreInteractions(mockedMapper);
    }

    @Test
    public void getTopicsShouldWork() {
        final Set<Topic> result = Sets.newHashSet(new Topic(TOPIC));
        when(mockedMapper.getTopics(MAILBOX_PATH)).thenReturn(result);
        assertThat(register.getTopics(MAILBOX_PATH)).isEqualTo(result);
    }

    @Test
    public void registerShouldWork() throws MailboxException {
        register.register(MAILBOX_PATH);
        verify(mockedMapper).doRegister(MAILBOX_PATH, register.getLocalTopic());
        verifyNoMoreInteractions(mockedMapper);
    }

    @Test
    public void registerShouldCallMapperOnce() throws MailboxException {
        register.register(MAILBOX_PATH);
        verify(mockedMapper).doRegister(MAILBOX_PATH, register.getLocalTopic());
        register.register(MAILBOX_PATH);
        verifyNoMoreInteractions(mockedMapper);
    }

    @Test
    public void unregisterShouldWork() throws MailboxException {
        register.register(MAILBOX_PATH);
        verify(mockedMapper).doRegister(MAILBOX_PATH, register.getLocalTopic());
        register.unregister(MAILBOX_PATH);
        verify(mockedMapper).doUnRegister(MAILBOX_PATH, register.getLocalTopic());
        verifyNoMoreInteractions(mockedMapper);
    }


    @Test
    public void unregisterShouldNotCallMapperIfListenersAreStillPresent() throws MailboxException {
        register.register(MAILBOX_PATH);
        verify(mockedMapper).doRegister(MAILBOX_PATH, register.getLocalTopic());
        register.register(MAILBOX_PATH);
        register.unregister(MAILBOX_PATH);
        verifyNoMoreInteractions(mockedMapper);
        verifyNoMoreInteractions(mockedMapper);
    }

    @Test
    public void unregisterShouldWorkWhenMultipleListenersWereRegistered() throws MailboxException {
        register.register(MAILBOX_PATH);
        verify(mockedMapper).doRegister(MAILBOX_PATH, register.getLocalTopic());
        register.register(MAILBOX_PATH);
        register.unregister(MAILBOX_PATH);
        verifyNoMoreInteractions(mockedMapper);
        register.unregister(MAILBOX_PATH);
        verify(mockedMapper).doUnRegister(MAILBOX_PATH, register.getLocalTopic());
        verifyNoMoreInteractions(mockedMapper);
    }

    @Test
    public void doRenameShouldWork() throws Exception {
        register.register(MAILBOX_PATH);
        verify(mockedMapper).doRegister(MAILBOX_PATH, register.getLocalTopic());
        register.doRename(MAILBOX_PATH, NEW_MAILBOX_PATH);
        verify(mockedMapper).doRegister(NEW_MAILBOX_PATH, register.getLocalTopic());
        verify(mockedMapper).doUnRegister(MAILBOX_PATH, register.getLocalTopic());
        assertThat(register.getRegisteredMailboxPathCount()).containsEntry(NEW_MAILBOX_PATH, 1L);
        verifyNoMoreInteractions(mockedMapper);
    }

    @Test
    public void doRenameShouldWorkWhenEntryAlreadyExists() throws Exception {
        register.register(MAILBOX_PATH);
        verify(mockedMapper).doRegister(MAILBOX_PATH, register.getLocalTopic());
        register.register(NEW_MAILBOX_PATH);
        verify(mockedMapper).doRegister(NEW_MAILBOX_PATH, register.getLocalTopic());
        register.doRename(MAILBOX_PATH, NEW_MAILBOX_PATH);
        verify(mockedMapper).doUnRegister(MAILBOX_PATH, register.getLocalTopic());
        assertThat(register.getRegisteredMailboxPathCount()).containsEntry(NEW_MAILBOX_PATH, 2L);
        verifyNoMoreInteractions(mockedMapper);
    }

    @Test
    public void mapShouldBeEmptyInitially() {
        assertThat(register.getRegisteredMailboxPathCount()).isEmpty();
    }

    @Test
    public void mapShouldContainOneListenerOnPathAfterRegister() throws MailboxException {
        register.register(MAILBOX_PATH);
        assertThat(register.getRegisteredMailboxPathCount()).containsEntry(MAILBOX_PATH, 1L);
    }

    @Test
    public void mapShouldContainTwoListenerOnPathAfterTwoRegister() throws MailboxException {
        register.register(MAILBOX_PATH);
        register.register(MAILBOX_PATH);
        assertThat(register.getRegisteredMailboxPathCount()).containsEntry(MAILBOX_PATH, 2L);
    }

    @Test
    public void mapListenerCountShouldBeOkAfterTwoRegisterAndOneUnregister() throws MailboxException {
        register.register(MAILBOX_PATH);
        register.register(MAILBOX_PATH);
        register.unregister(MAILBOX_PATH);
        assertThat(register.getRegisteredMailboxPathCount()).containsEntry(MAILBOX_PATH, 1L);
    }

    @Test
    public void mapListenerCountShouldBeEmptyAfterTwoRegisterAndOneUnregister() throws MailboxException {
        register.register(MAILBOX_PATH);
        register.unregister(MAILBOX_PATH);
        assertThat(register.getRegisteredMailboxPathCount()).isEmpty();
    }

    @Test
    public void mapListenerCountShouldBeEmptyAfterDoCompleteUnregister() throws MailboxException {
        register.register(MAILBOX_PATH);
        register.doCompleteUnRegister(MAILBOX_PATH);
        assertThat(register.getRegisteredMailboxPathCount()).isEmpty();
    }

    @Test
    public void mapListenerCountShouldHandleRename() throws Exception {
        register.register(MAILBOX_PATH);
        register.doRename(MAILBOX_PATH, NEW_MAILBOX_PATH);
        assertThat(register.getRegisteredMailboxPathCount()).containsEntry(NEW_MAILBOX_PATH, 1L);
    }

    @Test
    public void mapListenerCountShouldHandleRenameWhenEntryAlreadyExists() throws Exception {
        register.register(MAILBOX_PATH);
        register.register(NEW_MAILBOX_PATH);
        register.doRename(MAILBOX_PATH, NEW_MAILBOX_PATH);
        assertThat(register.getRegisteredMailboxPathCount()).containsEntry(NEW_MAILBOX_PATH, 2L);
    }

    @Test
    public void registerShouldNotBeAffectedByMapperError() throws MailboxException {
        doThrow(new RuntimeException()).when(mockedMapper).doRegister(MAILBOX_PATH, register.getLocalTopic());
        try {
            register.register(MAILBOX_PATH);
            fail("Register should have thrown");
        } catch (RuntimeException e) {
            assertThat(register.getRegisteredMailboxPathCount()).containsEntry(MAILBOX_PATH, 1L);
        }
    }

    @Test
    public void unregisterShouldNotBeAffectedByMapperErrors() throws MailboxException {
        register.register(MAILBOX_PATH);
        doThrow(new RuntimeException()).when(mockedMapper).doUnRegister(MAILBOX_PATH, register.getLocalTopic());
        try {
            register.unregister(MAILBOX_PATH);
            fail("Register should have thrown");
        } catch (RuntimeException e) {
            assertThat(register.getRegisteredMailboxPathCount()).isEmpty();
        }
    }

    @Test
    public void renameShouldNotBeAffectedByMapperErrors() throws MailboxException {
        register.register(MAILBOX_PATH);
        doThrow(new RuntimeException()).when(mockedMapper).doRegister(NEW_MAILBOX_PATH, register.getLocalTopic());
        try {
            register.doRename(MAILBOX_PATH, NEW_MAILBOX_PATH);
            fail("Register should have thrown");
        } catch (RuntimeException e) {
            assertThat(register.getRegisteredMailboxPathCount()).containsEntry(NEW_MAILBOX_PATH, 1L)
                .doesNotContainKey(MAILBOX_PATH);
        }
    }

    @Test
    public void completeUnregisterShouldNotBeAffectedByMapperErrors() throws MailboxException {
        register.register(MAILBOX_PATH);
        doThrow(new RuntimeException()).when(mockedMapper).doUnRegister(MAILBOX_PATH, register.getLocalTopic());
        try {
            register.doCompleteUnRegister(MAILBOX_PATH);
            fail("Register should have thrown");
        } catch (RuntimeException e) {
            assertThat(register.getRegisteredMailboxPathCount()).isEmpty();
        }
    }

    @Test
    public void registerShouldWorkInAConcurrentEnvironment() throws Exception {
        int numTask = 2;
        final long increments = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(numTask);
        for (int i = 0; i < numTask; i++) {
            executorService.submit(() -> {
                try {
                    int j = 0;
                    while (j < increments) {
                        register.register(MAILBOX_PATH);
                        j++;
                    }
                } catch (Exception e) {
                    fail("Exception caught in thread", e);
                }
            });
        }
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);
        assertThat(register.getRegisteredMailboxPathCount()).containsEntry(MAILBOX_PATH, numTask * increments);
    }

    @Test
    public void unregisterShouldWorkInAConcurrentEnvironment() throws Exception {
        int numTask = 2;
        final long increments = 100;
        for (int i = 0; i < numTask * increments; i++) {
            register.register(MAILBOX_PATH);
        }
        ExecutorService executorService = Executors.newFixedThreadPool(numTask);
        for (int i = 0; i < numTask; i++) {
            executorService.submit(() -> {
                try {
                    int j = 0;
                    while (j < increments) {
                        register.unregister(MAILBOX_PATH);
                        j++;
                    }
                } catch (Exception e) {
                    fail("Exception caught in thread", e);
                }
            });
        }
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);
        assertThat(register.getRegisteredMailboxPathCount()).isEmpty();
    }

    @Test
    public void unregisterMixedWithRegisterShouldWorkInAConcurrentEnvironment() throws Exception {
        int numTask = 2;
        final long increments = 100;
        for (int i = 0; i < increments; i++) {
            register.register(MAILBOX_PATH);
        }
        ExecutorService executorService = Executors.newFixedThreadPool(2* numTask);
        for (int i = 0; i < numTask; i++) {
            executorService.submit(() -> {
                try {
                    int j = 0;
                    while (j < increments) {
                        register.register(MAILBOX_PATH);
                        j++;
                    }
                } catch (Exception e) {
                    fail("Exception caught in thread", e);
                }
            });
            executorService.submit(() -> {
                try {
                    int j = 0;
                    while (j < increments) {
                        register.unregister(MAILBOX_PATH);
                        j++;
                    }
                } catch (Exception e) {
                    fail("Exception caught in thread", e);
                }
            });
        }
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);
        assertThat(register.getRegisteredMailboxPathCount()).containsEntry(MAILBOX_PATH, increments);
    }

    @Test
    public void schedulerShouldWork() throws Exception {
        register.register(MAILBOX_PATH);
        try {
            register.init();
            Thread.sleep(1050);

        } finally {
            register.destroy();
        }
        verify(mockedMapper, times(3)).doRegister(MAILBOX_PATH, register.getLocalTopic());
        verifyNoMoreInteractions(mockedMapper);
    }

}
