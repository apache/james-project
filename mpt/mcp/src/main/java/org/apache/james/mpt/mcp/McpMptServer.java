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

package org.apache.james.mpt.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpMptServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(McpMptServer.class);

    public static void main(String[] args) throws Exception {
        McpMptConfiguration configuration = McpMptConfiguration.fromEnvironment(System.getenv());
        LOGGER.info("Targets: {}", configuration.targets().values().stream().map(t -> t.name() + "=" + t.uri()).toList());
        McpHandler handler = new McpHandler(new ScriptRunner(configuration));
        switch (configuration.transport()) {
            case STDIO -> new StdioTransport(handler, System.in, System.out).serve();
            case HTTP -> {
                HttpTransport transport = new HttpTransport(handler, configuration.httpPort(), configuration.authToken()).start();
                Runtime.getRuntime().addShutdownHook(new Thread(transport::stop));
                Thread.currentThread().join();
            }
        }
    }
}
