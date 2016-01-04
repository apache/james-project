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

import org.apache.mailet.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.Matcher;

import java.util.Collection;
import java.util.Iterator;
import java.util.ArrayList;
import javax.mail.MessagingException;

public class Or extends GenericCompositeMatcher {

    /**
     * This is the Or CompositeMatcher - consider it to be a union of the
     * results. If any match results in a full set of recipients the matching is
     * short-circuited.
     * 
     * @return Collection of Recipient from the Or composition results of the
     *         child matchers.
     */
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        Collection<MailAddress> finalResult = null;
        Matcher matcher;
        boolean first = true;

        // the size of the complete set of recipients
        int size = mail.getRecipients().size();

        // Loop through until the finalResult is full or all the child matchers
        // have been executed
        for (Iterator<Matcher> matcherIter = iterator(); matcherIter.hasNext();) {
            matcher = matcherIter.next();
            // log("Matching with "
            // + matcher
            // .getMatcherConfig()
            // .getMatcherName()
            // );
            Collection<MailAddress> result = matcher.match(mail);
            if (first) {
                if (result == null) {
                    result = new ArrayList<MailAddress>(0);
                }
                finalResult = result;
                first = false;
            } else {
                // Check if we need to Or ...
                // if the finalResult and the subsequent result are the same
                // collection, then it contains the same recipients
                // so we can short-circuit building the OR of the two
                if (finalResult != result) {
                    if (result != null) {
                        if (finalResult == null) {
                            finalResult = result;
                        } else {
                            // the two results are different collections, so we
                            // must OR them
                            // Ensure that the finalResult only contains one
                            // copy of the recipients in the result collection
                            MailAddress recipient;
                            for (Object aResult : result) {
                                recipient = (MailAddress) aResult;
                                if (!finalResult.contains(recipient)) {
                                    System.out.println(recipient);
                                    finalResult.add(recipient);
                                }
                            }
                            recipient = null;
                        }
                    }
                }
            }
            if (finalResult.size() == size) {
                // we have a complete set of recipients, no need to OR in
                // anymore
                // i.e. short-circuit the Or
                break;
            }
            result = null;
            matcher = null;
        }
        // log("OrMatch: end.");
        return finalResult;
    }

}
