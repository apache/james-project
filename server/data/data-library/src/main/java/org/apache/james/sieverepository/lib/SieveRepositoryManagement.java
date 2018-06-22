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

import javax.inject.Inject;
import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

import org.apache.james.core.User;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.SieveRepositoryManagementMBean;
import org.apache.james.sieverepository.api.exception.SieveRepositoryException;

public class SieveRepositoryManagement extends StandardMBean implements SieveRepositoryManagementMBean {

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
        sieveRepository.setDefaultQuota(QuotaSize.size(quota));
    }

    @Override
    public void removeQuota() throws SieveRepositoryException {
        sieveRepository.removeQuota();
    }

    @Override
    public long getQuota(String user) throws SieveRepositoryException {
        return sieveRepository.getQuota(User.fromUsername(user)).asLong();
    }

    @Override
    public void setQuota(String user, long quota) throws SieveRepositoryException {
        sieveRepository.setQuota(User.fromUsername(user), QuotaSize.size(quota));
    }

    @Override
    public void removeQuota(String user) throws SieveRepositoryException {
        sieveRepository.removeQuota(User.fromUsername(user));
    }
}
