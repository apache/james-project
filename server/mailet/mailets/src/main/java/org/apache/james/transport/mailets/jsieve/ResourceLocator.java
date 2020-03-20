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
package org.apache.james.transport.mailets.jsieve;

import java.io.InputStream;
import java.time.ZonedDateTime;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;

public class ResourceLocator {

    public static class UserSieveInformation {
        private ZonedDateTime scriptActivationDate;
        private ZonedDateTime scriptInterpretationDate;
        private InputStream scriptContent;

        public UserSieveInformation(ZonedDateTime scriptActivationDate, ZonedDateTime scriptInterpretationDate, InputStream scriptContent) {
            this.scriptActivationDate = scriptActivationDate;
            this.scriptInterpretationDate = scriptInterpretationDate;
            this.scriptContent = scriptContent;
        }

        public ZonedDateTime getScriptActivationDate() {
            return scriptActivationDate;
        }

        public ZonedDateTime getScriptInterpretationDate() {
            return scriptInterpretationDate;
        }

        public InputStream getScriptContent() {
            return scriptContent;
        }
    }

    private final SieveRepository sieveRepository;
    private final UsersRepository usersRepository;

    public ResourceLocator(SieveRepository sieveRepository, UsersRepository usersRepository) {
        this.sieveRepository = sieveRepository;
        this.usersRepository = usersRepository;
    }

    public UserSieveInformation get(MailAddress mailAddress) throws Exception {
        Username username = retrieveUsername(mailAddress);
        return new UserSieveInformation(sieveRepository.getActivationDateForActiveScript(username), ZonedDateTime.now(), sieveRepository.getActive(username));
    }

    private Username retrieveUsername(MailAddress mailAddress) {
        try {
            return usersRepository.getUsername(mailAddress);
        } catch (UsersRepositoryException e) {
            return Username.fromMailAddress(mailAddress);
        }
    }

}
