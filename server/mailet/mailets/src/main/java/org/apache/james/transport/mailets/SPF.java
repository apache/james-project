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

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.jspf.core.Logger;
import org.apache.james.jspf.executor.SPFResult;
import org.apache.james.jspf.impl.DefaultSPF;
import org.apache.james.transport.mailets.managesieve.ManageSieveMailet;
import org.apache.mailet.Experimental;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.LoggerFactory;

/**
 * Check the ip, sender, helo against SPF. Add the following attributes to the
 * mail object:
 * 
 * <pre>
 * <code>
 *  org.apache.james.transport.mailets.spf.explanation
 *  org.apache.james.transport.mailets.spf.result
 * </code>
 * </pre>
 * 
 * Sample configuration:
 * 
 * <pre>
 * &lt;mailet match="All" class="SPF"&gt;
 *   &lt;addHeader&gt;true&lt;/addHeader&gt;
 *   &lt;debug&gt;false&lt;/debug&gt;
 * &lt;/mailet&gt;
 * </pre>
 */
@Experimental
public class SPF extends GenericMailet {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ManageSieveMailet.class);

    private boolean addHeader = false;
    private org.apache.james.jspf.impl.SPF spf;
    public final static String EXPLANATION_ATTRIBUTE = "org.apache.james.transport.mailets.spf.explanation";
    public final static String RESULT_ATTRIBUTE = "org.apache.james.transport.mailets.spf.result";

    /**
     * @see org.apache.mailet.base.GenericMailet#init()
     */
    public void init() {
        addHeader = Boolean.valueOf(getInitParameter("addHeader", "false"));
        SPFLoggerAdapter logger = new SPFLoggerAdapter(Boolean.valueOf(getInitParameter("debug", "false")));

        spf = new DefaultSPF(logger);
    }

    /**
     * @see org.apache.mailet.base.GenericMailet#service(org.apache.mailet.Mail)
     */
    public void service(Mail mail) throws MessagingException {
        String sender;
        MailAddress senderAddr = mail.getSender();
        String remoteAddr = mail.getRemoteAddr();
        String helo = mail.getRemoteHost();

        if (!remoteAddr.equals("127.0.0.1")) {
            if (senderAddr != null) {
                sender = senderAddr.toString();
            } else {
                sender = "";
            }
            SPFResult result = spf.checkSPF(remoteAddr, sender, helo);
            mail.setAttribute(EXPLANATION_ATTRIBUTE, result.getExplanation());
            mail.setAttribute(RESULT_ATTRIBUTE, result.getResult());

            LOGGER.debug("ip:" + remoteAddr + " from:" + sender + " helo:" + helo + " = " + result.getResult());
            if (addHeader) {
                try {
                    MimeMessage msg = mail.getMessage();
                    msg.addHeader(result.getHeaderName(), result.getHeaderText());
                    msg.saveChanges();
                } catch (MessagingException e) {
                    // Ignore not be able to add headers
                }
            }
        }
    }

    private class SPFLoggerAdapter implements Logger {
        private boolean debug = false;
        private String name = "SPFLogger";

        public SPFLoggerAdapter(boolean debug) {
            this.debug = debug;
        }

        public SPFLoggerAdapter(String name, boolean debug) {
            this.name = name;
            this.debug = debug;
        }

        public void debug(String arg0) {
            if (debug) {
                LOGGER.debug(arg0);
            }
        }

        public void debug(String arg0, Throwable arg1) {
            if (debug) {
                LOGGER.debug(arg0, arg1);
            }
        }

        public void error(String arg0) {
            LOGGER.error(arg0);
        }

        public void error(String arg0, Throwable arg1) {
            LOGGER.error(arg0, arg1);
        }

        public void fatalError(String arg0) {
            LOGGER.error(arg0);
        }

        public void fatalError(String arg0, Throwable arg1) {
            LOGGER.error(arg0, arg1);
        }

        public Logger getChildLogger(String childName) {
            return new SPFLoggerAdapter(name + "." + childName, debug);
        }

        public void info(String arg0) {
            LOGGER.info(arg0);
        }

        public void info(String arg0, Throwable arg1) {
            LOGGER.info(arg0, arg1);
        }

        public boolean isDebugEnabled() {
            return LOGGER.isDebugEnabled();
        }

        public boolean isErrorEnabled() {
            return LOGGER.isErrorEnabled();
        }

        public boolean isFatalErrorEnabled() {
            return LOGGER.isErrorEnabled();
        }

        public boolean isInfoEnabled() {
            return LOGGER.isInfoEnabled();
        }

        public boolean isWarnEnabled() {
            return LOGGER.isWarnEnabled();
        }

        public void warn(String arg0) {
            LOGGER.warn(arg0);
        }

        public void warn(String arg0, Throwable arg1) {
            LOGGER.warn(arg0, arg1);
        }

    }

}
