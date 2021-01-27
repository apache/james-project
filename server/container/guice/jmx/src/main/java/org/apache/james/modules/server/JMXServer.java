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

package org.apache.james.modules.server;

import java.lang.management.ManagementFactory;
import java.net.ServerSocket;
import java.rmi.registry.LocateRegistry;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.james.lifecycle.api.Startable;
import org.apache.james.util.RestrictingRMISocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableMap;

public class JMXServer implements Startable {
    private static final Logger LOGGER = LoggerFactory.getLogger(JMXServer.class);

    private final JmxConfiguration jmxConfiguration;
    private final Set<String> registeredKeys;
    private final Object lock;
    private JMXConnectorServer jmxConnectorServer;
    private boolean isStarted;
    private RestrictingRMISocketFactory restrictingRMISocketFactory;

    @Inject
    public JMXServer(JmxConfiguration jmxConfiguration) {
        this.jmxConfiguration = jmxConfiguration;
        isStarted = false;
        registeredKeys = new HashSet<>();
        lock = new Object();
    }

    public void start() {
        synchronized (lock) {
            if (!jmxConfiguration.isEnabled()) {
                return;
            }
            if (isStarted) {
                return;
            }
            isStarted = true;
            doStart();
        }
    }

    @PreDestroy
    public void stop() {
        synchronized (lock) {
            if (!isStarted) {
                return;
            }
            isStarted = false;
            doStop();
        }
    }

    public void register(String key, Object remote) throws Exception {
        ManagementFactory.getPlatformMBeanServer().registerMBean(remote, new ObjectName(key));
        synchronized (lock) {
            registeredKeys.add(key);
        }
    }

    private void doStart() {
        try {
            String serviceURL = "service:jmx:rmi://" + jmxConfiguration.getHost().getHostName()
                + "/jndi/rmi://" + jmxConfiguration.getHost().getHostName()
                + ":" + jmxConfiguration.getHost().getPort() + "/jmxrmi";
            restrictingRMISocketFactory = new RestrictingRMISocketFactory(jmxConfiguration.getHost().getHostName());
            LocateRegistry.createRegistry(jmxConfiguration.getHost().getPort(), restrictingRMISocketFactory, restrictingRMISocketFactory);

            Map<String, ?> environment = ImmutableMap.of();
            jmxConnectorServer = JMXConnectorServerFactory.newJMXConnectorServer(new JMXServiceURL(serviceURL),
                environment,
                ManagementFactory.getPlatformMBeanServer());

            jmxConnectorServer.start();
            LOGGER.info("JMX server started");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void doStop() {
        try {
            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
            registeredKeys.forEach(Throwing.consumer(key -> mBeanServer.unregisterMBean(new ObjectName(key))));
            registeredKeys.clear();
            jmxConnectorServer.stop();
            restrictingRMISocketFactory.getSockets()
                .forEach(Throwing.consumer(ServerSocket::close)
                    .sneakyThrow());
            LOGGER.info("JMX server stopped");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
