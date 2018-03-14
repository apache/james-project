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

package org.apache.james.mpt.monitor;

import org.apache.james.mpt.api.Monitor;

/**
 * Feeds monitored information to {@link System#out}.
 */
public final class SystemLoggingMonitor implements Monitor {

    private boolean verbose = false;

    public SystemLoggingMonitor() {
        this(false);
    }

    public SystemLoggingMonitor(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public void note(String message) {
        System.out.println(message);
    }

    @Override
    public void debug(char character) {
        if (verbose) {
            System.out.print(character);
        }
    }

    @Override
    public void debug(String message) {
        if (verbose) {
            System.out.println(message);
        }
    }

}
