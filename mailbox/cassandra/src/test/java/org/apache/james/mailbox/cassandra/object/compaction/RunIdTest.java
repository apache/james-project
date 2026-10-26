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

package org.apache.james.mailbox.cassandra.object.compaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class RunIdTest {
    @Test
    void shouldPreserveUuidAtStorageBoundary() {
        UUID value = UUID.randomUUID();
        RunId runId = new RunId(value);

        assertThat(runId.value()).isEqualTo(value);
        assertThat(runId).isEqualTo(new RunId(value));
        assertThat(runId.toString()).isEqualTo(value.toString());
    }

    @Test
    void shouldGenerateDistinctRunIds() {
        assertThat(RunId.generate()).isNotEqualTo(RunId.generate());
    }

    @Test
    void shouldRejectNullUuid() {
        assertThatNullPointerException().isThrownBy(() -> new RunId(null));
    }
}
