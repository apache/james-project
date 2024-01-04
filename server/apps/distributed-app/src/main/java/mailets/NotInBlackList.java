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

package mailets;

import java.util.Collection;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

import com.github.steveash.guavate.Guavate;

public class NotInBlackList extends GenericMatcher {
    NotInBlackList() {

    }

    @Override
    public Collection<MailAddress> match(Mail mail) {
        return mail.getRecipients()
            .stream()
            .filter(recipient -> !isSenderBlackListed(mail.getMaybeSender(), recipient))
            .collect(Guavate.toImmutableList());
    }

    private Boolean isSenderBlackListed(MaybeSender maybeSender, MailAddress recipient) {
        Domain domain = recipient.getDomain();

        System.out.println("receiver: " + recipient.getLocalPart() + " " + domain.toString());
        System.out.println("sender: " + maybeSender.get().getLocalPart() + " " + maybeSender.get().getDomain().toString());

        if (giveOrg(recipient.getLocalPart()).equals(giveOrg(maybeSender.get().getLocalPart())) && domain.toString().equals(maybeSender.get().getDomain().toString())) {
            System.out.println("valid email");
            return Boolean.FALSE;
        }
        System.out.printf("invalid email");
        return Boolean.TRUE;
    }

    public String giveOrg(String localpart) {
        int startIndex = 0;
        for (int i = localpart.length() - 2; i >= 0; i--) {
           if (localpart.charAt(i) == '.') {
               startIndex = i + 1;
               break;
           }
        }
        return localpart.substring(startIndex, localpart.length() - 1);
    }
}
