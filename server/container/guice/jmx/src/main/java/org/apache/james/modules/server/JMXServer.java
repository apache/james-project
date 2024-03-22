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

import static org.apache.james.modules.server.JmxConfiguration.ACCESS_FILE_NAME;
import static org.apache.james.modules.server.JmxConfiguration.JMX_CREDENTIAL_GENERATION_ENABLE_DEFAULT_VALUE;
import static org.apache.james.modules.server.JmxConfiguration.JMX_CREDENTIAL_GENERATION_ENABLE_PROPERTY_KEY;
import static org.apache.james.modules.server.JmxConfiguration.PASSWORD_FILE_NAME;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.rmi.registry.LocateRegistry;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.james.filesystem.api.JamesDirectoriesProvider;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.util.FunctionalUtils;
import org.apache.james.util.RestrictingRMISocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

public class JMXServer implements Startable {
    private static final Logger LOGGER = LoggerFactory.getLogger(JMXServer.class);

    private final JmxConfiguration jmxConfiguration;
    private final Set<String> registeredKeys;
    private final Object lock;
    private final String jmxPasswordFilePath;
    private final String jmxAccessFilePath;
    private JMXConnectorServer jmxConnectorServer;
    private boolean isStarted;
    private RestrictingRMISocketFactory restrictingRMISocketFactory;

    @Inject
    public JMXServer(JmxConfiguration jmxConfiguration, JamesDirectoriesProvider directoriesProvider) {
        this.jmxConfiguration = jmxConfiguration;
        this.jmxPasswordFilePath = directoriesProvider.getConfDirectory() + PASSWORD_FILE_NAME;
        this.jmxAccessFilePath = directoriesProvider.getConfDirectory() + ACCESS_FILE_NAME;
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
            generateJMXPasswordFileIfNeed();
            
            if (!existJmxPasswordFile()) {
                LOGGER.warn("No authentication setted up for the JMX component. This expose you to local privilege escalation attacks risk.");
            }

            Map<String, String> environment = Optional.of(existJmxPasswordFile())
                .filter(FunctionalUtils.identityPredicate())
                .map(hasJmxPasswordFile -> ImmutableMap.of("jmx.remote.x.password.file", jmxPasswordFilePath,
                    "jmx.remote.x.access.file", jmxAccessFilePath,
                    "jmx.remote.rmi.server.credentials.filter.pattern", "java.lang.String;!*"))
                .orElse(ImmutableMap.of("jmx.remote.rmi.server.credentials.filter.pattern", "java.lang.String;!*"));

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

    private void generateJMXPasswordFileIfNeed() {
        boolean shouldCreateJMXPasswordFile = Boolean.parseBoolean(System.getProperty(JMX_CREDENTIAL_GENERATION_ENABLE_PROPERTY_KEY, JMX_CREDENTIAL_GENERATION_ENABLE_DEFAULT_VALUE));
        if (shouldCreateJMXPasswordFile && !existJmxPasswordFile()) {
            generateJMXPasswordFile();
        }
    }

    private boolean existJmxPasswordFile() {
        return Files.exists(Path.of(jmxPasswordFilePath)) && Files.exists(Path.of(jmxAccessFilePath));
    }

    private void generateJMXPasswordFile() {
        Preconditions.checkState(!SystemUtils.IS_OS_WINDOWS, "Generating JMX password file is not supported on Windows");
        try {
            File passwordFile = new File(jmxPasswordFilePath);
            if (!passwordFile.exists()) {
                try (OutputStream outputStream = new FileOutputStream(passwordFile)) {
                    String randomPassword = RandomStringUtils.random(10, true, true);
                    IOUtils.write(JmxConfiguration.JAMES_ADMIN_USER_DEFAULT + " " + randomPassword + "\n", outputStream, StandardCharsets.UTF_8);
                    setPermissionOwnerOnly(passwordFile);
                    LOGGER.info("Generated JMX password file: " + passwordFile.getPath());
                } catch (IOException e) {
                    throw new RuntimeException("Error when creating JMX password file: " + passwordFile.getPath(), e);
                }
            }

            File accessFile = new File(jmxAccessFilePath);
            if (!accessFile.exists()) {
                try (OutputStream outputStream = new FileOutputStream(accessFile)) {
                    IOUtils.write(JmxConfiguration.JAMES_ADMIN_USER_DEFAULT + " readwrite\n", outputStream, StandardCharsets.UTF_8);
                    setPermissionOwnerOnly(accessFile);
                    LOGGER.info("Generated JMX access file: " + accessFile.getPath());
                } catch (IOException e) {
                    throw new RuntimeException("Error when creating JMX access file: " + accessFile.getPath(), e);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failure to auto-generate JMX password, fallback to unsecure JMX", e);
        }
    }

    private void setPermissionOwnerOnly(File file) throws IOException {
        Files.setPosixFilePermissions(file.toPath(), Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    }
}
