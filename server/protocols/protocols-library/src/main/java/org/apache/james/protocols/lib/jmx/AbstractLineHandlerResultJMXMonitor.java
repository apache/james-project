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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.ExtensibleHandler;
import org.apache.james.protocols.api.handler.LineHandler;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.api.handler.ProtocolHandlerResultHandler;
import org.apache.james.protocols.api.handler.WiringException;
import org.apache.james.protocols.lib.lifecycle.InitializingLifecycleAwareProtocolHandler;

/**
 * Handler which will gather statistics for {@link LineHandler}'s
 * 
 * @param <S>
 */
public abstract class AbstractLineHandlerResultJMXMonitor<R extends Response, S extends ProtocolSession> implements ProtocolHandlerResultHandler<R, S>, ExtensibleHandler, InitializingLifecycleAwareProtocolHandler {

    private final Map<String, LineHandlerStats> lStats = new HashMap<String, LineHandlerStats>();
    private String jmxName;

    /**
     * @see
     * org.apache.james.protocols.api.handler.ProtocolHandlerResultHandler
     * #onResponse(org.apache.james.protocols.api.ProtocolSession, longlong,
     * org.apache.james.protocols.api.handler.ProtocolHandler)
     */
    public Response onResponse(ProtocolSession session, Response response, long executionTime, ProtocolHandler handler) {
        if (handler instanceof LineHandler) {
            lStats.get(handler.getClass().getName()).increment(response);
        }
        return response;
    }

    /**
     * @see
     * org.apache.james.protocols.api.handler.ExtensibleHandler#getMarkerInterfaces()
     */
    public List<Class<?>> getMarkerInterfaces() {
        List<Class<?>> marker = new ArrayList<Class<?>>();
        marker.add(LineHandler.class);
        return marker;
    }

    /**
     * @see
     * org.apache.james.protocols.api.handler.ExtensibleHandler#wireExtensions(java.lang.Class, java.util.List)
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void wireExtensions(Class<?> interfaceName, List<?> extension) throws WiringException {

        if (interfaceName.equals(LineHandler.class)) {
            // add stats for all hooks
            for (Object anExtension : extension) {
                LineHandler c = (LineHandler) anExtension;
                if (!equals(c)) {
                    String cName = c.getClass().getName();
                    try {
                        lStats.put(cName, new LineHandlerStats(jmxName, cName));
                    } catch (Exception e) {
                        throw new WiringException("Unable to wire Hooks", e);
                    }
                }
            }
        }
    }

    @Override
    public void init(Configuration config) throws ConfigurationException {
        this.jmxName = config.getString("jmxName", getDefaultJMXName());        
    }

    @Override
    public void destroy() {
        for (LineHandlerStats lineHandlerStats : lStats.values()) {
            lineHandlerStats.dispose();
        }
    }

    /**
     * Return default JMX Name if none is configured
     * 
     * @return defaultJMXName
     */
    protected abstract String getDefaultJMXName();
}
