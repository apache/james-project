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

import javax.inject.Inject;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.api.handler.ProtocolHandlerChain;
import org.apache.james.protocols.lib.ProtocolHandlerChainImpl;
import org.apache.james.protocols.lib.handler.HandlersPackage;
import org.apache.james.protocols.lib.handler.ProtocolHandlerLoader;

/**
 * Abstract base class which use a {@link ProtocolHandlerLoader} for loading the {@link ProtocolHandler}
 */
public abstract class AbstractProtocolAsyncServer extends AbstractConfigurableAsyncServer {

    private ProtocolHandlerChainImpl handlerChain;

    private ProtocolHandlerLoader loader;

    private HierarchicalConfiguration config;

    @Inject
    public void setProtocolHandlerLoader(ProtocolHandlerLoader loader) {
        this.loader = loader;
    }
    
    @Override
    protected void preInit() throws Exception {
        super.preInit();
        handlerChain = new ProtocolHandlerChainImpl(loader, config.configurationAt("handlerchain"), jmxName, getCoreHandlersPackage(), getJMXHandlersPackage());
        handlerChain.init();
    }

    @Override
    protected void doConfigure(HierarchicalConfiguration config) throws ConfigurationException {
        super.doConfigure(config);
        this.config = config;
    }
    
    @Override
    protected void postDestroy() {
        super.postDestroy();
        handlerChain.destroy();
    }

    /**
     * Return the {@link ProtocolHandlerChain} which contains all loaded handlers
     * 
     * @return chain
     */
    protected ProtocolHandlerChain getProtocolHandlerChain() {
        return handlerChain;
    }
    
    /**
     * Return the {@link HandlersPackage} which is responsible to load the core {@link ProtocolHandler}
     * 
     * @return core
     */
    protected abstract Class< ? extends HandlersPackage> getCoreHandlersPackage();
    
    /**
     * Return the {@link HandlersPackage} which is responsible to load the jmx {@link ProtocolHandler}
     * 
     * @return jmx
     */
    protected abstract Class< ? extends HandlersPackage> getJMXHandlersPackage();

}
