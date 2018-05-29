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
package org.apache.james.smtpserver;

import javax.inject.Inject;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.core.AbstractSenderAuthIdentifyVerificationRcptHook;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;

/**
 * Handler which check if the authenticated user is incorrect
 */
public class SenderAuthIdentifyVerificationRcptHook extends AbstractSenderAuthIdentifyVerificationRcptHook {

    private DomainList domains;
    private UsersRepository users;

    @Inject
    public final void setUsersRepository(UsersRepository users) {
        this.users = users;
    }

    @Inject
    public void setDomainList(DomainList domains) {
        this.domains = domains;
    }

    @Override
    public void init(Configuration config) throws ConfigurationException {

    }

    @Override
    public void destroy() {

    }

    @Override
    public HookResult doRcpt(SMTPSession session, MailAddress sender, MailAddress rcpt) {
        ExtendedSMTPSession nSession = (ExtendedSMTPSession) session;
        if (nSession.verifyIdentity()) {
            return super.doRcpt(session, sender, rcpt);
        } else {
            return HookResult.DECLINED;
        }
    }

    @Override
    protected boolean isLocalDomain(Domain domain) {
        try {
            return domains.containsDomain(domain);
        } catch (DomainListException e) {
            return false;
        }
    }

    @Override
    protected String getUser(MailAddress mailAddress) {
        try {
            return users.getUser(mailAddress);
        } catch (UsersRepositoryException e) {
            throw new RuntimeException(e);
        }
    }

}
