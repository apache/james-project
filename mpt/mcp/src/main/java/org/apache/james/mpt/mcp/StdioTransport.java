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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MCP stdio transport: newline delimited JSON-RPC messages on stdin/stdout. Logs go to stderr.
 */
public class StdioTransport {
    private final McpHandler handler;
    private final InputStream in;
    private final PrintStream out;

    public StdioTransport(McpHandler handler, InputStream in, OutputStream out) {
        this.handler = handler;
        this.in = in;
        this.out = new PrintStream(out, false, UTF_8);
    }

    public void serve() throws IOException {
        // Requests are handled concurrently so that a long script run does not block pings or other calls
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String message = line;
                executor.submit(() -> handler.handle(message).ifPresent(this::send));
            }
        } finally {
            executor.shutdown();
        }
    }

    private synchronized void send(String response) {
        out.print(response);
        out.print('\n');
        out.flush();
    }
}
