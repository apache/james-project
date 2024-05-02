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

package org.apache.james.jmap.rfc8621.memory;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.MemoryJamesConfiguration;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.jmap.JMAPConfiguration;
import org.apache.james.jmap.rfc8621.contract.EmailQueryMethodContract;
import org.apache.james.junit.categories.Unstable;
import org.apache.james.modules.TestJMAPServerModule;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class MemoryEmailQueryMethodNoViewTest implements EmailQueryMethodContract {
    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<MemoryJamesConfiguration>(tmpDir ->
        MemoryJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(configuration -> MemoryJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule())
            .overrideWith(binder -> binder.bind(JMAPConfiguration.class)
                .toInstance(JMAPConfiguration.builder()
                    .enable()
                    .randomPort()
                    .disableEmailQueryView()
                    .build())))
        .build();

    @Test
    @Override
    @Disabled("JAMES-3377 Not supported for in-memory test")
    public void emailQueryFilterByTextShouldIgnoreMarkupsInHtmlBody(GuiceJamesServer server) {
    }

    @Test
    @Override
    @Disabled("JAMES-3377 Not supported for in-memory test" +
        "In memory do not attempt message parsing a performs a full match on the raw message content")
    public void emailQueryFilterByTextShouldIgnoreAttachmentContent(GuiceJamesServer server) {
    }

    @Override
    @Tag(Unstable.TAG)
    public void shouldListMailsReceivedBeforeADate(GuiceJamesServer server) {
        EmailQueryMethodContract.super.shouldListMailsReceivedBeforeADate(server);
    }

    @Override
    @Tag(Unstable.TAG)
    public void shouldListMailsReceivedAfterADate(GuiceJamesServer server) {
        EmailQueryMethodContract.super.shouldListMailsReceivedAfterADate(server);
    }


}
