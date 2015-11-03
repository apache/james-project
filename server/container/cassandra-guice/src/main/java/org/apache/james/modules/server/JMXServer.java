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

import java.io.IOException;
import java.lang.management.ManagementFactory;
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

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.util.RestrictingRMISocketFactory;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;

public class JMXServer {

    private final FileSystem fileSystem;
    private final Set<String> registeredKeys;
    private final Object lock;
    private JMXConnectorServer jmxConnectorServer;
    private boolean isStarted;

    @Inject
    public JMXServer(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
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
            PropertiesConfiguration configuration = new PropertiesConfiguration(fileSystem.getFile(FileSystem.FILE_PROTOCOL_AND_CONF + "jmx.properties"));
            String address = configuration.getString("jmx.address");
            int port = configuration.getInt("jmx.port");
            String serviceURL = "service:jmx:rmi://" + address + "/jndi/rmi://" + address+ ":" + port +"/jmxrmi";
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
