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

import java.util.Optional;

import javax.inject.Inject;
import javax.mail.MessagingException;

import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

/**
 * Stores incoming Mail in a repository defined by the sender's domain.<br>
 *
 * Supported configuration parameters:
 *
 *  - "urlPrefix" mandatory: defines the prefix for the per sender's domain repository.
 *  For example for the value 'cassandra://var/mail/sendersRepositories/', a mail sent by 'user@james.org'
 *  will be stored in 'cassandra://var/mail/sendersRepositories/james.org'.
 *  - "passThrough" optional, defaults to false. If true, the processing of the mail continues. If false it stops.
 *
 *  Example:
 *
 * &lt;mailet matcher="All" class="ToSenderDomainRepository"&gt;
 *     &lt;urlPrefix&gt;cassandra://var/mail/sendersRepositories/&lt;/urlPrefix&gt;
 *     &lt;passThrough&gt;false&lt;/passThrough&gt;
 * &lt;/mailet&gt;
 */
public class ToSenderDomainRepository extends GenericMailet {

    private static final boolean DEFAULT_CONSUME = false;

    private final MailRepositoryStore mailRepositoryStore;
    private String urlPrefix;
    private boolean passThrough;

    @Inject
    public ToSenderDomainRepository(MailRepositoryStore mailRepositoryStore) {
        this.mailRepositoryStore = mailRepositoryStore;
    }

    @Override
    public void init() throws MessagingException {
        urlPrefix = Optional.ofNullable(getInitParameter("urlPrefix"))
            .orElseThrow(() -> new MessagingException("'urlPrefix' is a mandatory configuration property"));
        passThrough = getInitParameter("passThrough", DEFAULT_CONSUME);
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        String url = urlPrefix + mail.getSender().getDomain().asString();
        store(mail, url);
        if (!passThrough) {
            mail.setState(Mail.GHOST);
        }
    }

    private void store(Mail mail, String url) throws MessagingException {
        try {
            mailRepositoryStore.select(url).store(mail);
        } catch (MailRepositoryStore.MailRepositoryStoreException e) {
            throw new MessagingException("Error while selecting url " + url, e);
        }
    }

    @Override
    public String getMailetInfo() {
        return "ToSenderDomainRepository Mailet";
    }
}
