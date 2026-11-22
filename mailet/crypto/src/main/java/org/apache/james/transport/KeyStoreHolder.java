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

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.CertPath;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathBuilderResult;
import java.security.cert.CertStore;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.mail.MessagingException;

import org.bouncycastle.cert.jcajce.JcaCertStoreBuilder;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.cms.jcajce.JcaX509CertSelectorConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.mail.smime.SMIMESigned;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

/**
 * This class is used to handle in a simple way a keystore that contains a set
 * of trusted certificates. It loads the set from the specified keystore (type,
 * location and password are supplied during the object's creation) and it is
 * able to verify a s/mime signature, also checking if the signer's certificate
 * is trusted or not.
 * 
 */
public class KeyStoreHolder {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeyStoreHolder.class);
    private static final String BC = BouncyCastleProvider.PROVIDER_NAME;

    protected KeyStore keyStore;

    public KeyStoreHolder(KeyStore keyStore) {
        this.keyStore = keyStore;
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
    public List<SMIMESignerInfo> verifySignatures(SMIMESigned signed) throws Exception {

        CertStore certs = new JcaCertStoreBuilder()
            .addCertificates(signed.getCertificates())
            .addCRLs(signed.getCRLs())
            .build();
        SignerInformationStore siginfo = signed.getSignerInfos();
        Collection<SignerInformation> sigCol = siginfo.getSigners();
        List<SMIMESignerInfo> result = new ArrayList<>(sigCol.size());
        // I iterate over the signer collection 
        // checking if the signatures put
        // on the message are valid.
        for (SignerInformation info: sigCol) {
            // The signer's certificate is the one the SID names: issuer and serial number, or subject key identifier.
            // Other certificates of the message are only candidates for the chain of trust.
            X509CertSelector certSelector = new JcaX509CertSelectorConverter().getCertSelector(info.getSID());
            @SuppressWarnings("unchecked")
            Collection<X509Certificate> certCollection = (Collection<X509Certificate>) certs.getCertificates(certSelector);
            // Several certificates can match a subject key identifier SID (for instance a renewed certificate
            // for the same key): the first one that verifies the signature and that can be trusted is kept.
            Optional<SMIMESignerInfo> signerInfo = Optional.empty();
            for (X509Certificate signerCert : certCollection) {
                // The signature is checked first: building a chain of trust is costly, and useless for a bad
                // signature.
                if (!verify(info, signerCert)) {
                    signerInfo = signerInfo.or(() -> Optional.of(new SMIMESignerInfo(signerCert, null, false)));
                    continue;
                }
                // The path is not null if the certificate can be trusted (it can be connected by a chain of trust
                // to a trusted certificate), null otherwise.
                CertPath path = verifyCertificate(signerCert, certs, keyStore);
                if (path != null) {
                    signerInfo = Optional.of(new SMIMESignerInfo(signerCert, path, true));
                    break;
                }
                if (signerInfo.isEmpty() || !signerInfo.get().isSignValid()) {
                    signerInfo = Optional.of(new SMIMESignerInfo(signerCert, null, true));
                }
            }
            signerInfo.ifPresent(result::add);
        }
        return result;
    }

    private static boolean verify(SignerInformation info, X509Certificate signerCert) {
        try {
            // If the signature is invalid an exception may be thrown by the "verify" method
            return info.verify(new JcaSimpleSignerInfoVerifierBuilder().setProvider(BC).build(signerCert));
        } catch (Exception e) {
            LOGGER.debug("Invalid S/MIME signature for {}", signerCert.getSubjectX500Principal(), e);
            return false;
        }
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

        Preconditions.checkNotNull(cert);
        Preconditions.checkNotNull(store);
        Preconditions.checkNotNull(trustedStore);

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
            return result.getCertPath();
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
