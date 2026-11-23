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

package org.apache.james.mailbox.store.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.InMemoryCombinationManagerTestSystem;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.CombinationManagerTestSystem;
import org.apache.james.mailbox.store.ThreadIdGuessingAlgorithmContract;
import org.apache.james.mailbox.store.mail.SearchThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.mail.ThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.mail.model.MimeMessageId;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.stream.RawField;
import org.junit.jupiter.api.Test;

public class SearchThreadIdGuessingAlgorithmTest extends ThreadIdGuessingAlgorithmContract {
    @Override
    protected CombinationManagerTestSystem createTestingSystem() {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();

        return new InMemoryCombinationManagerTestSystem(
            resources.getMailboxManager(),
            resources.getMessageIdManager());
    }

    @Override
    protected ThreadIdGuessingAlgorithm initThreadIdGuessingAlgorithm(CombinationManagerTestSystem testingData) {
        return new SearchThreadIdGuessingAlgorithm(testingData.getMailboxManager(), testingData.getMessageIdManager());
    }

    @Override
    protected MessageId initNewBasedMessageId() {
        return InMemoryMessageId.of(100);
    }

    @Override
    protected MessageId initOtherBasedMessageId() {
        return InMemoryMessageId.of(1000);
    }

    @Test
    void emptyInReplyToShouldNotJoinAnExistingThread() throws Exception {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        SearchThreadIdGuessingAlgorithm algorithm = new SearchThreadIdGuessingAlgorithm(resources.getMailboxManager(), resources.getMessageIdManager());
        MailboxSession session = resources.getMailboxManager().createSystemSession(Username.of("alice"));
        resources.getMailboxManager().createMailbox(MailboxPath.inbox(session.getUser()), session);
        resources.getMailboxManager().getMailbox(MailboxPath.inbox(session.getUser()), session)
            .appendMessage(MessageManager.AppendCommand.from(Message.Builder.of()
                .setMessageId("<existing@example.com>")
                .setField(new RawField("In-Reply-To", "<parent@example.com>"))
                .setBody("testmail", StandardCharsets.UTF_8)), session);
        MessageId newMessageId = InMemoryMessageId.of(100);

        ThreadId threadId = algorithm.guessThreadIdReactive(newMessageId, Optional.empty(), Optional.of(new MimeMessageId("")),
            Optional.empty(), Optional.empty(), session).block();

        assertThat(threadId).isEqualTo(ThreadId.fromBaseMessageId(newMessageId));
    }
}
