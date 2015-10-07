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
package org.apache.james.mailetcontainer.impl.camel;

import java.util.List;
import java.util.Locale;

import javax.mail.MessagingException;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.james.mailetcontainer.impl.MailetConfigImpl;
import org.apache.james.mailetcontainer.impl.ProcessorUtil;
import org.apache.james.mailetcontainer.lib.AbstractStateMailetProcessor.MailetProcessorListener;
import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.MailetConfig;
import org.slf4j.Logger;

/**
 * Mailet wrapper which execute a Mailet in a Processor
 */
public class CamelProcessor implements Processor {

    private final Mailet mailet;
    private final Logger logger;
    private final CamelMailetProcessor processor;

    /**
     * Mailet to call on process
     * 
     * @param mailet
     */
    public CamelProcessor(Mailet mailet, Logger logger, CamelMailetProcessor processor) {
        this.mailet = mailet;
        this.logger = logger;
        this.processor = processor;
    }

    /**
     * Call the wrapped mailet for the exchange
     */
    @SuppressWarnings("unchecked")
    public void process(Exchange exchange) throws Exception {
        Mail mail = exchange.getIn().getBody(Mail.class);
        long start = System.currentTimeMillis();
        MessagingException ex = null;
        try {
            mailet.service(mail);
        } catch (MessagingException me) {
            ex = me;
            String onMailetException = null;

            MailetConfig mailetConfig = mailet.getMailetConfig();
            if (mailetConfig instanceof MailetConfigImpl) {
                onMailetException = ((MailetConfigImpl) mailetConfig).getInitAttribute("onMailetException");
            }
            if (onMailetException == null) {
                onMailetException = Mail.ERROR;
            } else {
                onMailetException = onMailetException.trim().toLowerCase(Locale.US);
            }
            if (onMailetException.compareTo("ignore") == 0) {
                // ignore the exception and continue
                // this option should not be used if the mail object can be
                // changed by the mailet
                ProcessorUtil.verifyMailAddresses(mail.getRecipients());
            } else {
                ProcessorUtil.handleException(me, mail, mailet.getMailetConfig().getMailetName(), onMailetException, logger);
            }

        } finally {
            List<MailetProcessorListener> listeners = processor.getListeners();
            long complete = System.currentTimeMillis() - start;
            for (MailetProcessorListener listener : listeners) {
                listener.afterMailet(mailet, mail.getName(), mail.getState(), complete, ex);
            }
        }
    }

}
