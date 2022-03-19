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

import jakarta.mail.MessagingException;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.consumers.ThrowingConsumer;

/**
 * Stores incoming Mail in a repository defined by the sender's domain.<br>
 *
 * Supported configuration parameters:
 *
 *  - "urlPrefix" mandatory: defines the prefix for the per sender's domain repository.
 *  For example for the value 'cassandra://var/mail/sendersRepositories/', a mail sent by 'user@james.org'
 *  will be stored in 'cassandra://var/mail/sendersRepositories/james.org'.
 *  - "passThrough" optional, defaults to false. If true, the processing of the mail continues. If false it stops.
 *  - "allowRepositoryCreation" optional, defaults to true. If true, non existing repository will be created. In case of
 *  misconfiguration, this might lead to arbitrary repository creation. If false, the incoming mails will be stored only
 *  in already existing repository. If not existing, the email will be dropped with an appropriate log warning (leading
 *  to potential data loss). In case, you want to create a repository manually, make a http PUT request to
 *  /mailRepositories/encodedUrlOfTheRepository from web admin api.
 *  For example http://ip:port/mailRepositories/file%3A%2F%2FmailRepo
 *  @see <a href="https://james.apache.org/server/manage-webadmin.html">Create a mail repository</a>
 *
 *  Example:
 *
 * &lt;mailet match="All" class="ToSenderDomainRepository"&gt;
 *     &lt;urlPrefix&gt;cassandra://var/mail/sendersRepositories/&lt;/urlPrefix&gt;
 *     &lt;passThrough&gt;false&lt;/passThrough&gt;
 *     &lt;allowRepositoryCreation&gt;true&lt;/allowRepositoryCreation&gt;
 * &lt;/mailet&gt;
 */
public class ToSenderDomainRepository extends GenericMailet {
    public static final String URL_PREFIX = "urlPrefix";
    public static final String PASS_THROUGH = "passThrough";
    public static final String ALLOW_REPOSITORY_CREATION = "allowRepositoryCreation";

    private static final Logger LOGGER = LoggerFactory.getLogger(ToSenderDomainRepository.class);
    private static final boolean DEFAULT_CONSUME = false;
    private static final boolean DEFAULT_ALLOW_REPOSITORY_CREATION = true;

    private final MailRepositoryStore mailRepositoryStore;
    private MailRepositoryUrl urlPrefix;
    private boolean passThrough;
    private boolean allowRepositoryCreation;

    @Inject
    ToSenderDomainRepository(MailRepositoryStore mailRepositoryStore) {
        this.mailRepositoryStore = mailRepositoryStore;
    }

    @Override
    public void init() throws MessagingException {
        urlPrefix = Optional.ofNullable(getInitParameter(URL_PREFIX))
            .map(MailRepositoryUrl::from)
            .orElseThrow(() -> new MessagingException("'urlPrefix' is a mandatory configuration property"));
        passThrough = getInitParameter(PASS_THROUGH, DEFAULT_CONSUME);
        allowRepositoryCreation = getInitParameter(ALLOW_REPOSITORY_CREATION, DEFAULT_ALLOW_REPOSITORY_CREATION);
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        String domain = mail.getMaybeSender()
            .asOptional()
            .map(MailAddress::getDomain)
            .map(Domain::asString)
            .orElse("");

        MailRepositoryUrl repositoryUrl = urlPrefix.subUrl(domain);
        store(mail, repositoryUrl);
        if (!passThrough) {
            mail.setState(Mail.GHOST);
        }
    }

    private void store(Mail mail, MailRepositoryUrl url) throws MessagingException {
        try {
            Optional<MailRepository> mailRepository = retrieveRepository(url);
            if (!mailRepository.isPresent()) {
                LOGGER.warn("'{}' mail repository does not exist and will not be created. Mail {} will not be stored in it.",
                    url, mail.getName());
            }
            ThrowingConsumer<MailRepository> storingConsumer = repository -> repository.store(mail);
            mailRepository.ifPresent(Throwing.consumer(storingConsumer).sneakyThrow());
        } catch (MailRepositoryStore.MailRepositoryStoreException e) {
            throw new MessagingException("Error while selecting url " + url, e);
        }
    }

    private Optional<MailRepository> retrieveRepository(MailRepositoryUrl url) throws MailRepositoryStore.MailRepositoryStoreException {
        if (allowRepositoryCreation) {
            return Optional.of(mailRepositoryStore.select(url));
        } else {
            return mailRepositoryStore.get(url);
        }
    }

    @Override
    public String getMailetInfo() {
        return "ToSenderDomainRepository Mailet";
    }
}
