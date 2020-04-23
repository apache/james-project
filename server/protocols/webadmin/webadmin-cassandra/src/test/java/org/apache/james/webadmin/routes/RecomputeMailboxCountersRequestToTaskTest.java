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

package org.apache.james.webadmin.routes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.apache.james.mailbox.cassandra.mail.task.RecomputeMailboxCountersService.Options;
import org.junit.jupiter.api.Test;

class RecomputeMailboxCountersRequestToTaskTest {
    @Test
    void parseOptionsShouldReturnRecheckWhenEmpty() {
        assertThat(RecomputeMailboxCountersRequestToTask.parseOptions(Optional.empty()))
            .isEqualTo(Options.recheckMessageDenormalization());
    }

    @Test
    void parseOptionsShouldReturnRecheckWhenFalse() {
        assertThat(RecomputeMailboxCountersRequestToTask.parseOptions(Optional.of("false")))
            .isEqualTo(Options.recheckMessageDenormalization());
    }

    @Test
    void parseOptionsShouldBeCaseIncentive() {
        assertThat(RecomputeMailboxCountersRequestToTask.parseOptions(Optional.of("False")))
            .isEqualTo(Options.recheckMessageDenormalization());
    }

    @Test
    void parseOptionsShouldReturnTrueWhenTrust() {
        assertThat(RecomputeMailboxCountersRequestToTask.parseOptions(Optional.of("true")))
            .isEqualTo(Options.trustMessageDenormalization());
    }

    @Test
    void parseOptionsShouldFailWhenEmpty() {
        assertThatThrownBy(() -> RecomputeMailboxCountersRequestToTask.parseOptions(Optional.of("")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseOptionsShouldFailWhenNotAValidBoolean() {
        assertThatThrownBy(() -> RecomputeMailboxCountersRequestToTask.parseOptions(Optional.of("zz")))
            .isInstanceOf(IllegalArgumentException.class);
    }
}