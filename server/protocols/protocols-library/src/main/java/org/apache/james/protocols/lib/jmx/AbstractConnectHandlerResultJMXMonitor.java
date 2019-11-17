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
package org.apache.james.protocols.lib.jmx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.ConnectHandler;
import org.apache.james.protocols.api.handler.ExtensibleHandler;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.api.handler.ProtocolHandlerResultHandler;
import org.apache.james.protocols.api.handler.WiringException;

/**
 * Handler which will gather statistics for {@link ConnectHandler}'s
 */
public abstract class AbstractConnectHandlerResultJMXMonitor<R extends Response, S extends ProtocolSession> implements ProtocolHandlerResultHandler<R,S>, ExtensibleHandler, ProtocolHandler {

    private final Map<String, ConnectHandlerStats> cStats = new HashMap<>();
    private String jmxName;

    @Override
    public void init(Configuration config) throws ConfigurationException {
        this.jmxName = config.getString("jmxName", getDefaultJMXName());        
    }

    @Override
    public void destroy() {
        for (ConnectHandlerStats connectHandlerStats : cStats.values()) {
            connectHandlerStats.dispose();
        }
    }

    @Override
    public Response onResponse(ProtocolSession session, Response response, long executionTime, ProtocolHandler handler) {
        if (handler instanceof ConnectHandler) {
            cStats.get(handler.getClass().getName()).increment(response);
        }
        return response;
    }

    @Override
    public List<Class<?>> getMarkerInterfaces() {
        List<Class<?>> marker = new ArrayList<>();
        marker.add(ConnectHandler.class);

        return marker;
    }

    @Override
    @SuppressWarnings("unlikely-arg-type")
    public void wireExtensions(Class<?> interfaceName, List<?> extension) throws WiringException {
        if (interfaceName.equals(ConnectHandler.class)) {
            // add stats for all hooks
            for (Object anExtension : extension) {
                ConnectHandler<?> c = (ConnectHandler<?>) anExtension;
                if (!equals(c)) {
                    String cName = c.getClass().getName();
                    try {
                        cStats.put(cName, new ConnectHandlerStats(jmxName, cName));
                    } catch (Exception e) {
                        throw new WiringException("Unable to wire Hooks", e);
                    }
                }
            }
        }
    }

    /**
     * Return the default JMXName to use if none is configured
     */
    protected abstract String getDefaultJMXName();
}
