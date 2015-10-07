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



package org.apache.james.transport;

import java.security.Provider;
import java.security.Security;

import javax.activation.CommandMap;
import javax.activation.MailcapCommandMap;

/**
 * Security Providers initialization class. The first call of the init method
 * will have the class loader do the job. This technique ensures proper
 * initialization without the need of maintaining the
 * <i>${java_home}/lib/security/java.security</i> file, that would otherwise
 * need the addition of the following line:
 * <code>security.provider.<i>n</i>=org.bouncycastle.jce.provider.BouncyCastleProvider</code>.
 * 
 * The call also registers to the javamail's MailcapCommandMap the content
 * handlers that are needed to work with s/mime mails.
 * 
 */
public class InitJCE {
    private static boolean initialized = false;

    /**
     * Method that registers the security provider BouncyCastle as a system
     * security provider. The provider class is dinamically loaded on runtime so
     * there is no need to include the bouncycastle jar in the James
     * distribution. It can be downloaded and installed by the user if she needs
     * it.
     */        
    public static void init() throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        if (!initialized) {
            String bouncyCastleProviderClassName = "org.bouncycastle.jce.provider.BouncyCastleProvider";
            Security.addProvider((Provider)Class.forName(bouncyCastleProviderClassName).newInstance());
            
            MailcapCommandMap mailcap = (MailcapCommandMap) CommandMap.getDefaultCommandMap();

            mailcap.addMailcap("application/pkcs7-signature;; x-java-content-handler=org.bouncycastle.mail.smime.handlers.pkcs7_signature");
            mailcap.addMailcap("application/pkcs7-mime;; x-java-content-handler=org.bouncycastle.mail.smime.handlers.pkcs7_mime");
            mailcap.addMailcap("application/x-pkcs7-signature;; x-java-content-handler=org.bouncycastle.mail.smime.handlers.x_pkcs7_signature");
            mailcap.addMailcap("application/x-pkcs7-mime;; x-java-content-handler=org.bouncycastle.mail.smime.handlers.x_pkcs7_mime");
            mailcap.addMailcap("multipart/signed;; x-java-content-handler=org.bouncycastle.mail.smime.handlers.multipart_signed");

            CommandMap.setDefaultCommandMap(mailcap);
            
            initialized = true;
        } else {
        }
    }
}
