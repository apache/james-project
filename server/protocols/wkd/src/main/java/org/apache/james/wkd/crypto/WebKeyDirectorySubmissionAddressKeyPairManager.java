package org.apache.james.wkd.crypto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.inject.Inject;

import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.wkd.WebKeyDirectoryConfiguration;
import org.apache.james.wkd.store.PublicKeyEntry;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebKeyDirectorySubmissionAddressKeyPairManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(WebKeyDirectorySubmissionAddressKeyPairManager.class);

    private DomainList domainList;
    
    private static final String PRIVATE_KEY_PASSWORD = "secret";
    PGPPublicKeyRing publicKeySubmissionAddress = null;
    PGPSecretKeyRing privateKeySubmissionAddress = null;

    @Inject
    public WebKeyDirectorySubmissionAddressKeyPairManager(DomainList domainList) {
        this.domainList = domainList;
    }
    

    public PublicKeyEntry getPublicKeyEntryForSubmissionAddress() {
        try {
            if (publicKeySubmissionAddress == null || privateKeySubmissionAddress == null) {
                String defaultDomain = domainList.getDefaultDomain().asString();
                String submissionAddress = WebKeyDirectoryConfiguration.SUBMISSION_ADDRESS_LOCAL_PART
                    + "@" + defaultDomain;
                LOGGER.info("Generating key pair for " + submissionAddress);
                PGPKeyRingGenerator krgen;

                krgen = RSAGen.generateKeyRingGenerator(submissionAddress,
                    PRIVATE_KEY_PASSWORD.toCharArray());

                // Generate public key ring, dump to file.
                this.publicKeySubmissionAddress = krgen.generatePublicKeyRing();

                // Generate private key, dump to file.
                this.privateKeySubmissionAddress = krgen.generateSecretKeyRing();
            }

            ByteArrayOutputStream pubout = new ByteArrayOutputStream();
            this.publicKeySubmissionAddress.encode(pubout);
            pubout.close();
            return new PublicKeyEntry(WebKeyDirectoryConfiguration.SUBMISSION_ADDRESS_LOCAL_PART,
                pubout.toByteArray());
        } catch (IOException | DomainListException e) {
            LOGGER.error("Could not generate public key for submission address", e);
            return null;
        }

    }
}
