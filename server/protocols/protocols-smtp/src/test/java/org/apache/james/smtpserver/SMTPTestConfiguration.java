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

package org.apache.james.smtpserver;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.smtpserver.fastfail.DNSRBLHandler;
import org.apache.james.smtpserver.fastfail.MaxRcptHandler;
import org.apache.james.smtpserver.fastfail.ResolvableEhloHeloHandler;
import org.apache.james.smtpserver.fastfail.ReverseEqualsEhloHeloHandler;
import org.apache.james.smtpserver.fastfail.ValidSenderDomainHandler;

public class SMTPTestConfiguration extends BaseHierarchicalConfiguration {

    private int maxMessageSizeKB = 0;
    private String authorizedAddresses = "127.0.0.0/8";
    private String authorizingMode = "false";
    private boolean verifyIdentity = false;
    private Integer connectionLimit = null;
    private Integer connectionBacklog = null;
    private boolean heloResolv = false;
    private boolean ehloResolv = false;
    private boolean senderDomainResolv = false;
    private boolean checkAuthNetworks = false;
    private boolean heloEhloEnforcement = true;
    private boolean reverseEqualsHelo = false;
    private boolean reverseEqualsEhlo = false;
    private int maxRcpt = 0;
    private boolean useRBL = false;
    private boolean addressBracketsEnforcement = true;
    private boolean startTLS = false;

    public void setCheckAuthNetworks(boolean checkAuth) {
        checkAuthNetworks = checkAuth;
    }

    public void setMaxMessageSize(int kilobytes) {
        maxMessageSizeKB = kilobytes;
    }

    public void setAuthorizedAddresses(String authorizedAddresses) {
        this.authorizedAddresses = authorizedAddresses;
    }


    public void setAuthorizingAnnounce() {
        authorizingMode = "announce";
        verifyIdentity = true;
    }

    public void setConnectionLimit(int iConnectionLimit) {
        connectionLimit = iConnectionLimit;
    }

    public void setHeloResolv() {
        heloResolv = true;
    }

    public void setEhloResolv() {
        ehloResolv = true;
    }

    public void setReverseEqualsHelo() {
        reverseEqualsHelo = true;
    }

    public void setReverseEqualsEhlo() {
        reverseEqualsEhlo = true;
    }

    public void setSenderDomainResolv() {
        senderDomainResolv = true;
    }

    public void setMaxRcpt(int maxRcpt) {
        this.maxRcpt = maxRcpt;
    }

    public void setHeloEhloEnforcement(boolean heloEhloEnforcement) {
        this.heloEhloEnforcement = heloEhloEnforcement;
    }

    public void useRBL(boolean useRBL) {
        this.useRBL = useRBL;
    }

    public void setAddressBracketsEnforcement(boolean addressBracketsEnforcement) {
        this.addressBracketsEnforcement = addressBracketsEnforcement;
    }

    public void setStartTLS() {
        startTLS = true;
    }

    public void init() {

        addProperty("[@enabled]", true);

        addProperty("bind", "127.0.0.1:0");
        if (connectionLimit != null) {
            addProperty("connectionLimit", "" + connectionLimit);
        }
        if (connectionBacklog != null) {
            addProperty("connectionBacklog", "" + connectionBacklog);
        }

        addProperty("helloName", "myMailServer");
        addProperty("connectiontimeout", 360000);
        addProperty("authorizedAddresses", authorizedAddresses);
        addProperty("maxmessagesize", maxMessageSizeKB);
        addProperty("authRequired", authorizingMode);
        addProperty("heloEhloEnforcement", heloEhloEnforcement);
        addProperty("addressBracketsEnforcement", addressBracketsEnforcement);

        addProperty("tls.[@startTLS]", startTLS);
        addProperty("tls.keystore", "test_keystore");
        addProperty("tls.secret", "jamestest");
        addProperty("auth.requireSSL", false);
        addProperty("verifyIdentity", verifyIdentity);
        addProperty("gracefulShutdown", false);

        // add the rbl handler
        if (useRBL) {

            addProperty("handlerchain.handler.[@class]", DNSRBLHandler.class.getName());
            addProperty("handlerchain.handler.rblservers.blacklist", "bl.spamcop.net.");
        }
        if (heloResolv || ehloResolv) {
            addProperty("handlerchain.handler.[@class]", ResolvableEhloHeloHandler.class.getName());
            addProperty("handlerchain.handler.checkAuthNetworks", checkAuthNetworks);
        }
        if (reverseEqualsHelo || reverseEqualsEhlo) {
            addProperty("handlerchain.handler.[@class]", ReverseEqualsEhloHeloHandler.class.getName());
            addProperty("handlerchain.handler.checkAuthNetworks", checkAuthNetworks);
        }
        if (senderDomainResolv) {
            addProperty("handlerchain.handler.[@class]", ValidSenderDomainHandler.class.getName());
            addProperty("handlerchain.handler.checkAuthNetworks", checkAuthNetworks);
        }
        if (maxRcpt > 0) {
            addProperty("handlerchain.handler.[@class]", MaxRcptHandler.class.getName());
            addProperty("handlerchain.handler.maxRcpt", maxRcpt);
        }
        addProperty("handlerchain.[@coreHandlersPackage]", CoreCmdHandlerLoader.class.getName());
    }

}
