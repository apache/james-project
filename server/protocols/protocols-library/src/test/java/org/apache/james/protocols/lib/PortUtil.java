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

import java.io.IOException;
import java.net.ServerSocket;

/**
 * some utilities for James unit testing
 */
public class PortUtil {

    //the lowest possible port number assigned for testing
    private static final int PORT_RANGE_START = 8000;
    // the highest possible port number assigned for testing
    private static final int PORT_RANGE_END = 11000; 
    private static int PORT_LAST_USED = PORT_RANGE_START;

    /**
     * assigns a port from the range of test ports
     * 
     * @return port number
     */
    public static int getNonPrivilegedPort() {
        // uses sequential assignment of ports
        return getNextNonPrivilegedPort(); 
    }

    /**
     * assigns a random port from the range of test ports
     * 
     * @return port number
     */
    protected static int getRandomNonPrivilegedPortInt() {
        return ((int) (Math.random() * (PORT_RANGE_END - PORT_RANGE_START) + PORT_RANGE_START));
    }

    /**
     * assigns ports sequentially from the range of test ports
     * 
     * @return port number
     */
    protected static synchronized int getNextNonPrivilegedPort() {
        // Hack to increase probability that the port is bindable
        int nextPortCandidate = PORT_LAST_USED;
        while (true) {
            try {
                nextPortCandidate++;
                if (PORT_LAST_USED == nextPortCandidate) {
                    throw new RuntimeException("no free port found");
                }
                if (nextPortCandidate > PORT_RANGE_END) {
                    nextPortCandidate = PORT_RANGE_START; // start over
                }

                // test, port is available
                ServerSocket ss;
                ss = new ServerSocket(nextPortCandidate);
                ss.setReuseAddress(true);
                ss.close();
                break;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        PORT_LAST_USED = nextPortCandidate;
        return PORT_LAST_USED;
    }

}
