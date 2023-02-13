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
package org.apache.james.cli.probe.impl;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import com.google.common.collect.ImmutableMap;

public class JmxConnection implements Closeable {

    public static class AuthCredential {
        String username;
        String password;

        public AuthCredential(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }

    private static final String fmtUrl = "service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi";
    private static final int defaultPort = 9999;

    public static JmxConnection defaultJmxConnection(String host) throws IOException {
        return new JmxConnection(host, defaultPort, Optional.empty());
    }
    
    private final JMXConnector jmxConnector;

    public JmxConnection(String host, int port, Optional<AuthCredential> authCredential) throws IOException {
        JMXServiceURL jmxUrl = new JMXServiceURL(String.format(fmtUrl, host, port));
        Map<String, ?> env = authCredential
            .map(credential -> ImmutableMap.of("jmx.remote.credentials", new String[]{credential.username, credential.password}))
            .orElse(ImmutableMap.of());
        jmxConnector = JMXConnectorFactory.connect(jmxUrl, env);
    }

    @Override
    public void close() throws IOException {
        jmxConnector.close();
    }

    public <T> T retrieveBean(Class<T> mbeanType, String name) throws MalformedObjectNameException, IOException {
        return MBeanServerInvocationHandler.newProxyInstance(getMBeanServerConnection(), new ObjectName(name), mbeanType, true);
    }
    
    private MBeanServerConnection getMBeanServerConnection() throws IOException {
        return jmxConnector.getMBeanServerConnection();
    }
    

}
