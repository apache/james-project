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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.protocols.api.handler.ExtensibleHandler;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.api.handler.WiringException;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.Hook;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookResultHook;

/**
 * {@link HookResultHook} implementation which will register a
 * {@link HookStatsMBean} under JMX for every Hook it processed
 */
public class HookResultJMXMonitor implements HookResultHook, ExtensibleHandler, ProtocolHandler {

    private final Map<String, HookStats> hookStats = new HashMap<>();
    private String jmxPath;

    /**
     * @see
     * org.apache.james.protocols.smtp.hook.HookResultHook
     * #onHookResult(org.apache.james.protocols.smtp.SMTPSession,
     * org.apache.james.protocols.smtp.hook.HookResult, long,
     * org.apache.james.protocols.smtp.hook.Hook)
     */
    public HookResult onHookResult(SMTPSession session, HookResult result, long executionTime, Hook hook) {
        String hookName = hook.getClass().getName();
        HookStats stats = hookStats.get(hookName);
        if (stats != null) {
            stats.increment(result.getResult());
        }
        return result;
    }

    /**
     * @see
     * org.apache.james.protocols.api.handler.ExtensibleHandler#getMarkerInterfaces()
     */
    public List<Class<?>> getMarkerInterfaces() {
        List<Class<?>> marker = new ArrayList<>();
        marker.add(Hook.class);
        return marker;
    }

    /**
     * @see
     * org.apache.james.protocols.api.handler.ExtensibleHandler#wireExtensions(java.lang.Class, java.util.List)
     */
    public void wireExtensions(Class<?> interfaceName, List<?> extension) throws WiringException {
        if (interfaceName.equals(Hook.class)) {

            // add stats for all hooks
            for (Object hook : extension) {
                if (!equals(hook)) {
                    String hookName = hook.getClass().getName();
                    try {
                        hookStats.put(hookName, new HookStats(jmxPath, hookName));
                    } catch (Exception e) {
                        throw new WiringException("Unable to wire Hooks", e);
                    }
                }
            }
        }

    }

    protected String getDefaultJMXName() {
        return "smtpserver";
    }

    @Override
    public void init(Configuration config) throws ConfigurationException {
        this.jmxPath = config.getString("jmxName", getDefaultJMXName());        
    }

    @Override
    public void destroy() {
        synchronized (hookStats) {
            for (HookStats hookStats1 : hookStats.values()) {
                hookStats1.dispose();
            }
            hookStats.clear();
        }        
    }
}
