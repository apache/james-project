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

package org.apache.james.protocols.smtp.hook;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class HookResultTest {
    @Test
    public void shouldMatchBeanContract() {
        EqualsVerifier.forClass(HookResult.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    public void shouldThrowOnInvalidReturnCode() {
        assertThatThrownBy(() -> new HookResult(HookReturnCode.DENY + HookReturnCode.DECLINED))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldNotThrowOnOK() {
        assertThatCode(() -> new HookResult(HookReturnCode.OK))
            .doesNotThrowAnyException();
    }

    @Test
    public void shouldNotThrowOnDeny() {
        assertThatCode(() -> new HookResult(HookReturnCode.DENY))
            .doesNotThrowAnyException();
    }

    @Test
    public void shouldNotThrowOnDenySoft() {
        assertThatCode(() -> new HookResult(HookReturnCode.DENYSOFT))
            .doesNotThrowAnyException();
    }

    @Test
    public void shouldNotThrowOnDeclined() {
        assertThatCode(() -> new HookResult(HookReturnCode.DECLINED))
            .doesNotThrowAnyException();
    }

    @Test
    public void shouldNotThrowOnDisconnect() {
        assertThatCode(() -> new HookResult(HookReturnCode.DISCONNECT))
            .doesNotThrowAnyException();
    }
}