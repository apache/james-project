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

package org.apache.james.mpt.app;

import java.io.File;
import java.io.FileInputStream;

import org.apache.james.mpt.Runner;
import org.apache.james.mpt.host.ExternalHostSystem;
import org.apache.james.mpt.monitor.SystemLoggingMonitor;
import org.apache.james.mpt.protocol.ProtocolSessionBuilder;

/**
 * Runs a single script.
 */
class RunScript {

    private final File file;
    private final int port;
    private final String host;
    private final String shabang;
    private final SystemLoggingMonitor monitor;
    
    public RunScript(final File file, final int port, final String host, final String shabang, final boolean verbose) {
        super();
        this.file = file;
        this.port = port;
        this.host = host;
        this.shabang = shabang;
        monitor = new SystemLoggingMonitor(verbose);
    }

    /**
     * Runs the script.
     */
    public void run() throws Exception {
       System.out.println("Running " + file + " against " + host + ":"  + port + "...");
       
       final ExternalHostSystem host = new ExternalHostSystem(this.host, port, monitor, shabang, null);
       final ProtocolSessionBuilder builder = new ProtocolSessionBuilder();
       final Runner runner = new Runner();
       
       builder.addProtocolLines(file.getName(), new FileInputStream(file), runner.getTestElements());
       runner.runSessions(host);
    }
}
