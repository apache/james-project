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

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.lib.handler.ProtocolHandlerLoader;

import com.google.inject.Inject;

public class GuiceProtocolHandlerLoader implements ProtocolHandlerLoader {
    private final GuiceGenericLoader genericLoader;

    @Inject
    public GuiceProtocolHandlerLoader(GuiceGenericLoader genericLoader) {
        this.genericLoader = genericLoader;
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
            ClassName className = new ClassName(name);
            return genericLoader.instanciate(className);
        } catch (Exception e) {
            throw new LoadingException("Can not load " + name, e);
        }
    }

}
