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

import java.net.InetSocketAddress;
import java.util.Objects;

public class ProxyInformation {
    private final InetSocketAddress source;
    private final InetSocketAddress destination;

    public ProxyInformation(InetSocketAddress source, InetSocketAddress destination) {
        this.source = source;
        this.destination = destination;
    }

    /**
     * Return the {@link InetSocketAddress} of the proxy peer or null if proxy support is disabled.
     */
    public InetSocketAddress getDestination() {
        return destination;
    }

    /**
     * Return the {@link InetSocketAddress} of the remote peer if proxy is enabled or null if proxy
     * support is disabled.
     */
    public InetSocketAddress getSource() {
        return source;
    }


    @Override
    public final int hashCode() {
        return Objects.hash(source, destination);
    }
}
