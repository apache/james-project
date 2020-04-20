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

package org.apache.james.protocols.api;

import java.net.InetAddress;
import java.net.UnknownHostException;


/**
 * Default implementation of a {@link ProtocolConfiguration} which allows to easily set the different configurations. 
 * 
 * Be aware that set and get operations are not thread-safe
 */
public class ProtocolConfigurationImpl implements ProtocolConfiguration {

    private static final String DEFAULT_HELLO_NAME;

    private final String softwareName;

    static {
        String hName;
        try {
            hName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hName = "localhost";
        }
        DEFAULT_HELLO_NAME = hName;
    }

    private ProtocolConfigurationImpl() {
        this("JAMES Protocols Server");
    }

    protected ProtocolConfigurationImpl(String softwareName) {
        this.softwareName = softwareName;
    }

    @Override
    public String getHelloName() {
        return DEFAULT_HELLO_NAME;
    }
    
    @Override
    public String getGreeting() {
        return null;
    }

    @Override
    public String getSoftwareName() {
        return softwareName;
    }

}
