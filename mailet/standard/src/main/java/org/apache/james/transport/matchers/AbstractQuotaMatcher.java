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

import java.util.ArrayList;
import java.util.Collection;

import javax.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.mailet.Experimental;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <P>Abstract matcher checking whether a recipient has exceeded a maximum allowed quota.</P>
 * <P>"Quota" at this level is an abstraction whose specific interpretation
 * will be done by subclasses.</P> 
 * <P>Although extending GenericMatcher, its logic is recipient oriented.</P>
 *
 * @version CVS $Revision$ $Date$
 * @since 2.2.0
 *
 * @deprecated JAMES-2703 This class is deprecated and will be removed straight after upcoming James 3.4.0 release
 *
 * Please use IsOverQuota which relies on mailbox quota apis and avoids scanning
 */
@Experimental
@Deprecated
public abstract class AbstractQuotaMatcher extends GenericMatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractQuotaMatcher.class);

    /**
     * Standard matcher entrypoint.
     * First of all, checks the sender using {@link #isSenderChecked}.
     * Then, for each recipient checks it using {@link #isRecipientChecked} and
     * {@link #isOverQuota}.
     *
     * @throws MessagingException if either <CODE>isSenderChecked</CODE> or isRecipientChecked throw an exception
     */    
    @Override
    public final Collection<MailAddress> match(Mail mail) throws MessagingException {
        Collection<MailAddress> matching = null;
        if (isSenderChecked(mail.getMaybeSender())) {
            matching = new ArrayList<>();
            for (MailAddress recipient : mail.getRecipients()) {
                if (isRecipientChecked(recipient) && isOverQuota(recipient, mail)) {
                    matching.add(recipient);
                }
            }
        }
        return matching;
    }

    /**
     * Does the quota check.
     * Checks if {@link #getQuota} < {@link #getUsed} for a recipient.
     * Catches any throwable returning false, and so should any override do.
     *
     * @param address the recipient addresss to check
     * @param mail the mail involved in the check
     * @return true if over quota
     */
    protected boolean isOverQuota(MailAddress address, Mail mail) {
        try {
            boolean over = getQuota(address, mail) < getUsed(address, mail);
            if (over) {
                LOGGER.info("{} is over quota.", address);
            }
            return over;
        } catch (Throwable e) {
            LOGGER.error("Exception checking quota for: {}", address, e);
            return false;
        }
    }

    /** 
     * Checks the sender.
     * The default behaviour is to check that the sender <I>is not</I> null nor the local postmaster.
     * If a subclass overrides this method it should "and" <CODE>super.isSenderChecked</CODE>
     * to its check.
     *
     * @param sender the sender to check
     */
    private boolean isSenderChecked(MaybeSender sender) {
        return sender.asOptional()
            .filter(mailAddress -> !isPostmaster(mailAddress))
            .isPresent();
    }

    private boolean isPostmaster(MailAddress mailAddress) {
        return getMailetContext().getPostmaster().equals(mailAddress);
    }

    /** 
     * Checks the recipient.
     * The default behaviour is to check that the recipient <I>is not</I> the local postmaster.
     * If a subclass overrides this method it should "and" <CODE>super.isRecipientChecked</CODE>
     * to its check.
     *
     * @param recipient the recipient to check
     */    
    protected boolean isRecipientChecked(MailAddress recipient) throws MessagingException {
        return !(isPostmaster(recipient));
    }

    /** 
     * Gets the quota to check against.
     *
     * @param address the address holding the quota if applicable
     * @param mail the mail involved if needed
     */
    protected abstract long getQuota(MailAddress address, Mail mail) throws MessagingException;
    
    /**
     * Gets the used amount to check against the quota.
     *
     * @param address the address involved
     * @param mail the mail involved if needed
     */
    protected abstract long getUsed(MailAddress address, Mail mail) throws MessagingException;

    /**
     * Utility method that parses an amount string.
     * You can use 'k' and 'm' as optional postfixes to the amount (both upper and lowercase).
     * In other words, "1m" is the same as writing "1024k", which is the same as
     * "1048576".
     *
     * @param amount the amount string to parse
     */
    protected long parseQuota(String amount) throws MessagingException {
        long quota;
        try {
            if (amount.endsWith("k")) {
                amount = amount.substring(0, amount.length() - 1);
                quota = Long.parseLong(amount) * 1024;
            } else if (amount.endsWith("m")) {
                amount = amount.substring(0, amount.length() - 1);
                quota = Long.parseLong(amount) * 1024 * 1024;
            } else {
                quota = Long.parseLong(amount);
            }
            return quota;
        } catch (Exception e) {
            throw new MessagingException("Exception parsing quota", e);
        }
    }
}
