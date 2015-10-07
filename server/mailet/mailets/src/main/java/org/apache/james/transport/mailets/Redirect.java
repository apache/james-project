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

import java.util.Collection;
import java.util.HashSet;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;

import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;

/**
 * <p>
 * A mailet providing configurable redirection services.
 * </p>
 * <p>
 * Can produce listserver, forward and notify behaviour, with the original
 * message intact, attached, appended or left out altogether.
 * </p>
 * <p>
 * It differs from {@link Resend} because (i) some defaults are different,
 * notably for the following parameters: <i>&lt;recipients&gt;</i>,
 * <i>&lt;to&gt;</i>, <i>&lt;reversePath&gt;</i> and <i>&lt;inline&gt;</i>; (ii)
 * because it allows the use of the <i>&lt;static&gt;</i> parameter;.<br>
 * Use <code>Resend</code> if you need full control, <code>Redirect</code> if
 * the more automatic behaviour of some parameters is appropriate.
 * </p>
 * <p>
 * This built in functionality is controlled by the configuration as laid out
 * below. In the table please note that the parameters controlling message
 * headers accept the <b>&quot;unaltered&quot;</b> value, whose meaning is to
 * keep the associated header unchanged and, unless stated differently,
 * corresponds to the assumed default if the parameter is missing.
 * </p>
 * <p>
 * The configuration parameters are:
 * </p>
 * <table width="75%" border="1" cellspacing="2" cellpadding="2">
 * <tr valign=top>
 * <td width="20%">&lt;recipients&gt;</td>
 * <td width="80%">
 * A comma delimited list of addresses for recipients of this message; it will
 * use the &quot;to&quot; list if not specified, and &quot;unaltered&quot; if
 * none of the lists is specified.<br>
 * These addresses will only appear in the To: header if no &quot;to&quot; list
 * is supplied.<br>
 * Such addresses can contain &quot;full names&quot;, like <i>Mr. John D. Smith
 * &lt;john.smith@xyz.com&gt;</i>.<br>
 * The list can include constants &quot;sender&quot;, &quot;from&quot;,
 * &quot;replyTo&quot;, &quot;postmaster&quot;, &quot;reversePath&quot;,
 * &quot;recipients&quot;, &quot;to&quot;, &quot;null&quot; and
 * &quot;unaltered&quot;; &quot;replyTo&quot; uses the ReplyTo header if
 * available, otherwise the From header if available, otherwise the Sender
 * header if available, otherwise the return-path; &quot;from&quot; is made
 * equivalent to &quot;sender&quot;, and &quot;to&quot; is made equivalent to
 * &quot;recipients&quot;; &quot;null&quot; is ignored.</td>
 * </tr>
 * <tr valign=top>
 * <td width="20%">&lt;to&gt;</td>
 * <td width="80%">
 * A comma delimited list of addresses to appear in the To: header; the email
 * will be delivered to any of these addresses if it is also in the recipients
 * list.<br>
 * The recipients list will be used if this list is not supplied; if none of the
 * lists is specified it will be &quot;unaltered&quot;.<br>
 * Such addresses can contain &quot;full names&quot;, like <i>Mr. John D. Smith
 * &lt;john.smith@xyz.com&gt;</i>.<br>
 * The list can include constants &quot;sender&quot;, &quot;from&quot;,
 * &quot;replyTo&quot;, &quot;postmaster&quot;, &quot;reversePath&quot;,
 * &quot;recipients&quot;, &quot;to&quot;, &quot;null&quot; and
 * &quot;unaltered&quot;; &quot;from&quot; uses the From header if available,
 * otherwise the Sender header if available, otherwise the return-path;
 * &quot;replyTo&quot; uses the ReplyTo header if available, otherwise the From
 * header if available, otherwise the Sender header if available, otherwise the
 * return-path; &quot;recipients&quot; is made equivalent to &quot;to&quot;; if
 * &quot;null&quot; is specified alone it will remove this header.</td>
 * </tr>
 * <tr valign=top>
 * <td width="20%">&lt;sender&gt;</td>
 * <td width="80%">
 * A single email address to appear in the From: and Return-Path: headers and
 * become the sender.<br>
 * It can include constants &quot;sender&quot;, &quot;postmaster&quot; and
 * &quot;unaltered&quot;; &quot;sender&quot; is equivalent to
 * &quot;unaltered&quot;.<br>
 * Default: &quot;unaltered&quot;.</td>
 * </tr>
 * <tr valign=top>
 * <td width="20%">&lt;message&gt;</td>
 * <td width="80%">
 * A text message to insert into the body of the email.<br>
 * Default: no message is inserted.</td>
 * </tr>
 * <tr valign=top>
 * <td width="20%">&lt;inline&gt;</td>
 * <td width="80%">
 * <p>
 * One of the following items:
 * </p>
 * <ul>
 * <li>unaltered &nbsp;&nbsp;&nbsp;&nbsp;The original message is the new
 * message, for forwarding/aliasing</li>
 * <li>heads&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;The
 * headers of the original message are appended to the message</li>
 * <li>body&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;The
 * body of the original is appended to the new message</li>
 * <li>
 * all&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp
 * ;&nbsp;&nbsp;&nbsp;Both headers and body are appended</li>
 * <li>none&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
 * Neither body nor headers are appended</li>
 * </ul>
 * Default: &quot;body&quot;.</td>
 * </tr>
 * <tr valign=top>
 * <td width="20%">&lt;attachment&gt;</td>
 * <td width="80%">
 * <p>
 * One of the following items:
 * </p>
 * <ul>
 * <li>heads&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;The headers of the original are
 * attached as text</li>
 * <li>body&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;The body of the original is
 * attached as text</li>
 * <li>all&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Both
 * headers and body are attached as a single text file</li>
 * <li>none&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Nothing is attached</li>
 * <li>message &nbsp;The original message is attached as type message/rfc822,
 * this means that it can, in many cases, be opened, resent, fw'd, replied to
 * etc by email client software.</li>
 * </ul>
 * Default: &quot;none&quot;.</td>
 * </tr>
 * <tr valign=top>
 * <td width="20%">&lt;passThrough&gt;</td>
 * <td width="80%">
 * true or false, if true the original message continues in the mailet processor
 * after this mailet is finished. False causes the original to be stopped.<br>
 * Default: false.</td>
 * </tr>
 * <tr valign=top>
 * <td width="20%">&lt;fakeDomainCheck&gt;</td>
 * <td width="80%">
 * true or false, if true will check if the sender domain is valid.<br>
 * Default: true.</td>
 * </tr>
 * <tr valign=top>
 * <td width="20%">&lt;attachError&gt;</td>
 * <td width="80%">
 * true or false, if true any error message available to the mailet is appended
 * to the message body (except in the case of inline == unaltered).<br>
 * Default: false.</td>
 * </tr>
 * <tr valign=top>
 * <td width="20%">&lt;replyTo&gt;</td>
 * <td width="80%">
 * A single email address to appear in the Reply-To: header.<br>
 * It can include constants &quot;sender&quot;, &quot;postmaster&quot;
 * &quot;null&quot; and &quot;unaltered&quot;; if &quot;null&quot; is specified
 * it will remove this header.<br>
 * Default: &quot;unaltered&quot;.</td>
 * </tr>
 * </tr>
 * <tr valign=top>
 * <td width="20%">&lt;reversePath&gt;</td>
 * <td width="80%">
 * A single email address to appear in the Return-Path: header.<br>
 * It can include constants &quot;sender&quot;, &quot;postmaster&quot; and
 * &quot;null&quot;; if &quot;null&quot; is specified then it will set it to <>,
 * meaning &quot;null return path&quot;.<br>
 * Notice: the &quot;unaltered&quot; value is <i>not allowed</i>.<br>
 * Default: the value of the <i>&lt;sender&gt;</i> parameter, if set, otherwise
 * remains unaltered.</td>
 * </tr>
 * <tr valign=top>
 * <td width="20%">&lt;subject&gt;</td>
 * <td width="80%">
 * An optional string to use as the subject.<br>
 * Default: keep the original message subject.</td>
 * </tr>
 * <tr valign=top>
 * <td width="20%">&lt;prefix&gt;</td>
 * <td width="80%">
 * An optional subject prefix prepended to the original message subject, or to a
 * new subject specified with the <i>&lt;subject&gt;</i> parameter.<br>
 * For example: <i>[Undeliverable mail]</i>.<br>
 * Default: &quot;&quot;.</td>
 * </tr>
 * <tr valign=top>
 * <td width="20%">&lt;isReply&gt;</td>
 * <td width="80%">
 * true or false, if true the IN_REPLY_TO header will be set to the id of the
 * current message.<br>
 * Default: false.</td>
 * </tr>
 * <tr valign=top>
 * <td width="20%">&lt;debug&gt;</td>
 * <td width="80%">
 * true or false. If this is true it tells the mailet to write some debugging
 * information to the mailet log.<br>
 * Default: false.</td>
 * </tr>
 * <tr valign=top>
 * <td width="20%">&lt;static&gt;</td>
 * <td width="80%">
 * true or false. If this is true it tells the mailet that it can reuse all the
 * initial parameters (to, from, etc) without re-calculating their values. This
 * will boost performance where a redirect task doesn't contain any dynamic
 * values. If this is false, it tells the mailet to recalculate the values for
 * each e-mail processed.<br>
 * Default: false.</td>
 * </tr>
 * </table>
 * 
 * <p>
 * Example:
 * </p>
 * 
 * <pre>
 * <code>
 *  &lt;mailet match=&quot;RecipientIs=test@localhost&quot; class=&quot;Redirect&quot;&gt;
 *    &lt;recipients&gt;x@localhost, y@localhost, z@localhost&lt;/recipients&gt;
 *    &lt;to&gt;list@localhost&lt;/to&gt;
 *    &lt;sender&gt;owner@localhost&lt;/sender&gt;
 *    &lt;message&gt;sent on from James&lt;/message&gt;
 *    &lt;inline&gt;unaltered&lt;/inline&gt;
 *    &lt;passThrough&gt;FALSE&lt;/passThrough&gt;
 *    &lt;replyTo&gt;postmaster&lt;/replyTo&gt;
 *    &lt;prefix xml:space="preserve"&gt;[test mailing] &lt;/prefix&gt;
 *    &lt;!-- note the xml:space="preserve" to preserve whitespace --&gt;
 *    &lt;static&gt;TRUE&lt;/static&gt;
 * &lt;/mailet&gt;
 * </code>
 * </pre>
 * 
 * <p>
 * and:
 * </p>
 * 
 * <pre>
 * <code>
 *  &lt;mailet match=&quot;All&quot; class=&quot;Redirect&quot;&gt;
 *    &lt;recipients&gt;x@localhost&lt;/recipients&gt;
 *    &lt;sender&gt;postmaster&lt;/sender&gt;
 *    &lt;message xml:space="preserve"&gt;Message marked as spam:&lt;/message&gt;
 *    &lt;inline&gt;heads&lt;/inline&gt;
 *    &lt;attachment&gt;message&lt;/attachment&gt;
 *    &lt;passThrough&gt;FALSE&lt;/passThrough&gt;
 *    &lt;attachError&gt;TRUE&lt;/attachError&gt;
 *    &lt;replyTo&gt;postmaster&lt;/replyTo&gt;
 *    &lt;prefix&gt;[spam notification]&lt;/prefix&gt;
 *    &lt;static&gt;TRUE&lt;/static&gt;
 *  &lt;/mailet&gt;
 * </code>
 * </pre>
 * <p>
 * <i>replyto</i> can be used instead of <i>replyTo</i>; such name is kept for
 * backward compatibility.
 * </p>
 */

public class Redirect extends AbstractRedirect {

    /**
     * Returns a string describing this mailet.
     * 
     * @return a string describing this mailet
     */
    public String getMailetInfo() {
        return "Redirect Mailet";
    }

    /** Gets the expected init parameters. */
    protected String[] getAllowedInitParameters() {
        String[] allowedArray = { "static", "debug", "passThrough", "fakeDomainCheck", "inline", "attachment", "message", "recipients", "to", "replyTo", "replyto", "reversePath", "sender", "subject", "prefix", "attachError", "isReply" };
        return allowedArray;
    }

    /**
     * @return the <code>static</code> init parameter
     */
    protected boolean isStatic() {
        return isStatic;
    }

    /**
     * @return the <code>inline</code> init parameter
     */
    protected int getInLineType() {
        return getTypeCode(getInitParameter("inline", "body"));
    }

    /**
     * @return the <code>recipients</code> init parameter or the postmaster
     *         address or <code>SpecialAddress.SENDER</code> or
     *         <code>SpecialAddress.REVERSE_PATH</code> or
     *         <code>SpecialAddress.UNALTERED</code> or the <code>to</code> init
     *         parameter if missing or <code>null</code> if also the latter is
     *         missing
     */
    protected Collection<MailAddress> getRecipients() throws MessagingException {
        Collection<MailAddress> newRecipients = new HashSet<MailAddress>();
        String addressList = getInitParameter("recipients", getInitParameter("to"));

        // if nothing was specified, return <code>null</code> meaning no change
        if (addressList == null) {
            return null;
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
     * @return the <code>to</code> init parameter or the postmaster address or
     *         <code>SpecialAddress.SENDER</code> or
     *         <code>SpecialAddress.REVERSE_PATH</code> or
     *         <code>SpecialAddress.UNALTERED</code> or the
     *         <code>recipients</code> init parameter if missing or
     *         <code>null</code> if also the latter is missing
     */
    protected InternetAddress[] getTo() throws MessagingException {
        InternetAddress[] iaarray;
        String addressList = getInitParameter("to", getInitParameter("recipients"));

        // if nothing was specified, return null meaning no change
        if (addressList == null) {
            return null;
        }

        try {
            iaarray = InternetAddress.parse(addressList, false);
            for (int i = 0; i < iaarray.length; ++i) {
                String addressString = iaarray[i].getAddress();
                MailAddress specialAddress = getSpecialAddress(addressString, new String[] { "postmaster", "sender", "from", "replyTo", "reversePath", "unaltered", "recipients", "to", "null" });
                if (specialAddress != null) {
                    iaarray[i] = specialAddress.toInternetAddress();
                }
            }
        } catch (Exception e) {
            throw new MessagingException("Exception thrown in getTo() parsing: " + addressList, e);
        }
        if (iaarray.length == 0) {
            throw new MessagingException("Failed to initialize \"to\" list; empty <to> init parameter found.");
        }

        return iaarray;
    }

    /**
     * @return the <code>reversePath</code> init parameter or the postmaster
     *         address or <code>SpecialAddress.SENDER</code> or
     *         <code>SpecialAddress.NULL</code> or <code>null</code> if missing
     */
    protected MailAddress getReversePath() throws MessagingException {
        String addressString = getInitParameter("reversePath");
        if (addressString != null) {
            MailAddress specialAddress = getSpecialAddress(addressString, new String[] { "postmaster", "sender", "null" });
            if (specialAddress != null) {
                return specialAddress;
            }

            try {
                return new MailAddress(addressString);
            } catch (Exception e) {
                throw new MessagingException("Exception thrown in getReversePath() parsing: " + addressString, e);
            }
        }

        return null;
    }

    /**
     * @return {@link AbstractRedirect#getReversePath()}; if null return
     *         {@link AbstractRedirect#getSender(Mail)}, meaning the new
     *         requested sender if any
     */
    protected MailAddress getReversePath(Mail originalMail) throws MessagingException {
        MailAddress reversePath = super.getReversePath(originalMail);
        if (reversePath == null) {
            reversePath = getSender(originalMail);
        }
        return reversePath;
    }

}
