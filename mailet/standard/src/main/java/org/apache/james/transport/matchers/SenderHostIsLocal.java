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


package org.apache.james.transport.matchers;

import java.util.Collection;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

/**
 * Checks the sender's displayed domain name against a the hosts serviced by
 * this mail context. <br>
 * <br>
 * Sample Configuration: <br>
 * <br>
 * &lt;mailet match="SenderHostIsLocal" class="SpamAssassin"&gt; &lt;/mailet&gt;
 * <br>
 * <br>
 */
public class SenderHostIsLocal extends GenericMatcher {
    @Override
    public Collection<MailAddress> match(Mail mail) {
        if (mail.getMaybeSender().asOptional()
                .map(this::isLocalServer)
                .orElse(false)) {
            return mail.getRecipients();
        }
        return null;
        
    }

    private boolean isLocalServer(MailAddress sender) {
        return this.getMailetContext().isLocalServer(sender.getDomain());
    }

}
