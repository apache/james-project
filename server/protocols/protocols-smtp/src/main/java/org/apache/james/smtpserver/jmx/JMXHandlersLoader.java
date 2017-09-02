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
package org.apache.james.smtpserver.jmx;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.protocols.lib.handler.HandlersPackage;

public class JMXHandlersLoader implements HandlersPackage {

    private final List<String> handlers = new ArrayList<>();

    public JMXHandlersLoader() {
        handlers.add(ConnectHandlerResultJMXMonitor.class.getName());
        handlers.add(CommandHandlerResultJMXMonitor.class.getName());
        handlers.add(LineHandlerResultJMXMonitor.class.getName());
        handlers.add(HookResultJMXMonitor.class.getName());
    }

    /**
     */
    public List<String> getHandlers() {
        return handlers;
    }

    @Override
    public void init(Configuration config) throws ConfigurationException {

    }

    @Override
    public void destroy() {

    }
}
