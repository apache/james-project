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

package org.apache.james.transport.mailets;

import org.apache.mailet.MailAddress;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import java.util.Collection;
import java.util.HashSet;

/**
 * <p>
 * Replaces incoming recipients with those specified, and resends the message
 * unaltered.
 * </p>
 * <p>
 * Can be totally replaced by an equivalent usage of {@link Resend} (see below),
 * simply replacing <i>&lt;forwardto&gt;</i> with <i>&lt;recipients&gt</i>.
 * 
 * <p>
 * Sample configuration:
 * </p>
 * 
 * <pre>
 * <code>
 * &lt;mailet match="All" class="Forward">
 *   &lt;forwardTo&gt;<i>comma delimited list of email addresses</i>&lt;/forwardTo&gt;
 *   &lt;passThrough&gt;<i>true or false, default=false</i>&lt;/passThrough&gt;
 *   &lt;fakeDomainCheck&gt;<i>true or false, default=true</i>&lt;/fakeDomainCheck&gt;
 *   &lt;debug&gt;<i>true or false, default=false</i>&lt;/debug&gt;
 * &lt;/mailet&gt;
 * </code>
 * </pre>
 * 
 * <p>
 * The behaviour of this mailet is equivalent to using Resend with the following
 * configuration:
 * </p>
 * 
 * <pre>
 * <code>
 * &lt;mailet match="All" class="Resend">
 *   &lt;recipients&gt;comma delimited list of email addresses&lt;/recipients&gt;
 *   &lt;passThrough&gt;true or false&lt;/passThrough&gt;
 *   &lt;fakeDomainCheck&gt;<i>true or false</i>&lt;/fakeDomainCheck&gt;
 *   &lt;debug&gt;<i>true or false</i>&lt;/debug&gt;
 * &lt;/mailet&gt;
 * </code>
 * </pre>
 * <p>
 * <i>forwardto</i> can be used instead of <i>forwardTo</i>; such name is kept
 * for backward compatibility.
 * </p>
 */
public class Forward extends AbstractRedirect {

    /**
     * Return a string describing this mailet.
     * 
     * @return a string describing this mailet
     */
    public String getMailetInfo() {
        return "Forward Mailet";
    }

    /** Gets the expected init parameters. */
    protected String[] getAllowedInitParameters() {
        return new String[]{
                // "static",
                "debug", "passThrough", "fakeDomainCheck", "forwardto", "forwardTo" };
    }

    /**
     * @return UNALTERED
     */
    protected int getInLineType() {
        return UNALTERED;
    }

    /**
     * @return NONE
     */
    protected int getAttachmentType() {
        return NONE;
    }

    /**
     * @return ""
     */
    protected String getMessage() {
        return "";
    }

    /**
     * @return the <code>recipients</code> init parameter or null if missing
     */
    protected Collection<MailAddress> getRecipients() throws MessagingException {
        Collection<MailAddress> newRecipients = new HashSet<MailAddress>();
        String addressList = getInitParameter("forwardto", getInitParameter("forwardTo"));

        // if nothing was specified, throw an exception
        if (addressList == null) {
            throw new MessagingException("Failed to initialize \"recipients\" list: no <forwardTo> or <forwardto> init parameter found");
        }

        try {
            InternetAddress[] iaarray = InternetAddress.parse(addressList, false);
            for (InternetAddress anIaarray : iaarray) {
                String addressString = anIaarray.getAddress();
                MailAddress specialAddress = getSpecialAddress(addressString, new String[]{"postmaster", "sender", "from", "replyTo", "reversePath", "unaltered", "recipients", "to", "null"});
                if (specialAddress != null) {
                    newRecipients.add(specialAddress);
                } else {
                    newRecipients.add(new MailAddress(anIaarray));
                }
            }
        } catch (Exception e) {
            throw new MessagingException("Exception thrown in getRecipients() parsing: " + addressList, e);
        }
        if (newRecipients.size() == 0) {
            throw new MessagingException("Failed to initialize \"recipients\" list; empty <recipients> init parameter found.");
        }

        return newRecipients;
    }

    /**
     * @return null
     */
    protected InternetAddress[] getTo() throws MessagingException {
        return null;
    }

    /**
     * @return null
     */
    protected MailAddress getReplyTo() throws MessagingException {
        return null;
    }

    /**
     * @return null
     */
    protected MailAddress getReversePath() throws MessagingException {
        return null;
    }

    /**
     * @return null
     */
    protected MailAddress getSender() throws MessagingException {
        return null;
    }

    /**
     * @return null
     */
    protected String getSubject() {
        return null;
    }

    /**
     * @return ""
     */
    protected String getSubjectPrefix() {
        return null;
    }

    /**
     * @return false
     */
    protected boolean attachError() {
        return false;
    }

    /**
     * @return false
     */
    protected boolean isReply() {
        return false;
    }

}
