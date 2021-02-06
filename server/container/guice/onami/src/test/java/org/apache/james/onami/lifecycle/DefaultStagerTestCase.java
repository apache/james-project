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

package org.apache.james.onami.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class DefaultStagerTestCase {
    @Test
    void stagerShouldStageObjectsRegisteredWhileStaging() {
        final Stager<TestAnnotationA> stager = new DefaultStager<>(TestAnnotationA.class);
        final AtomicBoolean staged = new AtomicBoolean();
        stager.register(stageHandler1 -> stager
            .register(stageHandler2 ->
                staged.set(true)));

        stager.stage();

        assertThat(staged.get()).isTrue();
    }

    /*
     * Deadlock scenario:
     * 1. DefaultStager holds lock while calling Stageable.stage();
     * 2. Stageable.stage() blocks on some thread
     * 3. the thread blocks on the lock in DefaultStager.register()
     */
    @Test
    void stagerShouldNotDeadlockWhileStagingObjectChains() {
        final AtomicBoolean staged = new AtomicBoolean();
        final Stager<TestAnnotationA> stager = new DefaultStager<>(TestAnnotationA.class);
        stager.register(stageHandler1 -> {
            Thread thread = new Thread(
                () -> stager.register(stageHandler2 -> staged.set(true)));
            thread.start();
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        stager.stage();

        assertThat(staged.get()).isTrue();
    }
}
