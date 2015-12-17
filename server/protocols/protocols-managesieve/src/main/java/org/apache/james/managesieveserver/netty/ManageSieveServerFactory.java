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

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.managesieve.transcode.ManageSieveProcessor;
import org.apache.james.protocols.lib.netty.AbstractConfigurableAsyncServer;
import org.apache.james.protocols.lib.netty.AbstractServerFactory;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.user.api.UsersRepository;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public class ManageSieveServerFactory extends AbstractServerFactory {

    private SieveRepository sieveRepository;
    private UsersRepository usersRepository;
    private FileSystem fileSystem;
    private ManageSieveProcessor manageSieveProcessor;

    public ManageSieveServerFactory() {
        this.manageSieveProcessor = new ManageSieveProcessor();
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
    public void setFileSystem(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    @Override
    protected List<AbstractConfigurableAsyncServer> createServers(Logger log, HierarchicalConfiguration config) throws Exception {
        List<AbstractConfigurableAsyncServer> servers = new ArrayList<AbstractConfigurableAsyncServer>();
        List<HierarchicalConfiguration> configs = config.configurationsAt("managesieveserver");

        for (HierarchicalConfiguration serverConfig: configs) {
            ManageSieveServer server = new ManageSieveServer(8000, manageSieveProcessor);
            server.setLog(log);
            server.setFileSystem(fileSystem);
            server.configure(serverConfig);
            servers.add(server);
        }

        return servers;
    }
}
