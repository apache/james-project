/******************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one     *
 * or more contributor license agreements.  See the NOTICE file   *
 * distributed with this work for additional information          *
 * regarding copyright ownership.  The ASF licenses this file     *
 * to you under the Apache License, Version 2.0 (the              *
 * "License"); you may not use this file except in compliance     *
 * with the License.  You may obtain a copy of the License at     *
 *                                                                *
 * http://www.apache.org/licenses/LICENSE-2.0                     *
 *                                                                *
 * Unless required by applicable law or agreed to in writing,     *
 * software distributed under the License is distributed on an    *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY         *
 * KIND, either express or implied.  See the License for the      *
 * specific language governing permissions and limitations        *
 * under the License.                                             *
 ******************************************************************/

package org.apache.james.mailbox;

import static org.apache.james.mailbox.Authorizator.AuthorizationState.ALLOWED;
import static org.apache.james.mailbox.Authorizator.AuthorizationState.FORBIDDEN;
import static org.apache.james.mailbox.Authorizator.AuthorizationState.UNKNOWN_USER;

import org.apache.james.mailbox.Authorizator.AuthorizationState;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class AuthorizatorTest {
    @Test
    void combineShouldAggregateResults() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(AuthorizationState.combine(ALLOWED, ALLOWED)).isEqualTo(ALLOWED);
            softly.assertThat(AuthorizationState.combine(ALLOWED, FORBIDDEN)).isEqualTo(ALLOWED);
            softly.assertThat(AuthorizationState.combine(FORBIDDEN, ALLOWED)).isEqualTo(ALLOWED);
            softly.assertThat(AuthorizationState.combine(FORBIDDEN, FORBIDDEN)).isEqualTo(FORBIDDEN);
            softly.assertThat(AuthorizationState.combine(UNKNOWN_USER, FORBIDDEN)).isEqualTo(UNKNOWN_USER);
            softly.assertThat(AuthorizationState.combine(FORBIDDEN, UNKNOWN_USER)).isEqualTo(UNKNOWN_USER);
            softly.assertThat(AuthorizationState.combine(UNKNOWN_USER, UNKNOWN_USER)).isEqualTo(UNKNOWN_USER);
            softly.assertThat(AuthorizationState.combine(UNKNOWN_USER, ALLOWED)).isEqualTo(ALLOWED);
            softly.assertThat(AuthorizationState.combine(ALLOWED, UNKNOWN_USER)).isEqualTo(ALLOWED);
        });
    }
}
