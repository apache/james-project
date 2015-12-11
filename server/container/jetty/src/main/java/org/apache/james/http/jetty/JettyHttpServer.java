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

package org.apache.james.http.jetty;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

public class JettyHttpServer {

    public static class Configuration {
        
    }
    
    public static JettyHttpServer start(Configuration configuration) throws Exception {
        return new JettyHttpServer().start();
    }

    private Server server;
    private ServerConnector serverConnector;

    private JettyHttpServer() {
        server = new Server();
        serverConnector = new ServerConnector(server);
        server.addConnector(serverConnector);
    }
    
    private JettyHttpServer start() throws Exception {
        server.start();
        return this;
    }
    
    public void stop() {
    }

    public int getPort() {
        return serverConnector.getLocalPort();
    }
    
}
