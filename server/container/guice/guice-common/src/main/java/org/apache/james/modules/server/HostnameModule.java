/**
 * *************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/

package org.apache.james.modules.server;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.james.task.eventsourcing.Hostname;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

public class HostnameModule extends AbstractModule {
    private static class UnconfigurableHostnameException extends RuntimeException {
        UnconfigurableHostnameException(String message, Exception originException) {
            super(message, originException);
        }
    }

    @Override
    protected void configure() {
        bind(Hostname.class).in(Scopes.SINGLETON);
        bind(Hostname.class).toInstance(getHostname());
    }

    private Hostname getHostname() {
        try {
            return new Hostname(InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException e) {
            throw new UnconfigurableHostnameException("Hostname can not be retrieved, unable to initialize the distributed task manager", e);
        }
    }
}
