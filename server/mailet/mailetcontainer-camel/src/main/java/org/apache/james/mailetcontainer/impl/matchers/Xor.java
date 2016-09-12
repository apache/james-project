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
import java.util.ArrayList;

import org.apache.mailet.MailAddress;
import org.apache.mailet.Mail;
import javax.mail.MessagingException;
import org.apache.mailet.Matcher;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

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
        boolean first = true;
        for (Matcher matcher: getMatchers()) {
            Collection<MailAddress> matchedAddresses = Optional.fromNullable(matcher.match(mail)).or(new ArrayList<MailAddress>());

            if (first) {
                finalResult = matchedAddresses;
                first = false;
            } else {
                finalResult = performXor(finalResult, matchedAddresses);
            }
        }
        return finalResult;
    }

    private Collection<MailAddress> performXor(Collection<MailAddress> collection1, Collection<MailAddress> collection2) {
        ImmutableList.Builder<MailAddress> result = ImmutableList.builder();
        result.addAll(calculateExternalEnsemble(collection1, collection2));
        result.addAll(calculateExternalEnsemble(collection2, collection1));
        return result.build();
    }

    private Collection<MailAddress> calculateExternalEnsemble(Collection<MailAddress> baseEnsemble, Collection<MailAddress> potentiallyCommonEnsemble) {
        ImmutableList.Builder<MailAddress> externalEnsemble = ImmutableList.builder();
        for (MailAddress recipient : baseEnsemble) {
            if (!potentiallyCommonEnsemble.contains(recipient)) {
                externalEnsemble.add(recipient);
            }
        }
        return externalEnsemble.build();
    }

}
