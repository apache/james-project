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

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.library.netmatcher.NetMatcher;
import org.apache.james.jspf.core.DNSServiceEnabled;
import org.apache.james.jspf.core.MacroExpand;
import org.apache.james.jspf.core.MacroExpandEnabled;
import org.apache.james.jspf.core.SPFCheckEnabled;
import org.apache.james.jspf.executor.SPFResult;
import org.apache.james.jspf.executor.SynchronousSPFExecutor;
import org.apache.james.jspf.impl.DNSServiceXBillImpl;
import org.apache.james.jspf.impl.DefaultTermsFactory;
import org.apache.james.jspf.parser.RFC4408SPF1Parser;
import org.apache.james.jspf.wiring.WiringServiceTable;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Experimental;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;

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
 *   &lt;ignoredNetworks&gt;127.0.0.0/8&lt;/ignoredNetworks&gt;
 * &lt;/mailet&gt;
 * </pre>
 */
@Experimental
public class SPF extends GenericMailet {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(SPF.class);

    private boolean debug = false;
    private boolean addHeader = false;
    private boolean ignoreLocalIps = true;
    private org.apache.james.jspf.impl.SPF spf;
    public static final AttributeName EXPLANATION_ATTRIBUTE = AttributeName.of("org.apache.james.transport.mailets.spf.explanation");
    public static final AttributeName RESULT_ATTRIBUTE = AttributeName.of("org.apache.james.transport.mailets.spf.result");
    private static final String privateNetworks = "127.0.0.0/8, 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16";

    private final DNSService dnsService;
    private NetMatcher netMatcher;

    // should be removed in future (see JAMES-2158)
    private org.apache.james.jspf.core.DNSService spfDnsService;

    @Inject
    public SPF(DNSService dnsService) {
        this.dnsService = dnsService;
    }

    public void setSPFDnsService(org.apache.james.jspf.core.DNSService spfDnsService) {
        this.spfDnsService = spfDnsService;
    }

    @Override
    public void init() {
        debug = Boolean.parseBoolean(getInitParameter("debug", "false"));
        addHeader = Boolean.parseBoolean(getInitParameter("addHeader", "false"));
        ignoreLocalIps = Boolean.parseBoolean(getInitParameter("ignoreLocalIps", "true"));

        if (spfDnsService == null) {
            createSPF(new DNSServiceXBillImpl());
        } else {
            createSPF(spfDnsService);
        }

        Collection<String> ignoredNetworks = Splitter.on(',')
            .trimResults()
            .omitEmptyStrings()
            .splitToList(getInitParameter("ignoredNetworks", privateNetworks));
        netMatcher = new NetMatcher(ignoredNetworks, dnsService);

        LOGGER.info("SPF addHeader={} debug={} ignoredNetworks={}", addHeader, debug, ignoredNetworks);
    }

    private void createSPF(org.apache.james.jspf.core.DNSService dnsProbe) {
        // TODO use once fixed AsynchronousSPFExecutor (see JAMES-3920)
        WiringServiceTable wiringService = new WiringServiceTable();
        wiringService.put(DNSServiceEnabled.class, dnsProbe);
        MacroExpand macroExpand = new MacroExpand(dnsProbe);
        wiringService.put(MacroExpandEnabled.class, macroExpand);
        RFC4408SPF1Parser parser = new RFC4408SPF1Parser(new DefaultTermsFactory(wiringService));
        SynchronousSPFExecutor executor = new SynchronousSPFExecutor(dnsProbe);
        spf = new org.apache.james.jspf.impl.SPF(dnsProbe, parser, macroExpand, executor);
        wiringService.put(SPFCheckEnabled.class, spf);
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        String remoteAddr = mail.getRemoteAddr();

        if (ignoreLocalIps && netMatcher.matchInetNetwork(remoteAddr)) {
            LOGGER.debug("ignore SPF check for ip:{}", remoteAddr);
        } else {
            String helo = AttributeUtils.getValueAndCastFromMail(mail, Mail.SMTP_HELO, String.class).orElse(mail.getRemoteHost());
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
}
