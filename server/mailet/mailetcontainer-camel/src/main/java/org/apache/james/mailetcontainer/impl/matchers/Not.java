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

package org.apache.james.mailetcontainer.impl.matchers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.apache.mailet.MailAddress;
import org.apache.mailet.Matcher;
import org.apache.mailet.Mail;

import javax.mail.MessagingException;

public class Not extends GenericCompositeMatcher {

    /**
     * This is the Not CompositeMatcher - consider what wasn't in the result set
     * of each child matcher. Of course it is easier to understand if it only
     * includes one matcher in the composition, the normal recommended use. @See
     * CompositeMatcher interface.
     * 
     * @return Collectiom of Recipient from the Negated composition of the child
     *         Matcher(s).
     */
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        Collection<MailAddress> finalResult = mail.getRecipients();
        Matcher matcher;
        for (Iterator<Matcher> matcherIter = iterator(); matcherIter.hasNext();) {
            matcher = (matcherIter.next());
            // log("Matching with " +
            // matcher.getMatcherConfig().getMatcherName());
            Collection<MailAddress> result = matcher.match(mail);
            if (result == finalResult) {
                // Not is an empty list
                finalResult = null;
            } else if (result != null) {
                finalResult = new ArrayList<MailAddress>(finalResult);
                finalResult.removeAll(result);
            }
        }
        return finalResult;
    }
}
