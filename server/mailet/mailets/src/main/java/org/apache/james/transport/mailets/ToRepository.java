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

package org.apache.james.transport.mailets;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;

import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores incoming Mail in the specified Repository.<br>
 * If the "passThrough" in conf is true the mail will be returned untouched in
 * the pipe and may be processed by additional mailets. If false will be destroyed.
 */
public class ToRepository extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(ToRepository.class);

    private final MailRepositoryStore mailStore;

    private MailRepository repository;
    private MailRepositoryUrl repositoryPath;
    private boolean passThrough = false;

    @Inject
    public ToRepository(MailRepositoryStore mailStore) {
        this.mailStore = mailStore;
    }

    @Override
    public void init() throws MessagingException {
        repositoryPath = MailRepositoryUrl.from(getInitParameter("repositoryPath"));
        passThrough = getPassThroughParameter();
        repository = selectRepository();
    }

    private boolean getPassThroughParameter() {
        try {
            return getInitParameter("passThrough", false);
        } catch (Exception e) {
            return false;
        }
    }

    private MailRepository selectRepository() throws MessagingException {
        try {
            return mailStore.select(repositoryPath);
        } catch (Exception e) {
            throw new MessagingException("Failed to retrieve MailRepository for url " + repositoryPath, e);
        }
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        String logBuffer = "Storing mail " + mail.getName() + " in " + repositoryPath;
        LOGGER.info(logBuffer);
        repository.store(mail);
        if (!passThrough) {
            mail.setState(Mail.GHOST);
        }
    }

    @Override
    public String getMailetInfo() {
        return "ToRepository Mailet";
    }
}
