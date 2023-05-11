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

package org.apache.james.protocols.lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.protocols.api.ClientAuth;
import org.apache.james.protocols.lib.mock.ConfigLoader;
import org.apache.james.protocols.lib.netty.AbstractConfigurableAsyncServer;
import org.apache.james.protocols.netty.ChannelHandlerFactory;
import org.apache.james.protocols.netty.Encryption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.netty.channel.ChannelInboundHandlerAdapter;

class AbstractConfigurableAsyncServerTest {

    private static class MemoryFileSystem implements FileSystem {

        private final Map<String, String> fileResources = new HashMap<>();

        @Override
        public InputStream getResource(String url) throws IOException {
            String resourceName = fileResources.get(url);
            if (resourceName != null) {
                InputStream resourceStream = ClassLoader.getSystemResourceAsStream(resourceName);
                if (resourceStream != null) {
                    return resourceStream;
                }
            }
            throw new FileNotFoundException(url);
        }

        @Override
        public File getFile(String fileURL) throws FileNotFoundException {
            String resourceName = fileResources.get(fileURL);
            if (resourceName != null) {
                URL resource = ClassLoader.getSystemResource(resourceName);
                if (resource != null) {
                    return new File(resource.getPath());
                }
            }
            throw new FileNotFoundException(fileURL);
        }

        @Override
        public File getBasedir() {
            throw new NotImplementedException("getBasedir");
        }

        public void put(String fileUrl, String resourceName) {
            fileResources.put(fileUrl, resourceName);
        }
    }

    private static class TestableConfigurableAsyncServer extends AbstractConfigurableAsyncServer {

        protected TestableConfigurableAsyncServer(FileSystem filesystem) {
            super(filesystem);
        }

        @Override
        public String getServiceType () {
            return "Test Service";
        }

        @Override
        protected int getDefaultPort () {
            return 12345;
        }

        @Override
        protected String getDefaultJMXName () {
            return "testserver";
        }

        @Override
        protected ChannelHandlerFactory createFrameHandlerFactory () {
            return null;
        }

        @Override
        protected ChannelInboundHandlerAdapter createCoreHandler () {
            return null;
        }


        // test accessors

        public int getConnPerIp () {
            return connPerIP;
        }

        public String getJmxName() {
            return jmxName;
        }

        @Override
        public Encryption getEncryption() {
            return encryption;
        }

        @Override
        public void buildSSLContext() throws Exception {
            super.buildSSLContext();
        }
    }

    private void initTestServer(String configFile) throws Exception {
        testServer = new TestableConfigurableAsyncServer(memoryFileSystem);
        testServer.configure(ConfigLoader.getConfig(ClassLoader.getSystemResourceAsStream(configFile)));
    }

    private MemoryFileSystem memoryFileSystem;
    private TestableConfigurableAsyncServer testServer;

    @BeforeEach
    void setUp() {
        memoryFileSystem = new MemoryFileSystem();
    }

    @Test
    void testServerDisabled() throws Exception {
        initTestServer("testServerDisabled.xml");
        assertThat(testServer.isEnabled()).isFalse();
    }

    @Test
    void testEmpty() throws Exception {
        initTestServer("testServerDefaults.xml");
        assertThat(testServer.isEnabled()).isTrue();

        // NOTE: bind address/port etc. not exposed, cannot test without bind()

        assertThat(testServer.getJmxName()).isEqualTo(testServer.getDefaultJMXName());

        assertThat(testServer.getHelloName()).isEqualTo(getLocalHostName());

        assertThat(testServer.getTimeout()).isEqualTo(AbstractConfigurableAsyncServer.DEFAULT_TIMEOUT);
        assertThat(testServer.getBacklog()).isEqualTo(AbstractConfigurableAsyncServer.DEFAULT_BACKLOG);

        assertThat(testServer.getMaximumConcurrentConnections()).isZero(); // no default limit
        assertThat(testServer.getConnPerIp()).isZero(); // no default limit

        testServer.buildSSLContext();
        assertThat(testServer.getEncryption()).isNull(); // no TLS by default
    }

    @Test
    void testServerPlain() throws Exception {
        initTestServer("testServerPlain.xml");
        assertThat(testServer.isEnabled()).isTrue();

        // NOTE: bind address/port etc. not exposed, cannot test without bind()

        assertThat(testServer.getJmxName()).isEqualTo("testserver-custom");

        assertThat(testServer.getHelloName()).isEqualTo("custom-mailer");

        assertThat(testServer.getTimeout()).isEqualTo(360);
        assertThat(testServer.getBacklog()).isEqualTo(150);

        assertThat(testServer.getMaximumConcurrentConnections()).isEqualTo(100);
        assertThat(testServer.getConnPerIp()).isEqualTo(5);

        testServer.buildSSLContext();
        assertThat(testServer.getEncryption()).isNull(); // no TLS by default
    }

    @Test
    void testServerTLS() throws Exception {
        memoryFileSystem.put("file://conf/keystore", "keystore");

        initTestServer("testServerTLS.xml");
        testServer.buildSSLContext();

        assertThat(testServer.getEncryption()).isNotNull();
        assertThat(testServer.getEncryption().isStartTLS()).isFalse();
        assertThat(testServer.getEncryption().getEnabledCipherSuites()).isEmpty(); // no default constraints
        assertThat(testServer.getEncryption().getClientAuth()).isEqualTo(ClientAuth.NONE);
        assertThat(testServer.getEncryption().supportsEncryption()).isTrue();
    }

    @Test
    void testServerStartTLS() throws Exception {
        memoryFileSystem.put("file://conf/keystore", "keystore");

        initTestServer("testServerStartTLS.xml");
        testServer.buildSSLContext();

        assertThat(testServer.getEncryption()).isNotNull();
        assertThat(testServer.getEncryption().isStartTLS()).isTrue();
        assertThat(testServer.getEncryption().getEnabledCipherSuites()).isEmpty(); // no default constraints
        assertThat(testServer.getEncryption().getClientAuth()).isEqualTo(ClientAuth.NONE);
        assertThat(testServer.getEncryption().supportsEncryption()).isTrue();
    }

    @Test
    void testServerTLSNeedClientAuth() throws Exception {
        memoryFileSystem.put("file://conf/keystore", "keystore");
        memoryFileSystem.put("file://conf/truststore", "keystore");

        initTestServer("testServerTLSNeedAuth.xml");
        testServer.buildSSLContext();

        assertThat(testServer.getEncryption()).isNotNull();
        assertThat(testServer.getEncryption().getClientAuth()).isNotNull();
        assertThat(testServer.getEncryption().getClientAuth()).isEqualTo(ClientAuth.NEED);
    }

    @Test
    void testServerTLSDefaultClientAuth() throws Exception {
        memoryFileSystem.put("file://conf/keystore", "keystore");
        // memoryFileSystem.put("file://conf/truststore", "keystore");

        initTestServer("testServerTLSDefaultAuth.xml");
        testServer.buildSSLContext();

        assertThat(testServer.getEncryption()).isNotNull();
        assertThat(testServer.getEncryption().getClientAuth()).isNotNull();
        assertThat(testServer.getEncryption().getClientAuth()).isEqualTo(ClientAuth.NEED);
    }

    private static String getLocalHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ue) {
            return "localhost";
        }
    }
}