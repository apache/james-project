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

package org.apache.james.mpt.host;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.managesieve.core.CoreProcessor;
import org.apache.james.managesieve.jsieve.Parser;
import org.apache.james.managesieve.transcode.ArgumentParser;
import org.apache.james.managesieve.transcode.ManageSieveProcessor;
import org.apache.james.mpt.api.Continuation;
import org.apache.james.mpt.api.Session;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.user.api.UsersRepository;
import org.apache.jsieve.ConfigurationManager;

public abstract class JamesManageSieveHostSystem implements ManageSieveHostSystem {

    private UsersRepository usersRepository;
    private SieveRepository sieveRepository;
    private ManageSieveProcessor processor;

    @Override
    public void beforeTest() throws Exception {
        this.usersRepository = createUsersRepository();
        this.sieveRepository = createSieveRepository();
        this.processor = new ManageSieveProcessor(new ArgumentParser(new CoreProcessor(sieveRepository, usersRepository, new Parser(new ConfigurationManager()))));
    }

    @Override
    public void afterTest() throws Exception {
    }
    
    protected abstract SieveRepository createSieveRepository() throws Exception;

    protected abstract UsersRepository createUsersRepository();

    @Override
    public boolean addUser(Username username, String password) throws Exception {
        usersRepository.addUser(username, password);
        return true;
    }

    @Override
    public void setMaxQuota(String user, long value) throws Exception {
        sieveRepository.setQuota(Username.of(user), QuotaSizeLimit.size(value));
    }

    @Override
    public Session newSession(Continuation continuation) {
        return new ManageSieveSession(processor, continuation);
    }

}
