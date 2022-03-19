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


package org.apache.mailet.base;

import java.util.ArrayList;
import java.util.Collection;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.Matcher;
import org.apache.mailet.MatcherConfig;

/**
 * This class can be used as a wrapper for getting the "not matched" recipients
 * 
 */
public class MatcherInverter implements Matcher {

    private final Matcher wrappedMatcher;

    public MatcherInverter(Matcher wrappedMatcher) {
        this.wrappedMatcher = wrappedMatcher;
    }

    @Override
    public void destroy() {
        wrappedMatcher.destroy();
    }

    @Override
    public MatcherConfig getMatcherConfig() {
        return wrappedMatcher.getMatcherConfig();
    }

    @Override
    public String getMatcherInfo() {
        return wrappedMatcher.getMatcherInfo();
    }

    @Override
    public void init(MatcherConfig config) throws MessagingException {
        wrappedMatcher.init(config);
    }

    /**
     * Return a Collection of "not matched" recipients
     *
     */
    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        // Create a new recipient Collection cause mail.getRecipients() give a reference to the internal 
        // list of recipients. If we make changes there the original collection whould be corrupted
        Collection<MailAddress> recipients = new ArrayList<>(mail.getRecipients());
        Collection<MailAddress> matchedRcpts = wrappedMatcher.match(mail);
        
        // check if a only a part of the recipients matched
        if (matchedRcpts != null) {
            recipients.removeAll(matchedRcpts);
            if (recipients.isEmpty()) {
                return null;
            }
        }
       
        return recipients;
    }

}
