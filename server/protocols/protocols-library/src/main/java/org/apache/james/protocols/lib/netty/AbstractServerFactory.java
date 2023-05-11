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

package org.apache.james.protocols.lib.netty;

import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Singleton;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.lifecycle.api.Configurable;

/**
 * Abstract base class for Factories that need to create {@link AbstractConfigurableAsyncServer}'s via configuration files
 */
@Singleton
public abstract class AbstractServerFactory implements Configurable {

    private List<AbstractConfigurableAsyncServer> servers;
    private HierarchicalConfiguration<ImmutableNode> config;

    /**
     * Create {@link AbstractConfigurableAsyncServer} servers, inject dependencies and configure them before return all fo them in a {@link List}
     *
     * @return servers
     */
    protected abstract List<AbstractConfigurableAsyncServer> createServers(HierarchicalConfiguration<ImmutableNode> config) throws Exception;
    
    @Override
    public void configure(HierarchicalConfiguration<ImmutableNode> config) {
        this.config = config;
    }

    @PostConstruct
    public void init() throws Exception {
        servers = createServers(config);
        for (AbstractConfigurableAsyncServer server: servers) {
            server.init();
        }
    }
    
    /**
     * @return all {@link AbstractConfigurableAsyncServer} instances that was create via this Factory
     */
    public List<AbstractConfigurableAsyncServer> getServers() {
        return servers;
    }
    
    @PreDestroy
    public void destroy() {
        for (AbstractConfigurableAsyncServer server: servers) {
            server.destroy();
        }
    }
 
}
