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

package org.apache.james.backends.rabbitmq;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Accepts TCP connections but never answers, as a stalled RabbitMQ server or management API.
 */
class UnresponsiveServer implements AutoCloseable {
    private final ServerSocket serverSocket;
    private final List<Socket> acceptedSockets = new CopyOnWriteArrayList<>();
    private final Thread acceptingThread;

    UnresponsiveServer() throws IOException {
        serverSocket = new ServerSocket(0);
        acceptingThread = new Thread(() -> {
            try {
                while (!serverSocket.isClosed()) {
                    acceptedSockets.add(serverSocket.accept());
                }
            } catch (IOException e) {
                // the server is closed
            }
        });
        acceptingThread.setDaemon(true);
        acceptingThread.start();
    }

    URI uri(String scheme) {
        return URI.create(scheme + "://localhost:" + serverSocket.getLocalPort());
    }

    RabbitMQConfiguration configuration() {
        return RabbitMQConfiguration.builder()
            .amqpUri(uri("amqp"))
            .managementUri(uri("http"))
            .managementCredentials(RabbitMQFixture.DEFAULT_MANAGEMENT_CREDENTIAL)
            .build();
    }

    int acceptedConnections() {
        return acceptedSockets.size();
    }

    @Override
    public void close() throws IOException, InterruptedException {
        serverSocket.close();
        acceptingThread.join();
        for (Socket socket : acceptedSockets) {
            socket.close();
        }
    }
}
