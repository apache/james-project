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

import javax.inject.Inject;
import javax.mail.MessagingException;

import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.transport.mailets.managesieve.ManageSieveMailet;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(ManageSieveMailet.class);

    /** The repository from where this mailet spools mail. */
    private MailRepository repository;

    /** Whether this mailet should delete messages after being spooled */
    private boolean delete = false;

    /** The path to the repository */
    private String repositoryPath;

    /** The processor that will handle the re-spooled message(s) */
    private String processor;

    private MailRepositoryStore mailStore;

    @Inject
    public void setStore(MailRepositoryStore mailStore) {
        this.mailStore = mailStore;
    }

    /**
     * Initialize the mailet, loading configuration information.
     */
    public void init() throws MessagingException {
        repositoryPath = getInitParameter("repositoryPath");
        processor = (getInitParameter("processor") == null) ? Mail.DEFAULT : getInitParameter("processor");

        try {
            delete = (getInitParameter("delete") == null) ? false : Boolean.valueOf(getInitParameter("delete"));
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
    public void service(Mail trigger) throws MessagingException {
        trigger.setState(Mail.GHOST);
        Collection<String> processed = new ArrayList<>();
        Iterator<String> list = repository.list();
        while (list.hasNext()) {
            String key = (String) list.next();
            try {
                Mail mail = repository.retrieve(key);
                if (mail != null && mail.getRecipients() != null) {
                    LOGGER.debug((new StringBuffer(160).append("Spooling mail ").append(mail.getName()).append(" from ").append(repositoryPath)).toString());

                    mail.setAttribute("FromRepository", Boolean.TRUE);
                    mail.setState(processor);
                    getMailetContext().sendMail(mail);
                    if (delete)
                        processed.add(key);
                    LifecycleUtil.dispose(mail);
                }
            } catch (MessagingException e) {
                LOGGER.error((new StringBuffer(160).append("Unable to re-spool mail ").append(key).append(" from ").append(repositoryPath)).toString(), e);
            }
        }

        if (delete) {
            for (Object aProcessed : processed) {
                repository.remove((String) aProcessed);
            }
        }
    }

    /**
     * Return a string describing this mailet.
     * 
     * @return a string describing this mailet
     */
    public String getMailetInfo() {
        return "FromRepository Mailet";
    }
}
