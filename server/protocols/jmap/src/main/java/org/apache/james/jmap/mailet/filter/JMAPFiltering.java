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

package org.apache.james.jmap.mailet.filter;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.core.MailAddress;
import org.apache.james.core.User;
import org.apache.james.jmap.api.filtering.FilteringManagement;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JMAPFiltering extends GenericMailet {

    private final Logger logger = LoggerFactory.getLogger(JMAPFiltering.class);

    private final FilteringManagement filteringManagement;
    private final UsersRepository usersRepository;
    private final ActionApplier.Factory actionApplierFactory;

    @Inject
    public JMAPFiltering(FilteringManagement filteringManagement,
                         UsersRepository usersRepository, ActionApplier.Factory actionApplierFactory) {

        this.filteringManagement = filteringManagement;
        this.usersRepository = usersRepository;
        this.actionApplierFactory = actionApplierFactory;
    }

    @Override
    public void service(Mail mail) {
        mail.getRecipients()
            .forEach(recipient -> filteringForRecipient(mail, recipient));
    }

    private void filteringForRecipient(Mail mail, MailAddress recipient) {
        Optional<User> maybeUser = retrieveUser(recipient);
        maybeUser
            .ifPresent(user -> findFirstApplicableRule(user, mail));
    }

    private void findFirstApplicableRule(User user, Mail mail) {
        List<Rule> filteringRules = filteringManagement.listRulesForUser(user);
        RuleMatcher ruleMatcher = new RuleMatcher(filteringRules);
        Stream<Rule> matchingRules = ruleMatcher.findApplicableRules(mail);

        actionApplierFactory.forMail(mail)
            .forUser(user)
            .apply(matchingRules.map(Rule::getAction));
    }

    private Optional<User> retrieveUser(MailAddress recipient) {
        try {
            return Optional.ofNullable(User.fromUsername(usersRepository.getUser(recipient)));
        } catch (UsersRepositoryException e) {
            logger.error("cannot retrieve user " + recipient.asString(), e);
            return Optional.empty();
        }
    }
}
