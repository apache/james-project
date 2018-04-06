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
import static org.mockito.Mockito.mock;

import java.util.AbstractMap;

import org.apache.james.GuiceJamesServer;
import org.apache.james.MemoryJmapTestRule;
import org.apache.james.cli.util.OutputCapture;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.modules.server.JMXServerModule;
import org.apache.james.rrt.lib.MappingImpl;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.rrt.lib.MappingsImpl;
import org.apache.james.utils.DataProbeImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class DataCommandsIntegrationTest {

    public static final String DOMAIN = "domain.com";
    public static final String USER = "chibenwa";
    public static final String MAIL_ADDRESS = USER + "@" + DOMAIN;
    public static final String PASSWORD = "12345";
    private OutputCapture outputCapture;

    @Rule
    public MemoryJmapTestRule memoryJmap = new MemoryJmapTestRule();
    private GuiceJamesServer guiceJamesServer;
    private DataProbeImpl dataProbe;

    @Before
    public void setUp() throws Exception {
        guiceJamesServer = memoryJmap.jmapServer(new JMXServerModule(),
            binder -> binder.bind(ListeningMessageSearchIndex.class).toInstance(mock(ListeningMessageSearchIndex.class)));
        guiceJamesServer.start();
        dataProbe = guiceJamesServer.getProbe(DataProbeImpl.class);
        outputCapture = new OutputCapture();
    }

    @After
    public void tearDown() {
        guiceJamesServer.stop();
    }

    @Test
    public void addDomainShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "ADDDOMAIN", DOMAIN});

        assertThat(dataProbe.containsDomain(DOMAIN)).isTrue();
    }

    @Test
    public void removeDomainShouldWork() throws Exception {
        dataProbe.addDomain(DOMAIN);

        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "REMOVEDOMAIN", DOMAIN});

        assertThat(dataProbe.containsDomain(DOMAIN)).isFalse();
    }

    @Test
    public void listDomainsShouldWork() throws Exception {
        dataProbe.addDomain(DOMAIN);

        ServerCmd.executeAndOutputToStream(new String[] {"-h", "127.0.0.1", "-p", "9999", "listdomains"}, outputCapture.getPrintStream());

        assertThat(outputCapture.getContent()).contains(DOMAIN);
    }

    @Test
    public void containsDomainShouldWork() throws Exception {
        dataProbe.addDomain(DOMAIN);

        ServerCmd.executeAndOutputToStream(new String[] {"-h", "127.0.0.1", "-p", "9999", "containsdomain", DOMAIN},
            outputCapture.getPrintStream());

        assertThat(outputCapture.getContent())
            .containsOnlyOnce(DOMAIN + " exists");
    }

    @Test
    public void addUserShouldWork() throws Exception {
        dataProbe.addDomain(DOMAIN);

        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "ADDUSER", MAIL_ADDRESS, PASSWORD});

        assertThat(dataProbe.listUsers()).contains(MAIL_ADDRESS);
    }

    @Test
    public void removeUserShouldWork() throws Exception {
        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(MAIL_ADDRESS, PASSWORD);

        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "REMOVEUSER", MAIL_ADDRESS});

        assertThat(dataProbe.listUsers()).doesNotContain(MAIL_ADDRESS);
    }

    @Test
    public void listUsersShouldWork() throws Exception {
        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(MAIL_ADDRESS, PASSWORD);

        ServerCmd.executeAndOutputToStream(new String[] {"-h", "127.0.0.1", "-p", "9999", "listusers"}, outputCapture.getPrintStream());

        assertThat(outputCapture.getContent())
            .containsOnlyOnce(USER);
    }

    @Test
    public void addAddressMappingShouldWork() throws Exception {
        String redirectionAddress = "redirect@apache.org";
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "addaddressmapping", USER, DOMAIN, redirectionAddress});

        assertThat(dataProbe.listMappings())
            .containsOnly(
                new AbstractMap.SimpleEntry<String, Mappings>(
                    MAIL_ADDRESS,
                    MappingsImpl.builder()
                        .add(MappingImpl.address(redirectionAddress))
                        .build()));
    }

    @Test
    public void listMappingsShouldWork() throws Exception {
        String redirectionAddress = "redirect@apache.org";
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "addaddressmapping", USER, DOMAIN, redirectionAddress});

        ServerCmd.executeAndOutputToStream(new String[] {"-h", "127.0.0.1", "-p", "9999", "listmappings"},
            outputCapture.getPrintStream());

        assertThat(outputCapture.getContent())
            .containsOnlyOnce("chibenwa@domain.com=redirect@apache.org");
    }

    @Test
    public void listUsersDomainMappingShouldWork() throws Exception {
        String redirectionAddress = "redirect@apache.org";
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "addaddressmapping", USER, DOMAIN, redirectionAddress});

        ServerCmd.executeAndOutputToStream(new String[] {"-h", "127.0.0.1", "-p", "9999", "listuserdomainmappings", USER, DOMAIN},
            outputCapture.getPrintStream());

        assertThat(outputCapture.getContent())
            .containsOnlyOnce("redirect@apache.org");
    }

    @Test
    public void removeAddressMappingShouldWork() throws Exception {
        String redirectionAddress = "redirect@apache.org";
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "addaddressmapping", USER, DOMAIN, redirectionAddress});

        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "removeaddressmapping", USER, DOMAIN, redirectionAddress});

        assertThat(dataProbe.listMappings())
            .isEmpty();
    }

    @Test
    public void addRegexMappingShouldWork() throws Exception {
        String regex = "regex";
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "addregexmapping", USER, DOMAIN, regex});

        assertThat(dataProbe.listMappings())
            .containsOnly(
                new AbstractMap.SimpleEntry<String, Mappings>(
                    MAIL_ADDRESS,
                    MappingsImpl.builder()
                        .add(MappingImpl.regex(regex))
                        .build()));
    }

    @Test
    public void removeRegexMappingShouldWork() throws Exception {
        String regex = "regex";
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "addregexmapping", USER, DOMAIN, regex});

        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "removeregexmapping", USER, DOMAIN, regex});

        assertThat(dataProbe.listMappings())
            .isEmpty();
    }

}
