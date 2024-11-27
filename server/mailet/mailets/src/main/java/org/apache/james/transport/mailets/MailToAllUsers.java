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

import org.apache.james.core.Username;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;

/**
 * A mailet that helps to email all users in the system.
 *
 * <h3>Configuration</h3>
 * <pre><code>
 * {@code
 * <matcher name="notify-matcher" match="org.apache.james.mailetcontainer.impl.matchers.And">
 *     <matcher match="SenderIs=admin@gov.org"/>
 *     <matcher match="RecipientIs=all@gov.org"/>
 * </matcher>
 * <mailet match="notify-matcher" class="MailToAllUsers"/>
 * }
 * </code></pre>
 *
 */
public class MailToAllUsers extends GenericMailet {
    private final UsersRepository usersRepository;

    @Inject
    public MailToAllUsers(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        Flux.from(usersRepository.listReactive())
            .map(Throwing.function(Username::asMailAddress))
            .collectList()
            .doOnNext(mail::setRecipients)
            .block();
    }

    @Override
    public String getMailetName() {
        return "MailToAllUsers";
    }
}
