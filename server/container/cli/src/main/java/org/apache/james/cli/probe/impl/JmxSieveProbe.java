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

package org.apache.james.cli.probe.impl;

import java.io.IOException;

import javax.management.MalformedObjectNameException;

import org.apache.james.probe.SieveProbe;
import org.apache.james.sieverepository.api.SieveRepositoryManagementMBean;

public class JmxSieveProbe implements SieveProbe, JmxProbe {
    
    private static final String SIEVEMANAGER_OBJECT_NAME = "org.apache.james:type=component,name=sievemanagerbean";
    
    private SieveRepositoryManagementMBean sieveRepositoryManagement;
    
    @Override
    public JmxSieveProbe connect(JmxConnection jmxc) throws IOException {
        try {
            sieveRepositoryManagement = jmxc.retrieveBean(SieveRepositoryManagementMBean.class, SIEVEMANAGER_OBJECT_NAME);
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException("Invalid ObjectName? Please report this as a bug.", e);
        }
        return this;
    }

    @Override
    public long getSieveQuota() throws Exception {
        return sieveRepositoryManagement.getQuota();
    }

    @Override
    public void setSieveQuota(long quota) throws Exception {
        sieveRepositoryManagement.setQuota(quota);
    }

    @Override
    public void removeSieveQuota() throws Exception {
        sieveRepositoryManagement.removeQuota();
    }

    @Override
    public long getSieveQuota(String user) throws Exception {
        return sieveRepositoryManagement.getQuota(user);
    }

    @Override
    public void setSieveQuota(String user, long quota) throws Exception {
        sieveRepositoryManagement.setQuota(user, quota);
    }

    @Override
    public void removeSieveQuota(String user) throws Exception {
        sieveRepositoryManagement.removeQuota(user);
    }

    @Override
    public void addActiveSieveScript(String user, String name, String script) throws Exception {
        sieveRepositoryManagement.addActiveSieveScript(user, name, script);
    }

    @Override
    public void addActiveSieveScriptFromFile(String user, String name, String path) throws Exception {
        sieveRepositoryManagement.addActiveSieveScriptFromFile(user, name, path);
    }

}