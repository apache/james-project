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

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.lib.handler.ProtocolHandlerLoader;

import com.google.inject.Inject;
import com.google.inject.Injector;

public class GuiceProtocolHandlerLoader implements ProtocolHandlerLoader {

    private final Injector injector;
    private final ExtendedClassLoader extendedClassLoader;

    @Inject
    public GuiceProtocolHandlerLoader(Injector injector, ExtendedClassLoader extendedClassLoader) {
        this.injector = injector;
        this.extendedClassLoader = extendedClassLoader;
    }

    @Override
    public ProtocolHandler load(String name, Configuration config) throws LoadingException {
        ProtocolHandler handler = createProtocolHandler(name);
        try {
            handler.init(config);
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
        return handler;
    }

    private ProtocolHandler createProtocolHandler(String name) throws LoadingException {
        try {
            Class<ProtocolHandler> clazz = extendedClassLoader.locateClass(name);
            return injector.getInstance(clazz);
        } catch (ClassNotFoundException e) {
            throw new LoadingException("Can not load " + name);
        }
    }

}
