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

import org.apache.mailet.base.RFC2822Headers;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Iterator;

/**
 * <p>
 * Abstract mailet providing configurable notification services.<br>
 * This mailet can be subclassed to make authoring notification mailets simple.
 * <br>
 * <p>
 * Provides the following functionalities to all notification subclasses:
 * </p>
 * <ul>
 * <li>A common notification message layout.</li>
 * <li>A sender of the notification message can optionally be specified. If one
 * is not specified, the postmaster's address will be used.</li>
 * <li>A notice text can be specified, and in such case will be inserted into
 * the notification inline text.</li>
 * <li>If the notified message has an "error message" set, it will be inserted
 * into the notification inline text. If the <code>attachStackTrace</code> init
 * parameter is set to true, such error message will be attached to the
 * notification message.</li>
 * <li>The notified messages are attached in their entirety (headers and
 * content) and the resulting MIME part type is "message/rfc822".</li>
 * <li>Supports by default the <code>passThrough</code> init parameter (true if
 * missing).</li>
 * </ul>
 * 
 * <p>
 * Sample configuration common to all notification mailet subclasses:
 * </p>
 * 
 * <pre>
 * <code>
 * &lt;mailet match="All" class="<i>a notification mailet</i>">
 *   &lt;sender&gt;<i>an address or postmaster or sender or unaltered, default=postmaster</i>&lt;/sender&gt;
 *   &lt;attachError&gt;<i>true or false, default=false</i>&lt;/attachError&gt;
 *   &lt;message&gt;<i>notice attached to the original message text (optional)</i>&lt;/message&gt;
 *   &lt;prefix&gt;<i>optional subject prefix prepended to the original message</i>&lt;/prefix&gt;
 *   &lt;inline&gt;<i>see {@link Redirect}, default=none</i>&lt;/inline&gt;
 *   &lt;attachment&gt;<i>see {@link Redirect}, default=message</i>&lt;/attachment&gt;
 *   &lt;passThrough&gt;<i>true or false, default=true</i>&lt;/passThrough&gt;
 *   &lt;fakeDomainCheck&gt;<i>true or false, default=true</i>&lt;/fakeDomainCheck&gt;
 *   &lt;debug&gt;<i>true or false, default=false</i>&lt;/debug&gt;
 * &lt;/mailet&gt;
 * </code>
 * </pre>
 * <p>
 * <i>notice</i> and <i>senderAddress</i> can be used instead of <i>message</i>
 * and <i>sender</i>; such names are kept for backward compatibility.
 * </p>
 * 
 * @since 2.2.0
 */
public abstract class AbstractNotify extends AbstractRedirect {

    /**
     * @return the <code>passThrough</code> init parameter, or true if missing
     */
    protected boolean getPassThrough() {
        return Boolean.valueOf(getInitParameter("passThrough", "true"));
    }

    /**
     * @return the <code>inline</code> init parameter, or <code>NONE</code> if
     *         missing
     */
    protected int getInLineType() {
        return getTypeCode(getInitParameter("inline", "none"));
    }

    /**
     * @return the <code>attachment</code> init parameter, or
     *         <code>MESSAGE</code> if missing
     */
    protected int getAttachmentType() {
        return getTypeCode(getInitParameter("attachment", "message"));
    }

    /**
     * @return the <code>notice</code> init parameter, or the
     *         <code>message</code> init parameter if missing, or a default
     *         string if both are missing
     */
    protected String getMessage() {
        return getInitParameter("notice", getInitParameter("message", "We were unable to deliver the attached message because of an error in the mail server."));
    }

    /**
     * @return the full message to append, built from the Mail object
     */
    protected String getMessage(Mail originalMail) throws MessagingException {
        MimeMessage message = originalMail.getMessage();
        StringWriter sout = new StringWriter();
        PrintWriter out = new PrintWriter(sout, true);

        // First add the "local" notice
        // (either from conf or generic error message)
        out.println(getMessage());
        // And then the message from other mailets
        if (originalMail.getErrorMessage() != null) {
            out.println();
            out.println("Error message below:");
            out.println(originalMail.getErrorMessage());
        }
        out.println();
        out.println("Message details:");

        if (message.getSubject() != null) {
            out.println("  Subject: " + message.getSubject());
        }
        if (message.getSentDate() != null) {
            out.println("  Sent date: " + message.getSentDate());
        }
        out.println("  MAIL FROM: " + originalMail.getSender());
        Iterator<MailAddress> rcptTo = originalMail.getRecipients().iterator();
        out.println("  RCPT TO: " + rcptTo.next());
        while (rcptTo.hasNext()) {
            out.println("           " + rcptTo.next());
        }
        String[] addresses;
        addresses = message.getHeader(RFC2822Headers.FROM);
        if (addresses != null) {
            out.print("  From: ");
            for (String address : addresses) {
                out.print(address + " ");
            }
            out.println();
        }
        addresses = message.getHeader(RFC2822Headers.TO);
        if (addresses != null) {
            out.print("  To: ");
            for (String address : addresses) {
                out.print(address + " ");
            }
            out.println();
        }
        addresses = message.getHeader(RFC2822Headers.CC);
        if (addresses != null) {
            out.print("  CC: ");
            for (String address : addresses) {
                out.print(address + " ");
            }
            out.println();
        }
        out.println("  Size (in bytes): " + message.getSize());
        if (message.getLineCount() >= 0) {
            out.println("  Number of lines: " + message.getLineCount());
        }

        return sout.toString();
    }

    // All subclasses of AbstractNotify are expected to establish their own
    // recipients
    abstract protected Collection<MailAddress> getRecipients() throws MessagingException;

    /**
     * @return null
     */
    protected InternetAddress[] getTo() throws MessagingException {
        return null;
    }

    /**
     * @return <code>SpecialAddress.NULL</code>, that will remove the "ReplyTo:"
     *         header
     */
    protected MailAddress getReplyTo() throws MessagingException {
        return SpecialAddress.NULL;
    }

    /**
     * @return {@link AbstractRedirect#getSender(Mail)}, meaning the new
     *         requested sender if any
     */
    protected MailAddress getReversePath(Mail originalMail) throws MessagingException {
        return getSender(originalMail);
    }

    /**
     * @return the <code>sendingAddress</code> init parameter or the
     *         <code>sender</code> init parameter or the postmaster address if
     *         both are missing; possible special addresses returned are
     *         <code>SpecialAddress.SENDER</code> and
     *         <code>SpecialAddress.UNALTERED</code>
     */
    protected MailAddress getSender() throws MessagingException {
        String addressString = getInitParameter("sendingAddress", getInitParameter("sender"));

        if (addressString == null) {
            return getMailetContext().getPostmaster();
        }

        MailAddress specialAddress = getSpecialAddress(addressString, new String[] { "postmaster", "sender", "unaltered" });
        if (specialAddress != null) {
            return specialAddress;
        }

        try {
            return new MailAddress(addressString);
        } catch (Exception e) {
            throw new MessagingException("Exception thrown in getSender() parsing: " + addressString, e);
        }
    }

    /**
     * @return null
     */
    protected String getSubject() {
        return null;
    }

    /**
     * @return the <code>prefix</code> init parameter or "Re:" if missing
     */
    protected String getSubjectPrefix() {
        return getInitParameter("prefix", "Re:");
    }

    /**
     * Builds the subject of <i>newMail</i> appending the subject of
     * <i>originalMail</i> to <i>subjectPrefix</i>, but avoiding a duplicate.
     */
    protected void setSubjectPrefix(Mail newMail, String subjectPrefix, Mail originalMail) throws MessagingException {
        String subject = originalMail.getMessage().getSubject();
        if (subject == null) {
            subject = "";
        }
        if (subjectPrefix == null || subject.indexOf(subjectPrefix) == 0) {
            newMail.getMessage().setSubject(subject);
        } else {
            newMail.getMessage().setSubject(subjectPrefix + subject);
        }
    }

    /**
     * @return true
     */
    protected boolean isReply() {
        return true;
    }
}
