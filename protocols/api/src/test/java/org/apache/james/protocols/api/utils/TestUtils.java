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
package org.apache.james.protocols.api.utils;

import java.io.IOException;
import java.net.ServerSocket;

public class TestUtils {

    private static final int START_PORT = 20000;
    private static final int END_PORT = 30000;
    
    /**
     * Return a free port which can be used to bind to
     * 
     * @return port
     */
    public static synchronized int getFreePort() {
        for (int start = START_PORT; start <= END_PORT; start++) {
            try {
                ServerSocket socket = new ServerSocket(start);
                socket.setReuseAddress(true);
                socket.close();
                return start;
            } catch (IOException e) {
                // ignore 
            }
            
        }
        throw new RuntimeException("Unable to find a free port....");
    }
}
