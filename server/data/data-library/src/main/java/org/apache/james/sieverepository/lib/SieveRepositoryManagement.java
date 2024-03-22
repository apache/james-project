/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.sieverepository.lib;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

import jakarta.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.sieverepository.api.ScriptContent;
import org.apache.james.sieverepository.api.ScriptName;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.SieveRepositoryManagementMBean;
import org.apache.james.sieverepository.api.exception.SieveRepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SieveRepositoryManagement extends StandardMBean implements SieveRepositoryManagementMBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(SieveRepositoryManagement.class);

    private SieveRepository sieveRepository;

    public SieveRepositoryManagement() throws NotCompliantMBeanException {
        super(SieveRepositoryManagementMBean.class);
    }

    @Inject
    public void setSieveRepository(SieveRepository sieveRepository) {
        this.sieveRepository = sieveRepository;
    }

    @Override
    public long getQuota() throws SieveRepositoryException {
        return sieveRepository.getDefaultQuota().asLong();
    }

    @Override
    public void setQuota(long quota) throws SieveRepositoryException {
        sieveRepository.setDefaultQuota(QuotaSizeLimit.size(quota));
    }

    @Override
    public void removeQuota() throws SieveRepositoryException {
        sieveRepository.removeQuota();
    }

    @Override
    public long getQuota(String user) throws SieveRepositoryException {
        return sieveRepository.getQuota(Username.of(user)).asLong();
    }

    @Override
    public void setQuota(String user, long quota) throws SieveRepositoryException {
        sieveRepository.setQuota(Username.of(user), QuotaSizeLimit.size(quota));
    }

    @Override
    public void removeQuota(String user) throws SieveRepositoryException {
        sieveRepository.removeQuota(Username.of(user));
    }

    @Override
    public void addActiveSieveScript(String userName, String scriptName, String script) throws SieveRepositoryException {
        Username username = Username.of(userName);
        sieveRepository.putScript(username, new ScriptName(scriptName), new ScriptContent(script));
        sieveRepository.setActive(username, new ScriptName(scriptName));
    }

    @Override
    public void addActiveSieveScriptFromFile(String userName, String scriptName, String scriptPath) throws SieveRepositoryException {
        try (InputStream scriptFileAsStream = new FileInputStream(scriptPath)) {
            addActiveSieveScript(userName, scriptName, IOUtils.toString(scriptFileAsStream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOGGER.error("Error while reading sieve script from file {}", scriptPath, e);
        }
    }
}
