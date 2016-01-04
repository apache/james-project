package org.apache.james.mailetcontainer.impl.matchers;

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

import java.util.Collection;
import java.util.Iterator;
import java.util.ArrayList;

import org.apache.mailet.MailAddress;
import org.apache.mailet.Mail;
import javax.mail.MessagingException;
import org.apache.mailet.Matcher;

/**
 * This matcher performs And conjunction between the two recipients
 */
public class And extends GenericCompositeMatcher {

    /**
     * This is the And CompositeMatcher - consider it to be an intersection of
     * the results. If any match returns an empty recipient result the matching
     * is short-circuited.
     * 
     * @return Collection of Recipient from the And composition results of the
     *         child Matchers.
     */
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        Collection<MailAddress> finalResult = null;
        Matcher matcher;
        boolean first = true;
        for (Iterator<Matcher> matcherIter = iterator(); matcherIter.hasNext();) {
            matcher = (matcherIter.next());
            Collection<MailAddress> result = matcher.match(mail);

            if (result == null) {
                // short-circuit
                // log("Matching with " +
                // matcher.getMatcherConfig().getMatcherName() +
                // " result.size()=0");
                return new ArrayList<MailAddress>(0);
            }
            if (result.size() == 0) {
                return result;
            }

            // log("Matching with " +
            // matcher.getMatcherConfig().getMatcherName() +
            // " result.size()="+result.size());

            if (first) {
                finalResult = result;
                first = false;
            } else {
                // Check if we need to And ...
                // if the finalResult and the subsequent result are the same
                // collection, then it contains the same recipients
                // so we can short-circuit building the AND of the two
                if (finalResult != result) {
                    // the two results are different collections, so we AND
                    // them
                    // Ensure that the finalResult only contains recipients
                    // in the result collection
                    Collection<MailAddress> newResult = new ArrayList<MailAddress>();
                    MailAddress recipient;
                    for (Object aFinalResult : finalResult) {
                        recipient = (MailAddress) aFinalResult;
                        // log("recipient="+recipient.toString());
                        if (result.contains(recipient)) {
                            newResult.add(recipient);
                        }
                    }
                    recipient = null;
                    // basically the finalResult gets replaced with a
                    // smaller result
                    // otherwise finalResult would have been equal to result
                    // (in all cases)
                    finalResult = newResult;
                }
            }
            result = null;
        }
        matcher = null;
        // log("answer is "+finalResult.toString());
        return finalResult;
    }

}
