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

package org.apache.james.managesieveserver.netty;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.managesieve.core.CoreProcessor;
import org.apache.james.managesieve.jsieve.Parser;
import org.apache.james.managesieve.transcode.ArgumentParser;
import org.apache.james.managesieve.transcode.ManageSieveProcessor;
import org.apache.james.protocols.lib.netty.AbstractConfigurableAsyncServer;
import org.apache.james.protocols.lib.netty.AbstractServerFactory;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.user.api.UsersRepository;

public class ManageSieveServerFactory extends AbstractServerFactory {

    private FileSystem fileSystem;
    private ManageSieveProcessor manageSieveProcessor;
    private SieveRepository sieveRepository;
    private UsersRepository usersRepository;
    private Parser sieveParser;

    @Inject
    public void setFileSystem(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    @Inject
    public void setSieveRepository(SieveRepository sieveRepository) {
        this.sieveRepository = sieveRepository;
    }

    @Inject
    public void setUsersRepository(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    @Inject
    public void setParser(Parser sieveParser) {
        this.sieveParser = sieveParser;
    }


    @Override
    @PostConstruct
    public void init() throws Exception {
        manageSieveProcessor = new ManageSieveProcessor(new ArgumentParser(new CoreProcessor(sieveRepository, usersRepository, sieveParser)));
        super.init();
    }

    @Override
    protected List<AbstractConfigurableAsyncServer> createServers(HierarchicalConfiguration<ImmutableNode> config) throws Exception {
        List<AbstractConfigurableAsyncServer> servers = new ArrayList<>();
        List<HierarchicalConfiguration<ImmutableNode>> configs = config.configurationsAt("managesieveserver");

        for (HierarchicalConfiguration<ImmutableNode> serverConfig: configs) {
            ManageSieveServer server = new ManageSieveServer(8000, manageSieveProcessor);
            server.setFileSystem(fileSystem);
            server.configure(serverConfig);
            servers.add(server);
        }

        return servers;
    }
}
