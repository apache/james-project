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

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
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
    private static final Set<PosixFilePermission> OWNER_ONLY = Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

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
            if (!generateJMXPasswordFileIfNeed()) {
                LOGGER.error("The JMX connector is not started: its password file could not be created");
                return;
            }
            restrictingRMISocketFactory = new RestrictingRMISocketFactory(jmxConfiguration.getHost().getHostName());
            LocateRegistry.createRegistry(jmxConfiguration.getHost().getPort(), restrictingRMISocketFactory, restrictingRMISocketFactory);

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
            if (jmxConnectorServer == null) {
                return;
            }
            jmxConnectorServer.stop();
            restrictingRMISocketFactory.getSockets()
                .forEach(Throwing.consumer(ServerSocket::close)
                    .sneakyThrow());
            LOGGER.info("JMX server stopped");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return false when the files could not be generated
     */
    private boolean generateJMXPasswordFileIfNeed() {
        boolean shouldCreateJMXPasswordFile = Boolean.parseBoolean(System.getProperty(JMX_CREDENTIAL_GENERATION_ENABLE_PROPERTY_KEY, JMX_CREDENTIAL_GENERATION_ENABLE_DEFAULT_VALUE));
        if (shouldCreateJMXPasswordFile && !existJmxPasswordFile()) {
            return generateJMXPasswordFile();
        }
        return true;
    }

    private boolean existJmxPasswordFile() {
        return Files.exists(Path.of(jmxPasswordFilePath)) && Files.exists(Path.of(jmxAccessFilePath));
    }

    private boolean generateJMXPasswordFile() {
        Preconditions.checkState(!SystemUtils.IS_OS_WINDOWS, "Generating JMX password file is not supported on Windows");
        try {
            Path passwordFile = Path.of(jmxPasswordFilePath);
            if (!Files.exists(passwordFile, LinkOption.NOFOLLOW_LINKS)) {
                String randomPassword = RandomStringUtils.random(10, true, true);
                createOwnerOnlyFile(passwordFile, JmxConfiguration.JAMES_ADMIN_USER_DEFAULT + " " + randomPassword + "\n");
                LOGGER.info("Generated JMX password file: " + passwordFile);
            }

            Path accessFile = Path.of(jmxAccessFilePath);
            if (!Files.exists(accessFile, LinkOption.NOFOLLOW_LINKS)) {
                createOwnerOnlyFile(accessFile, JmxConfiguration.JAMES_ADMIN_USER_DEFAULT + " readwrite\n");
                LOGGER.info("Generated JMX access file: " + accessFile);
            }
            return true;
        } catch (Exception e) {
            LOGGER.error("Failure to auto-generate JMX password", e);
            return false;
        }
    }

    /**
     * Created readable by its owner only from the start, and never through a symbolic link: nobody else can open it
     * before the password is written.
     */
    static void createOwnerOnlyFile(Path path, String content) throws IOException {
        createOwnerOnly(path);
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            Files.deleteIfExists(path);
            throw e;
        }
    }

    /**
     * Readable by its owner only where the file system supports POSIX permissions. Elsewhere (Windows), the file gets
     * the access rights of its directory.
     */
    private static void createOwnerOnly(Path file) throws IOException {
        if (file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.createFile(file, PosixFilePermissions.asFileAttribute(OWNER_ONLY));
        } else {
            Files.createFile(file);
        }
    }
}
