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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.cli.util.OutputCapture;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.modules.server.JMXServerModule;
import org.apache.james.rrt.api.SourceDomainIsNotInDomainListException;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingsImpl;
import org.apache.james.utils.DataProbeImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class DataCommandsIntegrationTest {
    public static final String DOMAIN = "domain.com";
    public static final String USER = "chibenwa";
    public static final String MAIL_ADDRESS = USER + "@" + DOMAIN;
    public static final String PASSWORD = "12345";
    private OutputCapture outputCapture;

    @RegisterExtension
    JamesServerExtension memoryJmap = new JamesServerBuilder<>(JamesServerBuilder.defaultConfigurationProvider())
        .server(conf -> MemoryJamesServerMain.createServer(conf)
            .overrideWith(new JMXServerModule(),
                binder -> binder.bind(ListeningMessageSearchIndex.class).toInstance(mock(ListeningMessageSearchIndex.class))))
        .build();
    private DataProbeImpl dataProbe;

    @BeforeEach
    public void setUp(GuiceJamesServer guiceJamesServer) {
        dataProbe = guiceJamesServer.getProbe(DataProbeImpl.class);
        outputCapture = new OutputCapture();
    }

    @Test
    void addDomainShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "ADDDOMAIN", DOMAIN});

        assertThat(dataProbe.containsDomain(DOMAIN)).isTrue();
    }

    @Test
    void removeDomainShouldWork() throws Exception {
        dataProbe.addDomain(DOMAIN);

        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "REMOVEDOMAIN", DOMAIN});

        assertThat(dataProbe.containsDomain(DOMAIN)).isFalse();
    }

    @Test
    void listDomainsShouldWork() throws Exception {
        dataProbe.addDomain(DOMAIN);

        ServerCmd.executeAndOutputToStream(new String[] {"-h", "127.0.0.1", "-p", "9999", "listdomains"}, outputCapture.getPrintStream());

        assertThat(outputCapture.getContent()).contains(DOMAIN);
    }

    @Test
    void containsDomainShouldWork() throws Exception {
        dataProbe.addDomain(DOMAIN);

        ServerCmd.executeAndOutputToStream(new String[] {"-h", "127.0.0.1", "-p", "9999", "containsdomain", DOMAIN},
            outputCapture.getPrintStream());

        assertThat(outputCapture.getContent())
            .containsOnlyOnce(DOMAIN + " exists");
    }

    @Test
    void addUserShouldWork() throws Exception {
        dataProbe.addDomain(DOMAIN);

        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "ADDUSER", MAIL_ADDRESS, PASSWORD});

        assertThat(dataProbe.listUsers()).contains(MAIL_ADDRESS);
    }

    @Test
    void removeUserShouldWork() throws Exception {
        dataProbe.fluent()
            .addDomain(DOMAIN)
            .addUser(MAIL_ADDRESS, PASSWORD);

        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "REMOVEUSER", MAIL_ADDRESS});

        assertThat(dataProbe.listUsers()).doesNotContain(MAIL_ADDRESS);
    }

    @Test
    void listUsersShouldWork() throws Exception {
        dataProbe.fluent()
            .addDomain(DOMAIN)
            .addUser(MAIL_ADDRESS, PASSWORD);

        ServerCmd.executeAndOutputToStream(new String[] {"-h", "127.0.0.1", "-p", "9999", "listusers"}, outputCapture.getPrintStream());

        assertThat(outputCapture.getContent())
            .containsOnlyOnce(USER);
    }

    @Test
    void addAddressMappingShouldWork() throws Exception {
        dataProbe.addDomain(DOMAIN);

        String redirectionAddress = "redirect@apache.org";
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "addaddressmapping", USER, DOMAIN, redirectionAddress});

        assertThat(dataProbe.listMappings())
            .hasSize(1)
            .containsEntry(
                MAIL_ADDRESS,
                MappingsImpl.builder()
                    .add(Mapping.address(redirectionAddress))
                    .build());
    }

    @Test
    void addAddressMappingsShouldThrowWhenDomainIsNotInDomainList() throws Exception {
        String redirectionAddress = "redirect@apache.org";

        assertThatThrownBy(() -> ServerCmd.executeAndOutputToStream(
                new String[] {"-h", "127.0.0.1", "-p", "9999", "addAddressMapping", USER, DOMAIN, redirectionAddress},
                outputCapture.getPrintStream()))
            .isInstanceOf(SourceDomainIsNotInDomainListException.class)
            .hasMessage("Source domain '" + DOMAIN + "' is not managed by the domainList");
    }

    @Test
    void listMappingsShouldWork() throws Exception {
        dataProbe.addDomain(DOMAIN);

        String redirectionAddress = "redirect@apache.org";
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "addaddressmapping", USER, DOMAIN, redirectionAddress});

        ServerCmd.executeAndOutputToStream(new String[] {"-h", "127.0.0.1", "-p", "9999", "listmappings"},
            outputCapture.getPrintStream());

        assertThat(outputCapture.getContent())
            .containsOnlyOnce("chibenwa@domain.com=redirect@apache.org");
    }

    @Test
    void listUsersDomainMappingShouldWork() throws Exception {
        dataProbe.addDomain(DOMAIN);

        String redirectionAddress = "redirect@apache.org";
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "addaddressmapping", USER, DOMAIN, redirectionAddress});

        ServerCmd.executeAndOutputToStream(new String[] {"-h", "127.0.0.1", "-p", "9999", "listuserdomainmappings", USER, DOMAIN},
            outputCapture.getPrintStream());

        assertThat(outputCapture.getContent())
            .containsOnlyOnce("redirect@apache.org");
    }

    @Test
    void removeAddressMappingShouldWork() throws Exception {
        dataProbe.addDomain(DOMAIN);

        String redirectionAddress = "redirect@apache.org";
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "addaddressmapping", USER, DOMAIN, redirectionAddress});

        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "removeaddressmapping", USER, DOMAIN, redirectionAddress});

        assertThat(dataProbe.listMappings())
            .isEmpty();
    }


    @Test
    void addRegexMappingsShouldThrowWhenDomainIsNotInDomainList() throws Exception {
        String regexMapping = ".*@apache.org";

        assertThatThrownBy(() -> ServerCmd.executeAndOutputToStream(
                new String[] {"-h", "127.0.0.1", "-p", "9999", "AddRegexMapping", USER, DOMAIN, regexMapping},
                outputCapture.getPrintStream()))
            .isInstanceOf(SourceDomainIsNotInDomainListException.class)
            .hasMessage("Source domain '" + DOMAIN + "' is not managed by the domainList");
    }

    @Test
    void addRegexMappingShouldWork() throws Exception {
        dataProbe.addDomain(DOMAIN);

        String regex = "regex";
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "addregexmapping", USER, DOMAIN, regex});

        assertThat(dataProbe.listMappings())
            .hasSize(1)
            .containsEntry(
                MAIL_ADDRESS,
                MappingsImpl.builder()
                    .add(Mapping.regex(regex))
                    .build());
    }

    @Test
    void removeRegexMappingShouldWork() throws Exception {
        dataProbe.addDomain(DOMAIN);

        String regex = "regex";
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "addregexmapping", USER, DOMAIN, regex});

        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "removeregexmapping", USER, DOMAIN, regex});

        assertThat(dataProbe.listMappings())
            .isEmpty();
    }

}
