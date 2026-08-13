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

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.filesystem.api.mock.MockFileSystem;
import org.apache.james.mailbox.Authenticator;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.managesieve.core.CoreProcessor;
import org.apache.james.managesieve.jsieve.Parser;
import org.apache.james.managesieve.transcode.ArgumentParser;
import org.apache.james.managesieve.transcode.ManageSieveProcessor;
import org.apache.james.managesieveserver.netty.ManageSieveServer;
import org.apache.james.managesieveserver.netty.ManageSieveServerFactory.ManageSieveSaslMechanismLoader;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.utils.ProtocolServerUtils;
import org.apache.james.protocols.lib.LegacyJavaEncryptionFactory;
import org.apache.james.protocols.sasl.JamesSaslAuthenticator;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.apache.james.sieverepository.file.SieveFileRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.memory.MemoryUsersRepository;

import com.google.common.collect.ImmutableList;

class ManageSieveServerTestSystem {
    private static final int MAX_LINE_LENGTH = 8000;
    private static final DomainList NO_DOMAIN_LIST = null;
    public static final String PASSWORD = "bobpwd";
    public static final Username USERNAME = Username.of("bob");

    public ManageSieveServer manageSieveServer;
    private MemoryUsersRepository usersRepository;
    private MockFileSystem fileSystem;

    public ManageSieveServerTestSystem() throws Exception {
        this.usersRepository = MemoryUsersRepository.withoutVirtualHosting(NO_DOMAIN_LIST);
        this.usersRepository.addUser(USERNAME, PASSWORD);
        this.fileSystem = new MockFileSystem();
    }

    public void setUp(HierarchicalConfiguration<ImmutableNode> configuration) throws Exception {
        ImmutableList<SaslMechanism> saslMechanisms = ManageSieveSaslMechanismLoader.defaultLoader().load(configuration);
        setUp(configuration, saslMechanisms);
    }

    public void setUp(ImmutableList<SaslMechanism> saslMechanisms) throws Exception {
        HierarchicalConfiguration<ImmutableNode> configuration = FileConfigurationProvider.getConfig(ClassLoader.getSystemResourceAsStream("managesieveserver.xml"));
        setUp(configuration, saslMechanisms);
    }

    private void setUp(HierarchicalConfiguration<ImmutableNode> configuration, ImmutableList<SaslMechanism> saslMechanisms) throws Exception {
        this.fileSystem.clear();
        if (configuration.containsKey("tls.keystore")) {
            prepareKeystore();
        }
        Authenticator authenticator = (username, password) -> {
            try {
                return usersRepository.test(username, password.toString());
            } catch (UsersRepositoryException e) {
                throw new MailboxException("Unable to access users repository", e);
            }
        };
        ManageSieveProcessor manageSieveProcessor = new ManageSieveProcessor(
            new ArgumentParser(new CoreProcessor(new SieveFileRepository(this.fileSystem), new Parser(), saslMechanisms)),
            saslMechanisms,
            new JamesSaslAuthenticator(authenticator, (user, otherUser) -> Authorizator.AuthorizationState.FORBIDDEN));
        this.manageSieveServer = new ManageSieveServer(
            MAX_LINE_LENGTH,
            manageSieveProcessor
        );
        this.manageSieveServer.setFileSystem(this.fileSystem);
        this.manageSieveServer.setEncryptionFactory(new LegacyJavaEncryptionFactory(this.fileSystem));
        this.manageSieveServer.configure(configuration);
        this.manageSieveServer.init();
    }

    private void prepareKeystore() throws IOException {
        Files.createDirectories(this.fileSystem.getBasedir().toPath());
        try (InputStream keystore = ClassLoader.getSystemResourceAsStream("keystore")) {
            if (keystore == null) {
                throw new IOException("ManageSieve test keystore is missing");
            }
            Files.copy(keystore, this.fileSystem.getFile("file://keystore").toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
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
