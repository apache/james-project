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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;

import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.mailet.Attribute;
import org.apache.mailet.Experimental;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Re-spools Mail found in the specified Repository.
 * 
 * <pre>
 * &lt;mailet match="RecipientIs=respool@localhost" class="FromRepository"&gt;
 *    &lt;repositoryPath&gt; <i>repository path</i> &lt;/repositoryPath&gt;
 *    &lt;processor&gt; <i>target processor</i> &lt;/repositoryPath&gt;
 *    &lt;delete&t; [true|<b>false</b>] &lt;/delete&gt;
 * &lt;/mailet&gt;
 * </pre>
 */
@Experimental
public class FromRepository extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(FromRepository.class);

    private final MailRepositoryStore mailStore;

    /** The repository from where this mailet spools mail. */
    private MailRepository repository;

    /** Whether this mailet should delete messages after being spooled */
    private boolean delete = false;

    /** The path to the repository */
    private MailRepositoryUrl repositoryPath;

    /** The processor that will handle the re-spooled message(s) */
    private String processor;

    @Inject
    public FromRepository(MailRepositoryStore mailStore) {
        this.mailStore = mailStore;
    }

    @Override
    public void init() throws MessagingException {
        repositoryPath = MailRepositoryUrl.from(getInitParameter("repositoryPath"));
        processor = (getInitParameter("processor") == null) ? Mail.DEFAULT : getInitParameter("processor");

        try {
            delete = (getInitParameter("delete") == null) ? false : Boolean.parseBoolean(getInitParameter("delete"));
        } catch (Exception e) {
            // Ignore exception, default to false
        }

        try {
            repository = mailStore.select(repositoryPath);
        } catch (Exception e) {
            throw new MessagingException("Failed to retrieve MailRepository for url " + repositoryPath, e);
        }
    }

    /**
     * Spool mail from a particular repository.
     * 
     * @param trigger
     *            triggering e-mail (eventually parameterize via the trigger
     *            message)
     */
    @Override
    public void service(Mail trigger) throws MessagingException {
        trigger.setState(Mail.GHOST);
        Collection<MailKey> processed = new ArrayList<>();
        Iterator<MailKey> list = repository.list();
        while (list.hasNext()) {
            MailKey key = list.next();
            try {
                Mail mail = repository.retrieve(key);
                if (mail != null && mail.getRecipients() != null) {
                    LOGGER.debug("Spooling mail {} from {}", mail.getName(), repositoryPath);

                    mail.setAttribute(Attribute.convertToAttribute("FromRepository", Boolean.TRUE));
                    mail.setState(processor);
                    getMailetContext().sendMail(mail);
                    if (delete) {
                        processed.add(key);
                    }
                    LifecycleUtil.dispose(mail);
                }
            } catch (MessagingException e) {
                LOGGER.error("Unable to re-spool mail {} from {}", key, repositoryPath, e);
            }
        }

        if (delete) {
            for (Object aProcessed : processed) {
                repository.remove((MailKey) aProcessed);
            }
        }
    }

    @Override
    public String getMailetInfo() {
        return "FromRepository Mailet";
    }
}
