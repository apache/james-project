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

import java.util.Optional;

import javax.inject.Inject;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.core.MaybeSender;
import org.apache.james.jspf.core.DNSService;
import org.apache.james.jspf.core.exceptions.SPFErrorConstants;
import org.apache.james.jspf.executor.SPFResult;
import org.apache.james.jspf.impl.DefaultSPF;
import org.apache.james.jspf.impl.SPF;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.hook.MailHook;
import org.apache.james.smtpserver.JamesMessageHook;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SPFHandler implements JamesMessageHook, MailHook, ProtocolHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SPFHandler.class);

    /** This log is the fall back shared by all instances */
    private static final Logger FALLBACK_LOG = LoggerFactory.getLogger(SPFHandler.class);

    /**
     * Non context specific log should only be used when no context specific log
     * is available
     */
    private final Logger serviceLog = FALLBACK_LOG;

    private static final ProtocolSession.AttachmentKey<Boolean> SPF_BLOCKLISTED = ProtocolSession.AttachmentKey.of("SPF_BLOCKLISTED", Boolean.class);

    private static final ProtocolSession.AttachmentKey<String> SPF_DETAIL = ProtocolSession.AttachmentKey.of("SPF_DETAIL", String.class);

    private static final ProtocolSession.AttachmentKey<Boolean> SPF_TEMPBLOCKLISTED = ProtocolSession.AttachmentKey.of("SPF_TEMPBLOCKLISTED", Boolean.class);

    private static final ProtocolSession.AttachmentKey<String> SPF_HEADER = ProtocolSession.AttachmentKey.of("SPF_HEADER", String.class);

    private static final AttributeName SPF_HEADER_MAIL_ATTRIBUTE_NAME = AttributeName.of("org.apache.james.spf.header");

    /** If set to true the mail will also be rejected on a softfail */
    private boolean blockSoftFail = false;

    private boolean blockPermError = true;

    private SPF spf = new DefaultSPF();

    /**
     * block the email on a softfail
     *
     * @param blockSoftFail
     *            true or false
     */
    public void setBlockSoftFail(boolean blockSoftFail) {
        this.blockSoftFail = blockSoftFail;
    }

    /**
     * block the email on a permerror
     *
     * @param blockPermError
     *            true or false
     */
    public void setBlockPermError(boolean blockPermError) {
        this.blockPermError = blockPermError;
    }

    /**
     * DNSService to use
     *
     * @param dnsService
     *            The DNSService
     */
    @Inject
    public void setDNSService(DNSService dnsService) {
        spf = new SPF(dnsService);
    }

    /**
     * Calls a SPF check
     *
     * @param session
     *            SMTP session object
     */
    private void doSPFCheck(SMTPSession session, MaybeSender sender) {
        Optional<String> heloEhlo = session.getAttachment(SMTPSession.CURRENT_HELO_NAME, State.Connection);

        // We have no Sender or HELO/EHLO yet return false
        if (sender.isNullSender() || !heloEhlo.isPresent()) {
            LOGGER.info("No Sender or HELO/EHLO present");
        } else {

            String ip = session.getRemoteAddress().getAddress().getHostAddress();

            SPFResult result = spf.checkSPF(ip, sender.asString(), heloEhlo.get());

            String spfResult = result.getResult();

            String explanation = "Blocked - see: " + result.getExplanation();

            // Store the header
            session.setAttachment(SPF_HEADER, result.getHeaderText(), State.Transaction);

            LOGGER.info("Result for {} - {} - {} = {}", ip, sender.asString(), heloEhlo, spfResult);

            // Check if we should block!
            if ((spfResult.equals(SPFErrorConstants.FAIL_CONV)) || (spfResult.equals(SPFErrorConstants.SOFTFAIL_CONV) && blockSoftFail) || (spfResult.equals(SPFErrorConstants.PERM_ERROR_CONV) && blockPermError)) {

                if (spfResult.equals(SPFErrorConstants.PERM_ERROR_CONV)) {
                    explanation = "Block caused by an invalid SPF record";
                }
                session.setAttachment(SPF_DETAIL, explanation, State.Transaction);
                session.setAttachment(SPF_BLOCKLISTED, true, State.Transaction);

            } else if (spfResult.equals(SPFErrorConstants.TEMP_ERROR_CONV)) {
                session.setAttachment(SPF_TEMPBLOCKLISTED, true, State.Transaction);
            }

        }

    }

    @Override
    public HookResult doMail(SMTPSession session, MaybeSender sender) {
        if (!session.isRelayingAllowed()) {
            doSPFCheck(session, sender);

            // Check if session is blocklisted
            if (session.getAttachment(SPF_BLOCKLISTED, State.Transaction).isPresent()) {

                return HookResult.builder()
                    .hookReturnCode(HookReturnCode.deny())
                    .smtpDescription(DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.SECURITY_AUTH) + " " + session.getAttachment(SPF_TEMPBLOCKLISTED, State.Transaction).orElse(false))
                    .build();
            } else if (session.getAttachment(SPF_TEMPBLOCKLISTED, State.Transaction).isPresent()) {
                return HookResult.builder()
                    .hookReturnCode(HookReturnCode.denySoft())
                    .smtpReturnCode(SMTPRetCode.LOCAL_ERROR)
                    .smtpDescription(DSNStatus.getStatus(DSNStatus.TRANSIENT, DSNStatus.NETWORK_DIR_SERVER) + " Temporarily rejected: Problem on SPF lookup")
                    .build();
            }
        }
        return HookResult.DECLINED;
    }

    @Override
    public HookResult onMessage(SMTPSession session, Mail mail) {
        // Store the spf header as attribute for later using (when present)
        session.getAttachment(SPF_HEADER, State.Transaction).ifPresent(s ->
                mail.setAttribute(new Attribute(SPF_HEADER_MAIL_ATTRIBUTE_NAME, AttributeValue.of(s)))
        );

        return null;
    }

    @Override
    public void init(Configuration config) throws ConfigurationException {
        setBlockSoftFail(config.getBoolean("blockSoftFail", false));
        setBlockPermError(config.getBoolean("blockPermError", true));
    }
}
