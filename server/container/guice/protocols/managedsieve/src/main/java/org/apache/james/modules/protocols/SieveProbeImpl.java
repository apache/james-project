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
package org.apache.james.modules.protocols;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.probe.SieveProbe;
import org.apache.james.sieverepository.api.ScriptContent;
import org.apache.james.sieverepository.api.ScriptName;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.utils.GuiceProbe;


public class SieveProbeImpl implements GuiceProbe, SieveProbe {

    private final SieveRepository sieveRepository;

    @Inject
    private SieveProbeImpl(SieveRepository sieveRepository) {
        this.sieveRepository = sieveRepository;
    }

    @Override
    public long getSieveQuota() throws Exception {
        return sieveRepository.getDefaultQuota().asLong();
    }

    @Override
    public void setSieveQuota(long quota) throws Exception {
        sieveRepository.setDefaultQuota(QuotaSizeLimit.size(quota));
    }

    @Override
    public void removeSieveQuota() throws Exception {
        sieveRepository.removeQuota();
    }

    @Override
    public long getSieveQuota(String user) throws Exception {
        return sieveRepository.getQuota(Username.of(user)).asLong();
    }

    @Override
    public void setSieveQuota(String user, long quota) throws Exception {
        sieveRepository.setQuota(Username.of(user), QuotaSizeLimit.size(quota));
    }

    @Override
    public void removeSieveQuota(String user) throws Exception {
        sieveRepository.removeQuota(Username.of(user));
    }

    @Override
    public void addActiveSieveScript(String userName, String name, String script) throws Exception {
        Username username = Username.of(userName);
        sieveRepository.putScript(username, new ScriptName(name), new ScriptContent(script));
        sieveRepository.setActive(username, new ScriptName(name));
    }
}
