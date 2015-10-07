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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertPath;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathBuilderResult;
import java.security.cert.CertStore;
import java.security.cert.CertificateException;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.mail.MessagingException;

import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.mail.smime.SMIMESigned;

/**
 * This class is used to handle in a simple way a keystore that contains a set
 * of trusted certificates. It loads the set from the specified keystore (type,
 * location and password are supplied during the object's creation) and it is
 * able to verify a s/mime signature, also checking if the signer's certificate
 * is trusted or not.
 * 
 */
public class KeyStoreHolder {
    
    protected KeyStore keyStore;
    
    public KeyStoreHolder () throws IOException, GeneralSecurityException {
        // this is the default password of the sun trusted certificate store.
        this("changeit");
    }
    
    public KeyStoreHolder (String password) throws IOException, GeneralSecurityException {
        this(System.getProperty("java.home")+"/lib/security/cacerts".replace('/', File.separatorChar), password, KeyStore.getDefaultType());
    }
    
    public KeyStoreHolder(String keyStoreFileName, String keyStorePassword, String keyStoreType) 
        throws KeyStoreException, NoSuchAlgorithmException, CertificateException, NoSuchProviderException, IOException {
        
        if (keyStorePassword == null) keyStorePassword = "";

        try {
            InitJCE.init();
        } catch (InstantiationException e) {
            NoSuchProviderException ex = new NoSuchProviderException("Error during cryptography provider initialization. Has bcprov-jdkxx-yyy.jar been copied in the lib directory or installed in the system?");
            ex.initCause(e);
            throw ex;
        } catch (IllegalAccessException e) {
            NoSuchProviderException ex = new NoSuchProviderException("Error during cryptography provider initialization. Has bcprov-jdkxx-yyy.jar been copied in the lib directory or installed in the system?");
            ex.initCause(e);
            throw ex;
        } catch (ClassNotFoundException e) {
            NoSuchProviderException ex = new NoSuchProviderException("Error during cryptography provider initialization. Has bcprov-jdkxx-yyy.jar been copied in the lib directory or installed in the system?");
            ex.initCause(e);
            throw ex;
        }
        
        if (keyStoreType == null) {
            keyStoreType = KeyStore.getDefaultType();
        }
        
        keyStore = KeyStore.getInstance(keyStoreType);        
        keyStore.load(new BufferedInputStream(new FileInputStream(keyStoreFileName)), keyStorePassword.toCharArray());
        if (keyStore.size() == 0) throw new KeyStoreException("The keystore must be not empty");
    }
    
    /**
     * Verifies the signature of a SMIME message.
     * 
     * It checks also if the signer's certificate is trusted using the loaded
     * keystore as trusted certificate store.
     * 
     * @param signed
     *            the signed mail to check.
     * @return a list of SMIMESignerInfo which keeps the data of each mail
     *         signer.
     * @throws Exception
     * @throws MessagingException
     */
    public List<SMIMESignerInfo> verifySignatures(SMIMESigned signed) throws Exception, MessagingException {
        CertStore certs = signed.getCertificatesAndCRLs("Collection", "BC");
        SignerInformationStore siginfo = signed.getSignerInfos();
        Collection<SignerInformation> sigCol = siginfo.getSigners();
        Iterator<SignerInformation> sigIterator = sigCol.iterator();
        List<SMIMESignerInfo> result = new ArrayList<SMIMESignerInfo>(sigCol.size());
        // I iterate over the signer collection 
        // checking if the signatures put
        // on the message are valid.
        for (int i=0;sigIterator.hasNext();i++) {
            SignerInformation info = sigIterator.next();
            // I get the signer's certificate
            Collection certCollection = certs.getCertificates(info.getSID());
            Iterator<X509Certificate> certIter = certCollection.iterator();
            if (certIter.hasNext()) {
                X509Certificate signerCert = certIter.next();
                // The issuer's certifcate is searched in the list of trusted certificate.
                CertPath path = verifyCertificate(signerCert, certs, keyStore);

                try {
                    // if the signature is valid the SMIMESignedInfo is 
                    // created using "true" as last argument. If it is  
                    // invalid an exception is thrown by the "verify" method
                    // and the SMIMESignerInfo is created with "false".
                    //
                    // The second argument "path" is not null if the 
                    // certificate can be trusted (it can be connected 
                    // by a chain of trust to a trusted certificate), null
                    // otherwise.
                    if (info.verify(signerCert, "BC")) {
                        result.add(new SMIMESignerInfo(signerCert, path, true));
                    }
                } catch (Exception e) { 
                    result.add(new SMIMESignerInfo(signerCert,path, false)); 
                }
            }
        }
        return result;
    }

    /**
     * Verifies the validity of the given certificate, checking its signature
     * against the issuer's certificate.
     * 
     * @param cert
     *            the certificate to validate
     * @param store
     *            other certificates that can be used to create a chain of trust
     *            to a known trusted certificate.
     * @param trustedStore
     *            list of trusted (usually self-signed) certificates.
     * 
     * @return true if the certificate's signature is valid and can be validated
     *         using a trustedCertficated, false otherwise.
     */
    private static CertPath verifyCertificate(X509Certificate cert, CertStore store, KeyStore trustedStore) 
        throws InvalidAlgorithmParameterException, KeyStoreException, MessagingException, CertPathBuilderException {
        
        if (cert == null || store == null || trustedStore == null) throw new IllegalArgumentException("cert == "+cert+", store == "+store+", trustedStore == "+trustedStore);
        
        CertPathBuilder pathBuilder;
        
        // I create the CertPathBuilder object. It will be used to find a
        // certification path that starts from the signer's certificate and
        // leads to a trusted root certificate.
        try {
            pathBuilder = CertPathBuilder.getInstance("PKIX", "BC");
        } catch (Exception e) {
            throw new MessagingException("Error during the creation of the certpathbuilder.", e);
        }
        
        X509CertSelector xcs = new X509CertSelector();
        xcs.setCertificate(cert);
        PKIXBuilderParameters params = new PKIXBuilderParameters(trustedStore, xcs);
        params.addCertStore(store);
        params.setRevocationEnabled(false);
        
        try {
            CertPathBuilderResult result = pathBuilder.build(params);
            CertPath path = result.getCertPath();
            return path;
        } catch (CertPathBuilderException e) {
            // A certification path is not found, so null is returned.
            return null;
        } catch (InvalidAlgorithmParameterException e) {
            // If this exception is thrown an error has occured during
            // certification path search. 
            throw new MessagingException("Error during the certification path search.", e);
        }
    }
}
