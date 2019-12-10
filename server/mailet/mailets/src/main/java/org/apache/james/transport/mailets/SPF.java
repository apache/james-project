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
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Experimental;
import org.apache.mailet.Mail;
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
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(SPF.class);

    private boolean addHeader = false;
    private org.apache.james.jspf.impl.SPF spf;
    private static final AttributeName EXPLANATION_ATTRIBUTE = AttributeName.of("org.apache.james.transport.mailets.spf.explanation");
    private static final AttributeName RESULT_ATTRIBUTE = AttributeName.of("org.apache.james.transport.mailets.spf.result");

    @Override
    public void init() {
        addHeader = Boolean.parseBoolean(getInitParameter("addHeader", "false"));
        SPFLoggerAdapter logger = new SPFLoggerAdapter(Boolean.parseBoolean(getInitParameter("debug", "false")));

        spf = new DefaultSPF(logger);
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        String remoteAddr = mail.getRemoteAddr();
        String helo = mail.getRemoteHost();

        if (!remoteAddr.equals("127.0.0.1")) {
            String sender = mail.getMaybeSender().asString("");
            SPFResult result = spf.checkSPF(remoteAddr, sender, helo);
            mail.setAttribute(new Attribute(EXPLANATION_ATTRIBUTE, AttributeValue.of(result.getExplanation())));
            mail.setAttribute(new Attribute(RESULT_ATTRIBUTE, AttributeValue.of(result.getResult())));

            LOGGER.debug("ip:{} from:{} helo:{} = {}", remoteAddr, sender, helo, result.getResult());
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

    private static class SPFLoggerAdapter implements Logger {
        private boolean debug = false;
        private String name = "SPFLogger";

        public SPFLoggerAdapter(boolean debug) {
            this.debug = debug;
        }

        public SPFLoggerAdapter(String name, boolean debug) {
            this.name = name;
            this.debug = debug;
        }

        @Override
        public void debug(String arg0) {
            if (debug) {
                LOGGER.debug(arg0);
            }
        }

        @Override
        public void debug(String arg0, Throwable arg1) {
            if (debug) {
                LOGGER.debug(arg0, arg1);
            }
        }

        @Override
        public void error(String arg0) {
            LOGGER.error(arg0);
        }

        @Override
        public void error(String arg0, Throwable arg1) {
            LOGGER.error(arg0, arg1);
        }

        @Override
        public void fatalError(String arg0) {
            LOGGER.error(arg0);
        }

        @Override
        public void fatalError(String arg0, Throwable arg1) {
            LOGGER.error(arg0, arg1);
        }

        @Override
        public Logger getChildLogger(String childName) {
            return new SPFLoggerAdapter(name + "." + childName, debug);
        }

        @Override
        public void info(String arg0) {
            LOGGER.info(arg0);
        }

        @Override
        public void info(String arg0, Throwable arg1) {
            LOGGER.info(arg0, arg1);
        }

        @Override
        public boolean isDebugEnabled() {
            return LOGGER.isDebugEnabled();
        }

        @Override
        public boolean isErrorEnabled() {
            return LOGGER.isErrorEnabled();
        }

        @Override
        public boolean isFatalErrorEnabled() {
            return LOGGER.isErrorEnabled();
        }

        @Override
        public boolean isInfoEnabled() {
            return LOGGER.isInfoEnabled();
        }

        @Override
        public boolean isWarnEnabled() {
            return LOGGER.isWarnEnabled();
        }

        @Override
        public void warn(String arg0) {
            LOGGER.warn(arg0);
        }

        @Override
        public void warn(String arg0, Throwable arg1) {
            LOGGER.warn(arg0, arg1);
        }

    }

}
