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
package org.apache.james.protocols.lib.netty;

import java.io.FileInputStream;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509ExtendedKeyManager;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.protocols.api.ClientAuth;
import org.apache.james.protocols.api.Encryption;
import org.apache.james.protocols.lib.jmx.ServerMBean;
import org.apache.james.protocols.netty.AbstractAsyncServer;
import org.apache.james.protocols.netty.ChannelHandlerFactory;
import org.apache.james.util.concurrent.JMXEnabledThreadPoolExecutor;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.util.HashedWheelTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.altindag.ssl.SSLFactory;
import nl.altindag.ssl.util.PemUtils;

/**
 * Abstract base class for Servers for all James Servers
 */
public abstract class AbstractConfigurableAsyncServer extends AbstractAsyncServer implements Configurable, ServerMBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractConfigurableAsyncServer.class);

    /** The default value for the connection backlog. */
    public static final int DEFAULT_BACKLOG = 200;

    /** The default value for the connection timeout. */
    public static final int DEFAULT_TIMEOUT = 5 * 60;

    /** The name of the parameter defining the connection timeout. */
    private static final String TIMEOUT_NAME = "connectiontimeout";

    /** The name of the parameter defining the connection backlog. */
    private static final String BACKLOG_NAME = "connectionBacklog";

    /** The name of the parameter defining the service hello name. */
    public static final String HELLO_NAME = "helloName";

    public static final int DEFAULT_MAX_EXECUTOR_COUNT = 16;
    
    // By default, use the Sun X509 algorithm that comes with the Sun JCE
    // provider for SSL
    // certificates
    private static final String defaultX509algorithm = "SunX509";

    // The X.509 certificate algorithm
    private String x509Algorithm = defaultX509algorithm;

    private FileSystem fileSystem;
    private HashedWheelTimer timer;

    private boolean enabled;

    protected int connPerIP;

    private boolean useStartTLS;
    private boolean useSSL;

    private ClientAuth clientAuth;

    protected int connectionLimit;

    private String helloName;

    private String keystore;
    private String keystoreType;
    private String privateKey;
    private String certificates;

    private String secret;

    private String truststore;
    private String truststoreType;
    private char[] truststoreSecret;

    protected Encryption encryption;

    protected String jmxName;

    private String[] enabledCipherSuites;

    private final ConnectionCountHandler countHandler = new ConnectionCountHandler();

    private ExecutionHandler executionHandler = null;
    private ChannelHandlerFactory frameHandlerFactory;

    private int maxExecutorThreads;

    private MBeanServer mbeanServer;

    private int port;

    @Inject
    public final void setFileSystem(FileSystem filesystem) {
        this.fileSystem = filesystem;
    }

    @Inject
    public void setHashWheelTimer(HashedWheelTimer timer) {
        this.timer = timer;
    }

    protected void registerMBean() {

        try {
            mbeanServer.registerMBean(this, new ObjectName(getMBeanName()));
        } catch (Exception e) {
            throw new RuntimeException("Unable to register mbean", e);
        }

    }

    protected void unregisterMBean() {
        try {
            mbeanServer.unregisterMBean(new ObjectName(getMBeanName()));
        } catch (Exception e) {
            throw new RuntimeException("Unable to unregister mbean", e);
        }

    }
    
    private String getMBeanName() {
        return  "org.apache.james:type=server,name=" + jmxName;
    }
    
    @Override
    public final void configure(HierarchicalConfiguration<ImmutableNode> config) throws ConfigurationException {

        enabled = config.getBoolean("[@enabled]", true);

        if (!enabled) {
            LOGGER.info("{} disabled by configuration", getServiceType());
            return;
        }

        String[] listen = StringUtils.split(config.getString("bind", "0.0.0.0:" + getDefaultPort()), ',');
        List<InetSocketAddress> bindAddresses = new ArrayList<>();
        for (String aListen : listen) {
            String[] bind = StringUtils.split(aListen, ':');

            InetSocketAddress address;
            String ip = bind[0].trim();
            int port = Integer.parseInt(bind[1].trim());
            if (!ip.equals("0.0.0.0")) {
                try {
                    ip = InetAddress.getByName(ip).getHostName();
                } catch (UnknownHostException unhe) {
                    throw new ConfigurationException("Malformed bind parameter in configuration of service " + getServiceType(), unhe);
                }
            }
            address = new InetSocketAddress(ip, port);

            LOGGER.info("{} bound to: {}:{}", getServiceType(), ip, port);

            bindAddresses.add(address);
        }
        setListenAddresses(bindAddresses.toArray(InetSocketAddress[]::new));

        jmxName = config.getString("jmxName", getDefaultJMXName());
        int ioWorker = config.getInt("ioWorkerCount", DEFAULT_IO_WORKER_COUNT);
        setIoWorkerCount(ioWorker);

        maxExecutorThreads = config.getInt("maxExecutorCount", DEFAULT_MAX_EXECUTOR_COUNT);

        
        configureHelloName(config);

        setTimeout(config.getInt(TIMEOUT_NAME, DEFAULT_TIMEOUT));

        LOGGER.info("{} handler connection timeout is: {}", getServiceType(), getTimeout());

        setBacklog(config.getInt(BACKLOG_NAME, DEFAULT_BACKLOG));

        LOGGER.info("{} connection backlog is: {}", getServiceType(), getBacklog());

        String connectionLimitString = config.getString("connectionLimit", null);
        if (connectionLimitString != null) {
            try {
                connectionLimit = Integer.parseInt(connectionLimitString);
            } catch (NumberFormatException nfe) {
                LOGGER.error("Connection limit value is not properly formatted.", nfe);
            }
            if (connectionLimit < 0) {
                LOGGER.error("Connection limit value cannot be less than zero.");
                throw new ConfigurationException("Connection limit value cannot be less than zero.");
            } else if (connectionLimit > 0) {
                LOGGER.info("{} will allow a maximum of {} connections.", getServiceType(), connectionLimitString);
            }
        }

        String connectionLimitPerIP = config.getString("connectionLimitPerIP", null);
        if (connectionLimitPerIP != null) {
            try {
                connPerIP = Integer.parseInt(connectionLimitPerIP);
            } catch (NumberFormatException nfe) {
                LOGGER.error("Connection limit per IP value is not properly formatted.", nfe);
            }
            if (connPerIP < 0) {
                LOGGER.error("Connection limit per IP value cannot be less than zero.");
                throw new ConfigurationException("Connection limit value cannot be less than zero.");
            } else if (connPerIP > 0) {
                LOGGER.info("{} will allow a maximum of {} per IP connections for {}", getServiceType(), connPerIP, getServiceType());
            }
        }

        useStartTLS = config.getBoolean("tls.[@startTLS]", false);
        useSSL = config.getBoolean("tls.[@socketTLS]", false);

        if (config.getProperty("tls.clientAuth") != null || config.getKeys("tls.clientAuth").hasNext()) {
            clientAuth = ClientAuth.NEED;
        } else {
            clientAuth = ClientAuth.NONE;
        }

        if (useSSL && useStartTLS) {
            throw new ConfigurationException("startTLS is only supported when using plain sockets");
        }

        if (useStartTLS || useSSL) {
            enabledCipherSuites = config.getStringArray("tls.supportedCipherSuites.cipherSuite");
            keystore = config.getString("tls.keystore", null);
            privateKey = config.getString("tls.privateKey", null);
            certificates = config.getString("tls.certificates", null);
            keystoreType = config.getString("tls.keystoreType", "JKS");
            if (keystore == null && (privateKey == null || certificates == null)) {
                throw new ConfigurationException("keystore or (privateKey and certificates) needs to get configured");
            }
            secret = config.getString("tls.secret", null);
            x509Algorithm = config.getString("tls.algorithm", defaultX509algorithm);

            truststore = config.getString("tls.clientAuth.truststore", null);
            truststoreType = config.getString("tls.clientAuth.truststoreType", "JKS");
            truststoreSecret = config.getString("tls.clientAuth.truststoreSecret", "").toCharArray();
            LOGGER.info("TLS enabled with auth {} using truststore {}", clientAuth, truststore);
        }

        doConfigure(config);

    }

    @PostConstruct
    public final void init() throws Exception {

        if (isEnabled()) {

            buildSSLContext();
            preInit();
            executionHandler = createExecutionHandler();
            frameHandlerFactory = createFrameHandlerFactory();
            bind();
            port = retrieveFirstBindedPort();

            mbeanServer = ManagementFactory.getPlatformMBeanServer();
            registerMBean();
            
            LOGGER.info("Init {} done", getServiceType());

        }
    
    }

    private int retrieveFirstBindedPort() {
        List<InetSocketAddress> listenAddresses = getListenAddresses();
        InetSocketAddress inetSocketAddress = listenAddresses.get(0);
        return inetSocketAddress.getPort();
    }

    public int getPort() {
        return port;
    }

    public boolean useSSL() {
        return useSSL;
    }

    @PreDestroy
    public final void destroy() {
        
        LOGGER.info("Dispose {}", getServiceType());
        
        if (isEnabled()) {
            unbind();
            postDestroy();

            if (executionHandler != null) {
                executionHandler.releaseExternalResources();
            }

            unregisterMBean();
        }
        LOGGER.info("Dispose {} done", getServiceType());

    }

    protected void postDestroy() {
        // override me
    }
    
    
    /**
     * This method is called on init of the Server. Subclasses should override
     * this method to init stuff
     * 
     * @throws Exception
     */
    protected void preInit() throws Exception {
        // override me
    }

    protected void doConfigure(HierarchicalConfiguration<ImmutableNode> config) throws ConfigurationException {
        // override me
    }

    /**
     * Configure the helloName for the given Configuration
     * 
     * @param handlerConfiguration
     * @throws ConfigurationException
     */
    protected void configureHelloName(Configuration handlerConfiguration) throws ConfigurationException {
        String hostName;
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ue) {
            hostName = "localhost";
        }

        LOGGER.info("{} is running on: {}", getServiceType(), hostName);

        boolean autodetect = handlerConfiguration.getBoolean(HELLO_NAME + ".[@autodetect]", true);
        if (autodetect) {
            helloName = hostName;
        } else {
            helloName = handlerConfiguration.getString(HELLO_NAME);
            if (helloName == null || helloName.trim().length() < 1) {
                throw new ConfigurationException("Please configure the helloName or use autodetect");
            }
        }

        LOGGER.info("{} handler hello name is: {}", getServiceType(), helloName);
    }

    /**
     * Return if the server is enabled by the configuration
     * 
     * @return enabled
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Return helloName for this server
     * 
     * @return helloName
     */
    public String getHelloName() {
        return helloName;
    }

    protected Encryption getEncryption() {
        return encryption;
    }

    /**
     * Build the SSLEngine
     * 
     * @throws Exception
     */
    protected void buildSSLContext() throws Exception {
        if (useStartTLS || useSSL) {
            FileInputStream fis = null;
            SSLFactory.Builder sslFactoryBuilder = SSLFactory.builder()
                .withSslContextAlgorithm("TLS");
            try {
                if (keystore != null) {
                    char[] passwordAsCharArray = Optional.ofNullable(secret)
                        .orElse("")
                        .toCharArray();
                    sslFactoryBuilder.withIdentityMaterial(
                        fileSystem.getFile(keystore).toPath(),
                        passwordAsCharArray,
                        passwordAsCharArray,
                        keystoreType);
                } else {
                    X509ExtendedKeyManager keyManager = PemUtils.loadIdentityMaterial(
                        fileSystem.getResource(certificates),
                        fileSystem.getResource(privateKey),
                        Optional.ofNullable(secret)
                            .map(String::toCharArray)
                            .orElse(null));

                    sslFactoryBuilder.withIdentityMaterial(keyManager);
                }

                if (clientAuth != null) {
                    if (truststore != null) {
                        sslFactoryBuilder.withTrustMaterial(
                            fileSystem.getFile(truststore).toPath(),
                            truststoreSecret,
                            truststoreType);
                    }
                }

                SSLContext context = sslFactoryBuilder.build().getSslContext();

                if (useStartTLS) {
                    encryption = Encryption.createStartTls(context, enabledCipherSuites, clientAuth);
                } else {
                    encryption = Encryption.createTls(context, enabledCipherSuites, clientAuth);
                }
            } finally {
                if (fis != null) {
                    fis.close();
                }
            }
        }
    }

    /**
     * Return the default port which will get used for this server if non is
     * specify in the configuration
     * 
     * @return port
     */
    protected abstract int getDefaultPort();


    /**
     * Return the socket type. The Socket type can be secure or plain
     * 
     * @return the socket type ('plain' or 'secure')
     */
    @Override
    public String getSocketType() {
        if (getEncryption() != null && !getEncryption().isStartTLS()) {
            return "secure";
        }
        return "plain";
    }

    @Override
    public boolean getStartTLSSupported() {
        return getEncryption() != null && getEncryption().isStartTLS();
    }

    @Override
    public int getMaximumConcurrentConnections() {
        return connectionLimit;
    }

    protected String getThreadPoolJMXPath() {
        return "org.apache.james:type=server,name=" + jmxName + ",sub-type=threadpool";
    }
    
    @Override
    protected Executor createBossExecutor() {
        return JMXEnabledThreadPoolExecutor.newCachedThreadPool(getThreadPoolJMXPath(), getDefaultJMXName() + "-boss");
    }

    @Override
    protected Executor createWorkerExecutor() {
        return JMXEnabledThreadPoolExecutor.newCachedThreadPool(getThreadPoolJMXPath(), getDefaultJMXName() + "-worker");
    }

    /**
     * Return the default name of the the server in JMX if none is configured
     * via "jmxname" in the configuration
     * 
     * @return defaultJmxName
     */
    protected abstract String getDefaultJMXName();

    @Override
    public boolean isStarted() {
        return isBound();
    }

    @Override
    public boolean start() {
        try {
            bind();
        } catch (Exception e) {
            LOGGER.error("Unable to start server", e);
            return false;
        }
        return true;
    }

    @Override
    public boolean stop() {
        unbind();
        return true;
    }

    @Override
    public long getHandledConnections() {
        return countHandler.getConnectionsTillStartup();
    }

    @Override
    public int getCurrentConnections() {
        return countHandler.getCurrentConnectionCount();
    }

    protected ConnectionCountHandler getConnectionCountHandler() {
        return countHandler;
    }

    @Override
    public String[] getBoundAddresses() {

        List<InetSocketAddress> addresses = getListenAddresses();
        String[] addrs = new String[addresses.size()];
        for (int i = 0; i < addresses.size(); i++) {
            InetSocketAddress address = addresses.get(i);
            addrs[i] = address.getHostName() + ":" + address.getPort();
        }

        return addrs;
    }

    @Override
    protected void configureBootstrap(ServerBootstrap bootstrap) {
        super.configureBootstrap(bootstrap);
        
        // enable tcp keep-alives
        bootstrap.setOption("child.keepAlive", true);
    }
    
    /**
     * Create a new {@link ExecutionHandler} which is used to execute IO-Bound handlers
     * 
     * @return ehandler
     */
    protected ExecutionHandler createExecutionHandler() {
        return new ExecutionHandler(new JMXEnabledOrderedMemoryAwareThreadPoolExecutor(maxExecutorThreads, 0, 0, getThreadPoolJMXPath(), getDefaultJMXName() + "-executor"));
    }

    protected abstract ChannelHandlerFactory createFrameHandlerFactory();

    /**
     * Return the {@link ExecutionHandler} or null if non should be used. Be sure you call {@link #createExecutionHandler()} before
     * 
     * @return ehandler
     */
    protected ExecutionHandler getExecutionHandler() {
        return executionHandler;
    }
    
    protected ChannelHandlerFactory getFrameHandlerFactory() {
        return frameHandlerFactory;
    }

    protected abstract ChannelUpstreamHandler createCoreHandler();
    
    @Override
    protected ChannelPipelineFactory createPipelineFactory(ChannelGroup group) {
        return new AbstractExecutorAwareChannelPipelineFactory(getTimeout(), connectionLimit, connPerIP, group,
            getEncryption(), getExecutionHandler(), getFrameHandlerFactory(), timer) {

            @Override
            protected ChannelUpstreamHandler createHandler() {
                return AbstractConfigurableAsyncServer.this.createCoreHandler();

            }

            @Override
            protected ConnectionCountHandler getConnectionCountHandler() {
                return AbstractConfigurableAsyncServer.this.getConnectionCountHandler();
            }

        };
    }
    
}
