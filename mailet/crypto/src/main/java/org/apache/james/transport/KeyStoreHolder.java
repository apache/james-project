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
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CRL;
import java.security.cert.CertPath;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertStore;
import java.security.cert.CertStoreException;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathBuilderResult;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CRL;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.mail.MessagingException;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaCertStoreBuilder;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.cms.jcajce.JcaX509CertSelectorConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.mail.smime.SMIMESigned;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * This class is used to handle in a simple way a keystore that contains a set
 * of trusted certificates. It loads the set from the specified keystore (type,
 * location and password are supplied during the object's creation) and it is
 * able to verify a s/mime signature, also checking if the signer's certificate
 * is trusted or not.
 * 
 */
public class KeyStoreHolder {
    /**
     * Bounds the work a remote sender can make the signature check do: each SignerInfo can lead to a certification
     * path search over all the certificates of the message.
     */
    public record VerificationLimits(int maxSignerInfos, int maxCertificates, int maxCrls) {
        /**
         * The number of certification path searches per message is bounded by maxSignerInfos.
         */
        int maxCertPathSearches() {
            return maxSignerInfos;
        }

        public static final int DEFAULT_MAX_CRLS = 16;
        public static final VerificationLimits DEFAULT = new VerificationLimits(10, 50, DEFAULT_MAX_CRLS);

        public VerificationLimits {
            Preconditions.checkArgument(maxSignerInfos > 0, "maxSignerInfos must be strictly positive");
            Preconditions.checkArgument(maxCertificates > 0, "maxCertificates must be strictly positive");
            Preconditions.checkArgument(maxCrls >= 0, "maxCrls must be positive");
        }

        public VerificationLimits(int maxSignerInfos, int maxCertificates) {
            this(maxSignerInfos, maxCertificates, DEFAULT_MAX_CRLS);
        }
    }

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
        return verifySignatures(signed, VerificationLimits.DEFAULT);
    }

    /**
     * Verifies the signature of a SMIME message, like {@link #verifySignatures(SMIMESigned)}.
     *
     * A message with more SignerInfos, certificates or revocation lists than the limits allow is not checked: no
     * signer is returned.
     */
    @SuppressWarnings("unchecked")
    public List<SMIMESignerInfo> verifySignatures(SMIMESigned signed, VerificationLimits limits) throws Exception {
        int certificateCount = signed.getCertificates().getMatches(null).size();
        int signerInfoCount = signed.getSignerInfos().size();
        if (certificateCount > limits.maxCertificates() || signerInfoCount > limits.maxSignerInfos()) {
            LOGGER.warn("Not checking a S/MIME signature with {} SignerInfos and {} certificates: limits are {} and {}",
                signerInfoCount, certificateCount, limits.maxSignerInfos(), limits.maxCertificates());
            return ImmutableList.of();
        }
        // Each revocation list is checked against each certificate of a path
        int crlCount = signed.getCRLs().getMatches(null).size();
        if (crlCount > limits.maxCrls()) {
            LOGGER.warn("Not checking a S/MIME signature with {} revocation lists: the limit is {}", crlCount, limits.maxCrls());
            return ImmutableList.of();
        }

        CertStore certs = new JcaCertStoreBuilder()
            .addCertificates(signed.getCertificates())
            .addCRLs(signed.getCRLs())
            .build();
        // Each node of a certification path search scans every candidate sharing the name of the issuer it looks for:
        // a sender shipping many certificates sharing a few names would make the search explore their permutations.
        // A legitimate chain holds very few certificates per name (key rollover, cross certification).
        Collection<X509Certificate> intermediates = boundCandidatesPerSubject(
            (Collection<X509Certificate>) certs.getCertificates(null));
        Collection<? extends CRL> crls = certs.getCRLs(null);
        AtomicInteger remainingCertPathSearches = new AtomicInteger(limits.maxCertPathSearches());
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
                CertPath path = null;
                if (remainingCertPathSearches.getAndDecrement() > 0) {
                    path = verifyCertificate(signerCert, pathStore(signerCert, intermediates, crls), keyStore);
                } else {
                    LOGGER.warn("Not searching a certification path for {}: {} searches were already done for this message",
                        signerCert.getSubjectX500Principal(), limits.maxCertPathSearches());
                }
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

    @VisibleForTesting
    static final int MAX_CANDIDATES_PER_SUBJECT = 4;

    private static Collection<X509Certificate> boundCandidatesPerSubject(Collection<X509Certificate> certificates) {
        Map<X500Name, Integer> countBySubject = new HashMap<>();
        // X500Name compares names the way the BouncyCastle path builder does
        return certificates.stream()
            .filter(certificate -> countBySubject.merge(X500Name.getInstance(certificate.getSubjectX500Principal().getEncoded()), 1, Integer::sum)
                <= MAX_CANDIDATES_PER_SUBJECT)
            .collect(ImmutableList.toImmutableList());
    }

    private static CertStore pathStore(X509Certificate signerCert, Collection<X509Certificate> intermediates, Collection<? extends CRL> crls)
        throws InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        return CertStore.getInstance("Collection", new CollectionCertStoreParameters(ImmutableList.builder()
            .add(signerCert)
            .addAll(intermediates.stream().filter(certificate -> !certificate.equals(signerCert)).iterator())
            // The revocation lists carried by the message
            .addAll(crls)
            .build()));
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
     * Soft fail revocation check: a certificate of the path listed by a revocation list carried by the message, and
     * signed by its issuer, is revoked. Without such a list, nothing changes. Nothing is fetched from the network:
     * neither OCSP nor the distribution points of the certificates.
     */
    @SuppressWarnings("unchecked")
    private static boolean revokedByAvailableCrl(PKIXCertPathBuilderResult result, CertStore store) throws CertStoreException {
        Collection<X509CRL> crls = (Collection<X509CRL>) store.getCRLs(null);
        if (crls.isEmpty()) {
            return false;
        }
        List<X509Certificate> path = (List<X509Certificate>) result.getCertPath().getCertificates();
        for (int i = 0; i < path.size(); i++) {
            X509Certificate certificate = path.get(i);
            PublicKey issuerKey = i + 1 < path.size() ? path.get(i + 1).getPublicKey() : anchorKey(result.getTrustAnchor());
            boolean revoked = crls.stream()
                .filter(crl -> crl.getIssuerX500Principal().equals(certificate.getIssuerX500Principal()))
                .filter(crl -> signedBy(crl, issuerKey))
                .anyMatch(crl -> crl.isRevoked(certificate));
            if (revoked) {
                LOGGER.info("{} is revoked by a revocation list of the message", certificate.getSubjectX500Principal());
                return true;
            }
        }
        return false;
    }

    private static PublicKey anchorKey(TrustAnchor anchor) {
        return Optional.ofNullable(anchor.getTrustedCert())
            .map(X509Certificate::getPublicKey)
            .orElseGet(anchor::getCAPublicKey);
    }

    private static boolean signedBy(X509CRL crl, PublicKey issuerKey) {
        try {
            crl.verify(issuerKey, BC);
            return true;
        } catch (Exception e) {
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
            PKIXCertPathBuilderResult result = (PKIXCertPathBuilderResult) pathBuilder.build(params);
            if (revokedByAvailableCrl(result, store)) {
                return null;
            }
            return result.getCertPath();
        } catch (CertPathBuilderException | CertStoreException e) {
            // A certification path is not found, so null is returned.
            return null;
        } catch (InvalidAlgorithmParameterException e) {
            // If this exception is thrown an error has occured during
            // certification path search. 
            throw new MessagingException("Error during the certification path search.", e);
        }
    }
}
