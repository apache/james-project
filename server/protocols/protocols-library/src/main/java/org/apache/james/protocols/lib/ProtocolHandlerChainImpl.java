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
package org.apache.james.protocols.lib;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.CombinedConfiguration;
import org.apache.commons.configuration2.ConfigurationUtils;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.protocols.api.handler.ExtensibleHandler;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.api.handler.ProtocolHandlerChain;
import org.apache.james.protocols.api.handler.WiringException;
import org.apache.james.protocols.lib.handler.HandlersPackage;
import org.apache.james.protocols.lib.handler.ProtocolHandlerLoader;

public class ProtocolHandlerChainImpl implements ProtocolHandlerChain {

    private final ProtocolHandlerLoader loader;
    private final HierarchicalConfiguration<ImmutableNode> handlerchainConfig;
    private final String jmxName;
    private final String coreHandlersPackage;
    private final String jmxHandlersPackage;
    private final List<Object> handlers = new LinkedList<>();

    public ProtocolHandlerChainImpl(ProtocolHandlerLoader loader, HierarchicalConfiguration<ImmutableNode> handlerchainConfig, String jmxName, Class<? extends HandlersPackage> coreHandlersPackage, Class<? extends HandlersPackage> jmxHandlersPackage) {
        this.loader = loader;
        this.handlerchainConfig = handlerchainConfig;
        this.jmxName = jmxName;
        this.coreHandlersPackage = coreHandlersPackage.getName();
        this.jmxHandlersPackage = jmxHandlersPackage.getName();
    }

    public void init() throws Exception {
        List<org.apache.commons.configuration2.HierarchicalConfiguration<ImmutableNode>> children = handlerchainConfig.configurationsAt("handler");

        // check if the coreHandlersPackage was specified in the config, if
        // not add the default
        if (handlerchainConfig.getString("[@coreHandlersPackage]") == null) {
            handlerchainConfig.addProperty("[@coreHandlersPackage]", coreHandlersPackage);
        }

        String coreHandlersPackage = handlerchainConfig.getString("[@coreHandlersPackage]");

        if (handlerchainConfig.getString("[@jmxHandlersPackage]") == null) {
            handlerchainConfig.addProperty("[@jmxHandlersPackage]", jmxHandlersPackage);
        }

        String jmxHandlersPackage = handlerchainConfig.getString("[@jmxHandlersPackage]");

        HandlersPackage handlersPackage = (HandlersPackage) loader.load(coreHandlersPackage, addHandler(coreHandlersPackage));
        registerHandlersPackage(handlersPackage, null, children);

        if (handlerchainConfig.getBoolean("[@enableJmx]", true)) {
            HierarchicalConfiguration<ImmutableNode> builder = new BaseHierarchicalConfiguration();
            builder.addProperty("jmxName", jmxName);
            HandlersPackage jmxPackage = (HandlersPackage) loader.load(jmxHandlersPackage, addHandler(jmxHandlersPackage));

            registerHandlersPackage(jmxPackage, builder, children);
        }

        for (HierarchicalConfiguration<ImmutableNode> hConf : children) {
            String className = hConf.getString("[@class]", null);
            if (className != null) {
                handlers.add(loader.load(className, hConf));
            } else {
                throw new ConfigurationException("Missing @class attribute in configuration: " + ConfigurationUtils.toString(hConf));
            }
        }
        wireExtensibleHandlers();

    }

    private void wireExtensibleHandlers() throws WiringException {
        LinkedList<ExtensibleHandler> eHandlers = getHandlers(ExtensibleHandler.class);
        for (ExtensibleHandler extensibleHandler : eHandlers) {
            final List<Class<?>> markerInterfaces = extensibleHandler.getMarkerInterfaces();
            for (Class<?> markerInterface : markerInterfaces) {
                final List<?> extensions = getHandlers(markerInterface);
                // ok now time for try the wiring
                extensibleHandler.wireExtensions(markerInterface, extensions);
            }
        }

    }


    private void registerHandlersPackage(HandlersPackage handlersPackage, HierarchicalConfiguration<ImmutableNode> handlerConfig, List<HierarchicalConfiguration<ImmutableNode>> children) throws ConfigurationException {
        List<String> c = handlersPackage.getHandlers();

        for (String cName : c) {
            CombinedConfiguration conf = new CombinedConfiguration();
            HierarchicalConfiguration<ImmutableNode> cmdConf = addHandler(cName);
            conf.addConfiguration(cmdConf);
            if (handlerConfig != null) {
                conf.addConfiguration(handlerConfig);
            }
            children.add(conf);
        }
    }

    /**
     * Return a DefaultConfiguration build on the given command name and
     * classname.
     *
     * @param className The class name
     * @return DefaultConfiguration
     */
    private HierarchicalConfiguration<ImmutableNode> addHandler(String className) {
        HierarchicalConfiguration<ImmutableNode> hConf = new BaseHierarchicalConfiguration();
        hConf.addProperty("[@class]", className);
        return hConf;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> LinkedList<T> getHandlers(Class<T> type) {
        return handlers.stream()
            .filter(type::isInstance)
            .map(h -> (T) h)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Destroy all loaded {@link ProtocolHandler}
     */
    @Override
    public void destroy() {
        LinkedList<ProtocolHandler> lHandlers = getHandlers(ProtocolHandler.class);
        for (ProtocolHandler handler : lHandlers) {
            handler.destroy();
        }
    }
}
