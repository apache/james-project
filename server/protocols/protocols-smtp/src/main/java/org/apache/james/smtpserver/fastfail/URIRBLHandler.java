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
package org.apache.james.smtpserver.fastfail;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

import javax.inject.Inject;
import javax.inject.Named;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimePart;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.lib.lifecycle.InitializingLifecycleAwareProtocolHandler;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.smtpserver.JamesMessageHook;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extract domains from message and check against URIRBLServer. For more
 * informations see <a href="http://www.surbl.org">www.surbl.org</a>
 */
public class URIRBLHandler implements JamesMessageHook, InitializingLifecycleAwareProtocolHandler {

    /** This log is the fall back shared by all instances */
    private static final Logger FALLBACK_LOG = LoggerFactory.getLogger(URIRBLHandler.class);

    /**
     * Non context specific log should only be used when no context specific log
     * is available
     */
    private final Logger serviceLog = FALLBACK_LOG;

    private final static String LISTED_DOMAIN = "LISTED_DOMAIN";

    private final static String URBLSERVER = "URBL_SERVER";

    private DNSService dnsService;

    private Collection<String> uriRbl;

    private boolean getDetail = false;

    /**
     * Gets the DNS service.
     * 
     * @return the dnsService
     */
    public final DNSService getDNSService() {
        return dnsService;
    }

    /**
     * Sets the DNS service.
     * 
     * @param dnsService
     *            the dnsService to set
     */
    @Inject
    public final void setDNSService(@Named("dnsservice") DNSService dnsService) {
        this.dnsService = dnsService;
    }

    /**
     * Set the UriRBL Servers
     * 
     * @param uriRbl
     *            The Collection holding the servers
     */
    public void setUriRblServer(Collection<String> uriRbl) {
        this.uriRbl = uriRbl;
    }

    /**
     * Set for try to get a TXT record for the blocked record.
     * 
     * @param getDetail
     *            Set to ture for enable
     */
    public void setGetDetail(boolean getDetail) {
        this.getDetail = getDetail;
    }

    /**
     * @see org.apache.james.smtpserver.JamesMessageHook#onMessage(org.apache.james.protocols.smtp.SMTPSession,
     *      org.apache.mailet.Mail)
     */
    public HookResult onMessage(SMTPSession session, Mail mail) {
        if (check(session, mail)) {
            String uRblServer = (String) session.getAttachment(URBLSERVER, State.Transaction);
            String target = (String) session.getAttachment(LISTED_DOMAIN, State.Transaction);
            String detail = null;

            // we should try to retrieve details
            if (getDetail) {
                Collection<String> txt = dnsService.findTXTRecords(target + "." + uRblServer);

                // Check if we found a txt record
                if (!txt.isEmpty()) {
                    // Set the detail
                    detail = txt.iterator().next();

                }
            }

            if (detail != null) {
                return new HookResult(HookReturnCode.DENY, DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.SECURITY_OTHER) + "Rejected: message contains domain " + target + " listed by " + uRblServer + " . Details: " + detail);
            } else {
                return new HookResult(HookReturnCode.DENY, DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.SECURITY_OTHER) + " Rejected: message contains domain " + target + " listed by " + uRblServer);
            }

        } else {
            return new HookResult(HookReturnCode.DECLINED);
        }
    }

    /**
     * Recursively scans all MimeParts of an email for domain strings. Domain
     * strings that are found are added to the supplied HashSet.
     * 
     * @param part
     *            MimePart to scan
     * @param session
     *            not null
     * @return domains The HashSet that contains the domains which were
     *         extracted
     */
    private HashSet<String> scanMailForDomains(MimePart part, SMTPSession session) throws MessagingException, IOException {
        HashSet<String> domains = new HashSet<String>();
        session.getLogger().debug("mime type is: \"" + part.getContentType() + "\"");

        if (part.isMimeType("text/plain") || part.isMimeType("text/html")) {
            session.getLogger().debug("scanning: \"" + part.getContent().toString() + "\"");
            HashSet<String> newDom = URIScanner.scanContentForDomains(domains, part.getContent().toString());

            // Check if new domains are found and add the domains
            if (newDom != null && newDom.size() > 0) {
                domains.addAll(newDom);
            }
        } else if (part.isMimeType("multipart/*")) {
            MimeMultipart multipart = (MimeMultipart) part.getContent();
            int count = multipart.getCount();
            session.getLogger().debug("multipart count is: " + count);

            for (int index = 0; index < count; index++) {
                session.getLogger().debug("recursing index: " + index);
                MimeBodyPart mimeBodyPart = (MimeBodyPart) multipart.getBodyPart(index);
                HashSet<String> newDomains = scanMailForDomains(mimeBodyPart, session);

                // Check if new domains are found and add the domains
                if (newDomains != null && newDomains.size() > 0) {
                    domains.addAll(newDomains);
                }
            }
        }
        return domains;
    }

    /**
     * Check method
     */
    protected boolean check(SMTPSession session, Mail mail) {
        MimeMessage message;

        try {
            message = mail.getMessage();

            HashSet<String> domains = scanMailForDomains(message, session);

            for (String domain : domains) {
                Iterator<String> uRbl = uriRbl.iterator();
                String target = domain;

                while (uRbl.hasNext()) {
                    try {
                        String uRblServer = uRbl.next();
                        String address = target + "." + uRblServer;

                        if (session.getLogger().isDebugEnabled()) {
                            session.getLogger().debug("Lookup " + address);
                        }

                        dnsService.getByName(address);

                        // store server name for later use
                        session.setAttachment(URBLSERVER, uRblServer, State.Transaction);
                        session.setAttachment(LISTED_DOMAIN, target, State.Transaction);

                        return true;

                    } catch (UnknownHostException uhe) {
                        // domain not found. keep processing
                    }
                }
            }
        } catch (MessagingException e) {
            session.getLogger().error(e.getMessage());
        } catch (IOException e) {
            session.getLogger().error(e.getMessage());
        }
        return false;
    }

    @Override
    public void init(Configuration config) throws ConfigurationException {
        String[] servers = config.getStringArray("uriRblServers.server");
        Collection<String> serverCollection = new ArrayList<String>();
        for (String rblServerName : servers) {
            serverCollection.add(rblServerName);
            if (serviceLog.isInfoEnabled()) {
                serviceLog.info("Adding uriRBL server: " + rblServerName);
            }
        }
        if (serverCollection != null && serverCollection.size() > 0) {
            setUriRblServer(serverCollection);
        } else {
            throw new ConfigurationException("Please provide at least one server");
        }

        setGetDetail(config.getBoolean("getDetail", false));        
    }

    @Override
    public void destroy() {
        // TODO Auto-generated method stub
        
    }
}
