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

package org.apache.james.webadmin.service;

import static org.mockito.Mockito.mock;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.core.Username;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.webadmin.validation.MailboxName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClearMailboxContentTaskSerializationTest {
    private UserMailboxesService userMailboxesService;
    private static final Username USERNAME = Username.of("bob@domain.tld");
    private static final MailboxName MAILBOX_NAME = new MailboxName("mbn1");

    @BeforeEach
    void setUp() {
        userMailboxesService = mock(UserMailboxesService.class);
    }

    @Test
    void shouldMatchJsonSerializationContract() throws Exception {
        JsonSerializationVerifier.dtoModule(ClearMailboxContentTaskDTO.module(userMailboxesService))
            .bean(new ClearMailboxContentTask(USERNAME, MAILBOX_NAME, userMailboxesService))
            .json(ClassLoaderUtils.getSystemResourceAsString("json/clearMailboxContent.task.json"))
            .verify();
    }
}
