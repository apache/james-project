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
package org.apache.james.mpt.imapmailbox.cyrus.host;

import org.apache.james.mpt.api.UserAdder;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.spotify.docker.client.messages.ContainerCreation;

@Singleton
public class CyrusUserAdder implements UserAdder {

    private final Provider<ContainerCreation> container;
    private final Docker docker;

    @Inject
    private CyrusUserAdder(Docker docker, Provider<ContainerCreation> container) {
        this.docker = docker;
        this.container = container;
    }

    @Override
    public void addUser(String user, String password) throws Exception {
        this.docker.createUser(container.get(), user, password);
    }
}