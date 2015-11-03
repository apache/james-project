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

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import org.apache.james.util.RestrictingRMISocketFactory;
import org.apache.james.utils.PropertiesReader;

import javax.annotation.PreDestroy;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.rmi.registry.LocateRegistry;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class JMXServer {

    private final Set<String> registeredKeys;
    private final Object lock;
    private JMXConnectorServer jmxConnectorServer;
    private boolean isStarted;

    public JMXServer() {
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
        PropertiesReader propertiesReader = new PropertiesReader("jmx.properties");
        String address = propertiesReader.getProperty("jmx.address");
        int port = Integer.parseInt(propertiesReader.getProperty("jmx.port"));
        String serviceURL = "service:jmx:rmi://" + address + "/jndi/rmi://" + address+ ":" + port +"/jmxrmi";
        try {
            RestrictingRMISocketFactory restrictingRMISocketFactory = new RestrictingRMISocketFactory(address);
            LocateRegistry.createRegistry(port, restrictingRMISocketFactory, restrictingRMISocketFactory);

            Map<String, ?> environment = ImmutableMap.of();
            jmxConnectorServer = JMXConnectorServerFactory.newJMXConnectorServer(new JMXServiceURL(serviceURL),
                environment,
                ManagementFactory.getPlatformMBeanServer());

            jmxConnectorServer.start();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private void doStop() {
        try {
            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
            registeredKeys.forEach(key -> {
                try {
                    mBeanServer.unregisterMBean(new ObjectName(key));
                } catch (Exception e) {
                    throw Throwables.propagate(e);
                }
            });
            registeredKeys.clear();
            jmxConnectorServer.stop();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

}
