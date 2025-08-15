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

package org.apache.james.managesieveserver;

import java.net.InetAddress;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.filesystem.api.mock.MockFileSystem;
import org.apache.james.managesieve.core.CoreProcessor;
import org.apache.james.managesieve.jsieve.Parser;
import org.apache.james.managesieve.transcode.ArgumentParser;
import org.apache.james.managesieve.transcode.ManageSieveProcessor;
import org.apache.james.managesieveserver.netty.ManageSieveServer;
import org.apache.james.protocols.api.utils.ProtocolServerUtils;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.apache.james.sieverepository.file.SieveFileRepository;
import org.apache.james.user.memory.MemoryUsersRepository;

class ManageSieveServerTestSystem {
    private static final int MAX_LINE_LENGTH = 8000;
    private static final DomainList NO_DOMAIN_LIST = null;
    public static final String PASSWORD = "bobpwd";
    public static final Username USERNAME = Username.of("bob");


    private ManageSieveProcessor manageSieveProcessor;
    public ManageSieveServer manageSieveServer;
    private MemoryUsersRepository usersRepository;
    private MockFileSystem fileSystem;

    public ManageSieveServerTestSystem() throws Exception {
        this.usersRepository = MemoryUsersRepository.withoutVirtualHosting(NO_DOMAIN_LIST);
        this.usersRepository.addUser(USERNAME, PASSWORD);
        this.fileSystem = new MockFileSystem();
        this.manageSieveProcessor = new ManageSieveProcessor(
            new ArgumentParser(
                new CoreProcessor(
                    new SieveFileRepository(this.fileSystem),
                    this.usersRepository,
                    new Parser()
                )
            )
        );
    }

    public void setUp(HierarchicalConfiguration<ImmutableNode> configuration) throws Exception {
        this.fileSystem.clear();
        this.manageSieveServer = new ManageSieveServer(
            MAX_LINE_LENGTH,
            this.manageSieveProcessor
        );
        this.manageSieveServer.setFileSystem(this.fileSystem);
        this.manageSieveServer.configure(configuration);
        this.manageSieveServer.init();
    }

    public void setUp(String configFilePath) throws Exception {
        HierarchicalConfiguration<ImmutableNode> configuration = FileConfigurationProvider.getConfig(ClassLoader.getSystemResourceAsStream(configFilePath));
        setUp(configuration);
    }

    public void setUp() throws Exception {
        setUp("managesieveserver.xml");
    }

    public InetAddress getBindedIP() {
        return new ProtocolServerUtils(this.manageSieveServer).retrieveBindedAddress().getAddress();
    }

    public int getBindedPort() {
        return new ProtocolServerUtils(this.manageSieveServer).retrieveBindedAddress().getPort();
    }
}
