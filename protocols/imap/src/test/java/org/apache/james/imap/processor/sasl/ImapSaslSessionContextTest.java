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

package org.apache.james.imap.processor.sasl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.apache.james.imap.api.process.ImapSession;
import org.junit.jupiter.api.Test;

class ImapSaslSessionContextTest {
    private interface ExtensionSaslService {
    }

    private static class FakeExtensionSaslService implements ExtensionSaslService {
    }

    @Test
    void serviceShouldExposeRegisteredProtocolService() {
        ImapSaslSessionContext testee = new ImapSaslSessionContext(mock(ImapSession.class));
        ExtensionSaslService service = new FakeExtensionSaslService();

        testee.register(ExtensionSaslService.class, service);

        assertThat(testee.service(ExtensionSaslService.class)).contains(service);
    }

    @Test
    void serviceShouldReturnEmptyWhenProtocolServiceIsNotRegistered() {
        ImapSaslSessionContext testee = new ImapSaslSessionContext(mock(ImapSession.class));

        assertThat(testee.service(ExtensionSaslService.class)).isEmpty();
    }
}
