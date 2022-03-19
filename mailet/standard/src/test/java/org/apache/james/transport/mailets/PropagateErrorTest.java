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

package org.apache.james.transport.mailets;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.mail.MessagingException;

import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PropagateErrorTest {
    private PropagateError testee;

    @BeforeEach
    public void setUp() throws Exception {
        testee = new PropagateError();
        testee.init(FakeMailetConfig.builder()
            .mailetContext(FakeMailContext.builder())
            .build());
    }

    @Test
    public void shouldThrowWhenNoErrorMessage() throws Exception {
        Mail mail = FakeMail.builder()
            .name("mail")
            .build();

        assertThatThrownBy(() -> testee.service(mail))
            .isInstanceOf(MessagingException.class)
            .hasMessage("Propagating previously encountered mailet processing errors: null");
    }

    @Test
    public void shouldThrowWhenErrorMessage() throws Exception {
        Mail mail = FakeMail.builder()
            .name("mail")
            .errorMessage("This is what happened")
            .build();

        assertThatThrownBy(() -> testee.service(mail))
            .isInstanceOf(MessagingException.class)
            .hasMessage("Propagating previously encountered mailet processing errors: This is what happened");
    }
}