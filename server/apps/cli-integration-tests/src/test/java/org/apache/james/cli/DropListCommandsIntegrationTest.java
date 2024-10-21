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

package org.apache.james.cli;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.apache.james.droplists.api.DropList.Status.ALLOWED;
import static org.apache.james.droplists.api.DropList.Status.BLOCKED;
import static org.apache.james.droplists.api.DropListContract.provideParametersForGetEntryListTest;
import static org.apache.james.droplists.api.DropListContract.provideParametersForReturnAllowedTest;
import static org.apache.james.droplists.api.DropListContract.provideParametersForReturnBlockedTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.stream.Stream;

import jakarta.mail.internet.AddressException;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.MemoryJamesConfiguration;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.cli.util.OutputCapture;
import org.apache.james.core.MailAddress;
import org.apache.james.droplists.api.DropListEntry;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.modules.server.JMXServerModule;
import org.apache.james.utils.DropListProbeImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DropListCommandsIntegrationTest {
    private OutputCapture outputCapture;

    @RegisterExtension
    JamesServerExtension memoryJmap = new JamesServerBuilder<MemoryJamesConfiguration>(tmpDir ->
        MemoryJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .enableDropLists()
            .build())
        .server(conf -> MemoryJamesServerMain.createServer(conf)
            .overrideWith(new JMXServerModule(),
                binder -> binder.bind(ListeningMessageSearchIndex.class).toInstance(mock(ListeningMessageSearchIndex.class))))
        .build();
    private DropListProbeImpl dropListProbe;

    @BeforeEach
    public void setUp(GuiceJamesServer guiceJamesServer) {
        dropListProbe = guiceJamesServer.getProbe(DropListProbeImpl.class);
        outputCapture = new OutputCapture();
    }

    public static Stream<Arguments> provideParametersForGetDropListEntryTest() throws AddressException {
        return provideParametersForGetEntryListTest();
    }

    public static Stream<Arguments> provideParametersForDropLIstQueryReturnAllowedTest() throws AddressException {
        return provideParametersForReturnAllowedTest();
    }

    public static Stream<Arguments> provideParametersForDropLIstQueryReturnBlockedTest() throws AddressException {
        return provideParametersForReturnBlockedTest();
    }

    @ParameterizedTest(name = "{index} {0}")
    @MethodSource("provideParametersForGetDropListEntryTest")
    void addDropListEntryShouldWork(DropListEntry dropListEntry) throws Exception {
        ServerCmd.doMain(new String[]{"-h", "127.0.0.1", "-p", "9999", "AddDropListEntry",
            dropListEntry.getOwnerScope().name(), dropListEntry.getOwner(), dropListEntry.getDeniedEntity()});

        assertThat(dropListProbe.getDropList(dropListEntry.getOwnerScope(), dropListEntry.getOwner())).containsOnlyOnce(dropListEntry);
    }

    @ParameterizedTest(name = "{index} {0}")
    @MethodSource("provideParametersForGetDropListEntryTest")
    void removeDropListEntryShouldWork(DropListEntry dropListEntry) throws Exception {
        dropListProbe.addDropListEntry(dropListEntry);
        assertThat(dropListProbe.getDropList(dropListEntry.getOwnerScope(), dropListEntry.getOwner())).containsOnlyOnce(dropListEntry);

        ServerCmd.doMain(new String[]{"-h", "127.0.0.1", "-p", "9999", "RemoveDropListEntry",
            dropListEntry.getOwnerScope().name(), dropListEntry.getOwner(), dropListEntry.getDeniedEntity()});

        assertThat(dropListProbe.getDropList(dropListEntry.getOwnerScope(), dropListEntry.getOwner())).doesNotContain(dropListEntry);
    }

    @ParameterizedTest(name = "{index} {0}")
    @MethodSource("provideParametersForGetDropListEntryTest")
    void getDropListShouldWork(DropListEntry dropListEntry) throws Exception {
        dropListProbe.addDropListEntry(dropListEntry);

        ServerCmd.executeAndOutputToStream(new String[]{"-h", "127.0.0.1", "-p", "9999", "GetDropList",
            dropListEntry.getOwnerScope().name(), dropListEntry.getOwner()}, outputCapture.getPrintStream());

        assertThat(outputCapture.getContent()).contains(dropListEntry.getDeniedEntity());
    }

    @ParameterizedTest(name = "{index} {0}, sender: {1}, recipient: {2}")
    @MethodSource("provideParametersForDropLIstQueryReturnAllowedTest")
    void dropListQueryShouldReturnAllowed(DropListEntry dropListEntry, MailAddress senderMailAddress, String recipient) throws Exception {
        dropListProbe.addDropListEntry(dropListEntry);

        ServerCmd.executeAndOutputToStream(new String[]{"-h", "127.0.0.1", "-p", "9999", "DropListQuery",
            dropListEntry.getOwnerScope().name(), recipient, senderMailAddress.asString()}, outputCapture.getPrintStream());

        assertThat(outputCapture.getContent()).contains(ALLOWED.name());
    }

    @ParameterizedTest(name = "{index} {0}, sender: {1}, recipient: {2}")
    @MethodSource("provideParametersForDropLIstQueryReturnBlockedTest")
    void dropListQueryShouldReturnBlocked(DropListEntry dropListEntry, MailAddress senderMailAddress, String recipient) throws Exception {
        dropListProbe.addDropListEntry(dropListEntry);

        ServerCmd.executeAndOutputToStream(new String[]{"-h", "127.0.0.1", "-p", "9999", "DropListQuery",
            dropListEntry.getOwnerScope().name(), recipient, senderMailAddress.asString()}, outputCapture.getPrintStream());

        assertThat(outputCapture.getContent()).contains(BLOCKED.name());
    }
}
