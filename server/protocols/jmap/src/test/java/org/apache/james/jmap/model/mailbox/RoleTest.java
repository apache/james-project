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
package org.apache.james.jmap.model.mailbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.jmap.model.mailbox.Role;
import org.junit.Test;

import java.util.Locale;
import java.util.Optional;

public class RoleTest {

    @Test
    public void fromShouldReturnInbox() {
        assertThat(Role.from("inbox")).isEqualTo(Optional.of(Role.INBOX));
    }

    @Test
    public void fromShouldReturnEmptyWhenUnknownValue() {
        assertThat(Role.from("jjjj")).isEqualTo(Optional.empty());
    }

    @Test
    public void fromShouldReturnInboxWhenContainsUppercaseValue() {
        assertThat(Role.from("InBox")).isEqualTo(Optional.of(Role.INBOX));
    }

    @Test
    public void fromShouldReturnInboxWhenContainsUppercaseValueInTurkish() {
        Locale previousLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr"));
        try {
            assertThat(Role.from("InBox")).isEqualTo(Optional.of(Role.INBOX));
        } finally {
            Locale.setDefault(previousLocale);
        }
    }

    @Test
    public void fromShouldReturnSomethingWhenXPrefixedRole() {
        assertThat(Role.from("x-client-specific-role")).isEqualTo(Optional.of(new Role("x-client-specific-role")));
    }

}