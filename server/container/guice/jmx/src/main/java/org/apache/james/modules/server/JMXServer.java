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

import java.io.FileNotFoundException;
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

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.util.RestrictingRMISocketFactory;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableMap;

public class JMXServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(JMXServer.class);

    private final PropertiesProvider propertiesProvider;
    private final Set<String> registeredKeys;
    private final Object lock;
    private JMXConnectorServer jmxConnectorServer;
    private boolean isStarted;
    private RestrictingRMISocketFactory restrictingRMISocketFactory;

    @Inject
    public JMXServer(PropertiesProvider propertiesProvider) {
        this.propertiesProvider = propertiesProvider;
        isStarted = false;
        registeredKeys = new HashSet<>();
        lock = new Object();
    }

    public void start() {
        synchronized (lock) {
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
            PropertiesConfiguration configuration = getPropertiesConfiguration();
            String address = configuration.getString("jmx.address", "localhost");
            int port = configuration.getInt("jmx.port", 9999);
            String serviceURL = "service:jmx:rmi://" + address + "/jndi/rmi://" + address + ":" + port + "/jmxrmi";
            restrictingRMISocketFactory = new RestrictingRMISocketFactory(address);
            LocateRegistry.createRegistry(port, restrictingRMISocketFactory, restrictingRMISocketFactory);

            Map<String, ?> environment = ImmutableMap.of();
            jmxConnectorServer = JMXConnectorServerFactory.newJMXConnectorServer(new JMXServiceURL(serviceURL),
                environment,
                ManagementFactory.getPlatformMBeanServer());

            jmxConnectorServer.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private PropertiesConfiguration getPropertiesConfiguration() throws ConfigurationException {
        try {
            return propertiesProvider.getConfiguration("jmx");
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not locate configuration file for JMX. Defaults to rmi://127.0.0.1:9999");
            return new PropertiesConfiguration();
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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
