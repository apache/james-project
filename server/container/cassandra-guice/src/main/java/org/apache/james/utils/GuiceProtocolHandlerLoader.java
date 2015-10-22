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

package org.apache.james.utils;

import com.google.common.base.Throwables;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.lib.handler.ProtocolHandlerLoader;

public class GuiceProtocolHandlerLoader implements ProtocolHandlerLoader {

    private final Injector injector;

    @Inject
    public GuiceProtocolHandlerLoader(Injector injector) {
        this.injector = injector;
    }

    @Override
    public ProtocolHandler load(String name, Configuration config) throws LoadingException {
        ProtocolHandler handler = createProtocolHandler(name);
        try {
            handler.init(config);
        } catch (ConfigurationException e) {
            throw Throwables.propagate(e);
        }
        return handler;
    }

    private ProtocolHandler createProtocolHandler(String name) throws LoadingException {
        try {
            Class<ProtocolHandler> clazz = (Class<ProtocolHandler>) ClassLoader.getSystemClassLoader().loadClass(name);
            return injector.getInstance(clazz);
        } catch (ClassNotFoundException e) {
            throw new LoadingException("Can not load " + name);
        }
    }

}
