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

import javax.inject.Inject;

import org.apache.james.core.MailAddress;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.mailet.LoopPrevention;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

/**
 * This matcher allow you to know if the sender of an email is part of a RRT loop.
 *
 * This is useful when bouncing upon RRT execution issues: we don't want to create a bouncing loop
 * (as the execution of that RRT loop will fail).
 *
 * Example:
 *
 * <pre><code>
 * &lt;mailet match=&quot;IsSenderInRRTLoop&quot; class=&quot;&lt;any-class&gt;&quot;/&gt;
 * </code></pre>
 *
 */
public class IsSenderInRRTLoop extends GenericMatcher {

    private final RecipientRewriteTable recipientRewriteTable;

    @Inject
    public IsSenderInRRTLoop(RecipientRewriteTable recipientRewriteTable) {
        this.recipientRewriteTable = recipientRewriteTable;
    }

    @Override
    public Collection<MailAddress> match(Mail mail) {
        try {
            if (mail.hasSender()) {
                recipientRewriteTable.getResolvedMappings(mail.getMaybeSender().get().getLocalPart(), mail.getMaybeSender().get().getDomain());
            }
        } catch (RecipientRewriteTable.TooManyMappingException e) {
            return mail.getRecipients();
        } catch (Exception e) {
            LoggerFactory.getLogger(IsSenderInRRTLoop.class).warn("Error while executing RRT");
        }
        return mail.getMaybeSender()
            .asOptional()
            .filter(sender -> recordedRecipientsContainSender(mail, sender))
            .map(any -> mail.getRecipients())
            .orElse(ImmutableList.of());
    }

    private static boolean recordedRecipientsContainSender(Mail mail, MailAddress sender) {
        return LoopPrevention.RecordedRecipients.fromMail(mail).getRecipients().contains(sender);
    }
}
