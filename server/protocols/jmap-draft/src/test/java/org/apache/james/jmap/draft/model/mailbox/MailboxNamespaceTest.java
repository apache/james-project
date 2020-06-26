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

package org.apache.james.jmap.draft.model.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.core.Username;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class MailboxNamespaceTest {
    @Test
    public void shouldRespectJavaBeanContract() throws Exception {
        EqualsVerifier.forClass(MailboxNamespace.class)
            .verify();
    }

    @Test
    public void delegatedShouldThrowWhenNullOwner() throws Exception {
        assertThatThrownBy(() -> MailboxNamespace.delegated(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void delegatedShouldThrowWhenEmptyOwner() throws Exception {
        assertThatThrownBy(() -> MailboxNamespace.delegated(Username.of("")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void delegatedShouldThrowWhenBlankOwner() throws Exception {
        assertThatThrownBy(() -> MailboxNamespace.delegated(Username.of("  ")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void delegatedShouldReturnDelegatedNamespace() throws Exception {
        Username owner = Username.of("owner@test.com");
        MailboxNamespace actualNamespace = MailboxNamespace.delegated(owner);

        assertThat(actualNamespace.getType()).isEqualTo(MailboxNamespace.Type.Delegated);
        assertThat(actualNamespace.getOwner().get()).isEqualTo(owner);
    }

    @Test
    public void personalShouldReturnPersonalNamespace() throws Exception {
        MailboxNamespace actualNamespace = MailboxNamespace.personal();

        assertThat(actualNamespace.getOwner()).isEmpty();
        assertThat(actualNamespace.getType()).isEqualTo(MailboxNamespace.Type.Personal);
    }

}