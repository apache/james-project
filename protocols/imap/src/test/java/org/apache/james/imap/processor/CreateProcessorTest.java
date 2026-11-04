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


package org.apache.james.imap.processor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.james.core.Username;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.decode.main.OutputStreamImapResponseWriter;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.imap.encode.base.ImapResponseComposerImpl;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.encode.main.DefaultLocalizer;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.main.ResponseEncoder;
import org.apache.james.imap.message.request.CreateRequest;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CreateProcessorTest {
    @ParameterizedTest
    @ValueSource(strings = {"#private", "#private."})
    void createShouldAnswerWhenTheNameDesignatesTheNamespaceRoot(String mailboxName) throws Exception {
        InMemoryMailboxManager mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        CreateProcessor testee = new CreateProcessor(mailboxManager, new UnpooledStatusResponseFactory(),
            new RecordingMetricFactory(), PathConverter.Factory.DEFAULT);
        FakeImapSession session = new FakeImapSession();
        session.authenticated();
        session.setMailboxSession(mailboxManager.createSystemSession(Username.of("bob")));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImapResponseComposerImpl composer = new ImapResponseComposerImpl(new OutputStreamImapResponseWriter(outputStream));
        testee.process(new CreateRequest(mailboxName, new Tag("A1")),
            new ResponseEncoder(new DefaultImapEncoderFactory(new DefaultLocalizer(), true).buildImapEncoder(), composer),
            session);
        composer.flush();

        assertThat(outputStream.toString(StandardCharsets.US_ASCII))
            .startsWith("A1 BAD CREATE");
    }
}
