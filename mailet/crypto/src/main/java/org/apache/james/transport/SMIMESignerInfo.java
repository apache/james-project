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

import java.io.Serializable;
import java.security.cert.CertPath;
import java.security.cert.X509Certificate;

public class SMIMESignerInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public X509Certificate signerCertificate;
    public CertPath certPath;
    
    public boolean valid = false;
    
    public SMIMESignerInfo(X509Certificate signerCertificate, CertPath certPath, boolean valid) {
        this.signerCertificate = signerCertificate;
        this.certPath = certPath;
        this.valid = valid;
    }
    
    public X509Certificate getSignerCertificate() {
        return signerCertificate;
    }
    
    public CertPath getCertPath() {
        return certPath;
    }
    
    public boolean isSignValid() {
        return valid;
    }
}
