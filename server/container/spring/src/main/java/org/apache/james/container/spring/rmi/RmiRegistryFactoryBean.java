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
package org.apache.james.container.spring.rmi;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import org.apache.james.util.RestrictingRMISocketFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;

public class RmiRegistryFactoryBean implements FactoryBean<Registry>, DisposableBean {
    private final Registry registry;

    public RmiRegistryFactoryBean(int port, RestrictingRMISocketFactory restrictingRMISocketFactory) throws Exception {
        registry = LocateRegistry.createRegistry(port, restrictingRMISocketFactory, restrictingRMISocketFactory);
    }

    @Override
    public Registry getObject() throws Exception {
        return registry;
    }

    @Override
    public Class<?> getObjectType() {
        return Registry.class;
    }

    @Override
    public void destroy() throws RemoteException {
        UnicastRemoteObject.unexportObject(registry, true);
    }
}
