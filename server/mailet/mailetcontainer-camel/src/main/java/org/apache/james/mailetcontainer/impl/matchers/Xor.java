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

import java.util.Collection;
import java.util.Iterator;
import java.util.ArrayList;

import org.apache.mailet.MailAddress;
import org.apache.mailet.Mail;
import javax.mail.MessagingException;
import org.apache.mailet.Matcher;

public class Xor extends GenericCompositeMatcher {

    /**
     * This is the Xor CompositeMatcher - consider it to be the inequality
     * operator for recipients. If any recipients match other matcher results
     * then the result does not include that recipient.
     * 
     * @return Collection of Recipients from the Xor composition of the child
     *         matchers.
     */
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        Collection<MailAddress> finalResult = null;
        Matcher matcher;
        boolean first = true;
        for (Iterator<Matcher> matcherIter = iterator(); matcherIter.hasNext();) {
            matcher = matcherIter.next();
            Collection<MailAddress> result = matcher.match(mail);
            if (result == null) {
                result = new ArrayList<MailAddress>(0);
            }
            // log("Matching with " +
            // matcher.getMatcherConfig().getMatcherName() +
            // " result="+result.toString() );

            if (first) {
                finalResult = result;
                first = false;
            } else {
                // Check if we need to Xor ...
                // if the finalResult and the subsequent result are the same
                // collection, then it contains the same recipients
                // so we can short-circuit building the XOR and return an empty
                // set
                if (finalResult == result) {
                    // the XOR of the same collection is empty
                    finalResult.clear();
                    // log("same collection - so clear");
                } else {
                    // the two results are different collections, so we XOR them
                    // Ensure that the finalResult does not contain recipients
                    // in the result collection
                    MailAddress recipient;
                    for (Object aResult : result) {
                        recipient = (MailAddress) (aResult);
                        if (!finalResult.contains(recipient)) {
                            finalResult.add(recipient);
                        } else {
                            finalResult.remove(recipient);
                        }
                    }
                    recipient = null;
                    // log("xor recipients into new finalResult="+finalResult);
                }
                // basically the finalResult gets replaced with a smaller result
                // otherwise finalResult would have been equal to result (in all
                // cases)
            }
            result = null;
        }
        return finalResult;
    }

}
