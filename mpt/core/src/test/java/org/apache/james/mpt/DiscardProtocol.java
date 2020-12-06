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

package org.apache.james.mpt;

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;

import org.apache.james.util.Port;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple <a href='http://tools.ietf.org/html/rfc863'>RFC 863</a> implementation.
 */
public class DiscardProtocol {
    private static final int SOCKET_CONNECTION_WAIT_MILLIS = 30;
    
    private static final int IDLE_TIMEOUT = 120000;

    private static final Logger LOG = LoggerFactory.getLogger(DiscardProtocol.class);
    
    /** Serve on this port */
    private Port port;
    
    /** 
     * Queues requests for recordings.
     * Also, used as lock.
     */
    private final Queue<Server> queue;
    
    private final Collection<Server> runningServers;
    
    /** 
     * Server socket when started, null otherwise.
     * Null indicates to the socket serving thread that the server is stopped.
     */
    private volatile ServerSocketChannel socket;
    

    public DiscardProtocol() {
        super();
        queue = new LinkedList<>();
        runningServers = new LinkedList<>();
    }
    
    /**
     * Starts serving.
     * @throws IOException when connection fails
     * @throws IllegalStateException when already started
     */
    public void start() throws IOException {
        synchronized (queue) {
            if (socket == null) {
                socket = ServerSocketChannel.open();
                socket.socket().bind(new InetSocketAddress(0));
                port = new Port(socket.socket().getLocalPort());
                // only going to record a single conversation
                socket.configureBlocking(false);
                
                final Thread socketMonitorThread = new Thread(new SocketMonitor());
                socketMonitorThread.start();
                
            } else {
                throw new IllegalStateException("Already started");
            }
        }
    }
    
    public Port getPort() {
        return port;
    }
    
    public Record recordNext() {
        synchronized (queue) {
            Server server = new Server();
            queue.add(server);
            return server;
        }
    }
    
    private void abort() {
        synchronized (queue) {
            stop();
            for (Server server: queue) {
                server.abort();
            }
            queue.clear();
        }
    }
    
    public void stop() {
        synchronized (queue) {
            try {
                if (socket != null) {
                    if (socket.isOpen()) {
                        socket.close();
                    }
                }
            } catch (IOException e) {
                LOG.warn("Failed to close socket", e);
            }
            socket = null;
            for (Server server: runningServers) {
                server.abort();
            }
        }
    }
    
    private final class SocketMonitor implements Runnable {
        @Override
        public void run() {
            try {
                long lastConnection = System.currentTimeMillis();
                while (socket != null) {
                    final SocketChannel socketChannel = socket.accept();
                    if (socketChannel == null) {
                        if (System.currentTimeMillis() - lastConnection > IDLE_TIMEOUT) {
                            throw new Exception("Idle timeout");
                        }
                        Thread.sleep(SOCKET_CONNECTION_WAIT_MILLIS);
                    } else {
                        synchronized (queue) {
                            Server nextServer = (Server) queue.poll();
                            if (nextServer == null) {
                                nextServer = new Server();
                            }
                            nextServer.setSocketChannel(socketChannel);
                            
                            final Thread channelThread = new Thread(nextServer);
                            channelThread.start();
                            runningServers.add(nextServer);
                            lastConnection = System.currentTimeMillis();
                        }
                    }
                }
            } catch (Exception e) {
                LOG.error("Cannot accept connection", e);
                abort();
            }
        }
    }

    public interface Record {
        /** Blocks until completion of conversation */
        String complete() throws Exception;
    }
    
    /**
     * Basic server.
     */
    private static final class Server implements Runnable, Record {

        private static final int COMPLETION_TIMEOUT = 60000;

        private static final int COMPLETION_PAUSE = 1000;

        private static final int INITIAL_BUFFER_CAPACITY = 2048;
        
        private final ByteBuffer buffer;
        /**
         * Safe for concurrent access by multiple threads.
         */
        private final StringBuffer out;
        
        /**
         * Initialised by setter 
         */
        private SocketChannel socketChannel;
        
        private volatile boolean aborted;
        private volatile boolean complete;
        
        public Server() {
            complete = false;
            out = new StringBuffer(INITIAL_BUFFER_CAPACITY);
            buffer = ByteBuffer.allocate(INITIAL_BUFFER_CAPACITY);
            aborted = false;
            socketChannel = null;
        }
        
        
        public void setSocketChannel(SocketChannel socketChannel) {
            this.socketChannel = socketChannel;
        }

        @Override
        public void run() {
            try {
                if (socketChannel == null) {
                    LOG.error("Socket channel must be set before instance is run.");
                } else {
                    try {
                        while (!socketChannel.finishConnect()) {
                            Thread.sleep(SOCKET_CONNECTION_WAIT_MILLIS);
                        }
                        
                        int read = 0;
                        while (!aborted && socketChannel.isOpen() && read >= 0) {
                            read = socketChannel.read(buffer);
                            if (!buffer.hasRemaining()) {
                                decant();
                            }
                        }
                        
                    } catch (Exception e) {
                        LOG.error("Socket communication failed", e);
                        aborted = true;
                        
                    // Tidy up
                    } finally {
                        try {
                            socketChannel.close();
                        } catch (Exception e) {
                            LOG.debug("Ignoring failure to close socket.", e);
                        }
                    }
                }
            } finally {
                synchronized (this) {
                    // Ensure completion is flagged
                    complete = true;
                    // Signal to any waiting threads 
                    notifyAll();
                }
            }
        }

        /**
         * Transfers all data from buffer to builder
         *
         */
        private void decant() {
            buffer.flip();
            final CharBuffer decoded = US_ASCII.decode(buffer);
            out.append(decoded);
            buffer.clear();
        }


        public void abort() {
            aborted = true;
        }
        
        /**
         * Blocks until connection is complete (closed)
         */
        @Override
        public synchronized String complete() throws Exception {
            if (aborted) {
                throw new Exception("Aborted");
            }
            final long startTime = System.currentTimeMillis();
            boolean isTimedOut = false;
            while (!complete  && !isTimedOut) {
                wait(COMPLETION_PAUSE);
                isTimedOut = (System.currentTimeMillis() - startTime) > COMPLETION_TIMEOUT;
            }
            if (isTimedOut && !complete) {
                throw new Exception("Timed out wait for be notified that read is complete");
            }
            decant();
            return out.toString();
        }        
    }
}
