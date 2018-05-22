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

package org.apache.james.mailbox.quota.mailing.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.core.User;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class UserQuotaThresholdsTest {

    public static final User BOB = User.fromUsername("bob@domain");

    @Test
    public void aggregateShouldMatchBeanContract() {
        EqualsVerifier.forClass(UserQuotaThresholds.Id.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    public void asAggregationKeyShouldConvertAggregateToAStringRepresentation() {
        assertThat(UserQuotaThresholds.Id.from(BOB, "listenerName")
            .asAggregateKey())
            .isEqualTo("QuotaThreasholdEvents/listenerName/bob@domain");
    }

    @Test
    public void fromShouldThrowWhenUserWithSlash() {
        assertThatThrownBy(() -> UserQuotaThresholds.Id.from(User.fromUsername("foo/bar@domain"), "listenerName"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromShouldThrowWhenDomainWithSlash() {
        assertThatThrownBy(() -> UserQuotaThresholds.Id.from(User.fromUsername("foo.bar@dom/ain"), "listenerName"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromShouldThrowWhenListenerNameWithSlash() {
        assertThatThrownBy(() -> UserQuotaThresholds.Id.from(BOB, "listener/Name"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void asAggregationKeyShouldParseAsOriginalPojo() {
        UserQuotaThresholds.Id id = UserQuotaThresholds.Id.from(BOB, "listenerName");
        assertThat(UserQuotaThresholds.Id.fromKey(id.asAggregateKey())).isEqualTo(id);
    }

    @Test
    public void fromKeyShouldThrowWhenLessThan3Parts() {
        assertThatThrownBy(() -> UserQuotaThresholds.Id.fromKey("1/2")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromKeyShouldThrowWhenMoreThan3Parts() {
        assertThatThrownBy(() -> UserQuotaThresholds.Id.fromKey("1/2/3/4")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromKeyShouldThrowWhenPrefixIsNotQuotaThreasholdEvents() {
        assertThatThrownBy(() -> UserQuotaThresholds.Id.fromKey("WrongPrefix/bob@domain/name")).isInstanceOf(IllegalArgumentException.class);
    }
}