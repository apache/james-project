package org.apache.james.wkd.crypto;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;

import javax.inject.Inject;

import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.wkd.WebKeyDirectoryConfiguration;
import org.apache.james.wkd.store.PublicKeyEntry;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebKeyDirectorySubmissionAddressKeyPairManager {

    private static final Logger LOGGER = LoggerFactory
        .getLogger(WebKeyDirectorySubmissionAddressKeyPairManager.class);

    DomainList domainList;

    private static final String PRIVATE_KEY_PASSWORD = "secret";
    PGPPublicKeyRing publicKeySubmissionAddress = null;
    PGPSecretKeyRing privateKeySubmissionAddress = null;

    FileSystem fileSystem;

    String configurationPrefix;

    @Inject
    public WebKeyDirectorySubmissionAddressKeyPairManager(DomainList domainList,
        FileSystem fileSystem,
        org.apache.james.server.core.configuration.Configuration configuration) {
        this.domainList = domainList;
        this.fileSystem = fileSystem;
        this.configurationPrefix = configuration.configurationPath();
    }

    public PublicKeyEntry getPublicKeyEntryForSubmissionAddress() {
        try {
            makeSureKeysAreLoaded();

            ByteArrayOutputStream pubout = new ByteArrayOutputStream();
            this.publicKeySubmissionAddress.encode(pubout);
            pubout.close();
            return new PublicKeyEntry(WebKeyDirectoryConfiguration.SUBMISSION_ADDRESS_LOCAL_PART,
                pubout.toByteArray());
        } catch (IOException | DomainListException | PGPException e) {
            LOGGER.error("Could not generate or read public and private key for submission address",
                e);
            return null;
        }
    }

    private void makeSureKeysAreLoaded()
        throws FileNotFoundException, IOException, PGPException, DomainListException {
        if (publicKeySubmissionAddress == null || privateKeySubmissionAddress == null) {
            File privateKey = fileSystem.getFile(configurationPrefix + "submission-address.key");
            if (privateKey.exists()) {
                privateKeySubmissionAddress = readSecretKey(new FileInputStream(privateKey));
                File publicKey = fileSystem.getFile(configurationPrefix + "submission-address.pub");
                publicKeySubmissionAddress = readPublicKey(new FileInputStream(publicKey));
            } else {
                generateAndSaveKeyPair();
            }
        }
    }

    private void generateAndSaveKeyPair() throws DomainListException {
        String defaultDomain = domainList.getDefaultDomain().asString();
        String submissionAddress = WebKeyDirectoryConfiguration.SUBMISSION_ADDRESS_LOCAL_PART + "@"
            + defaultDomain;
        LOGGER.info("Generating key pair for " + submissionAddress);
        PGPKeyRingGenerator krgen;

        krgen = RSAGen.generateKeyRingGenerator(submissionAddress,
            PRIVATE_KEY_PASSWORD.toCharArray());

        // Generate public key ring, dump to file.
        this.publicKeySubmissionAddress = krgen.generatePublicKeyRing();

        // Generate private key, dump to file.
        this.privateKeySubmissionAddress = krgen.generateSecretKeyRing();
        saveKeyPair();
    }

    private void saveKeyPair() {
        try {
            File privateKey = fileSystem.getFile(configurationPrefix + "submission-address.key");
            OutputStream out = new ArmoredOutputStream(new FileOutputStream(privateKey));
            privateKeySubmissionAddress.encode(out);
            out.close();
            File publicKey = fileSystem.getFile(configurationPrefix + "submission-address.pub");
            out = new ArmoredOutputStream(new FileOutputStream(publicKey));
            publicKeySubmissionAddress.encode(out);
            out.close();

        } catch (IOException e) {
            LOGGER.error("Could not save key pair", e);
        }
    }

    public PGPPrivateKey receivePGPPrivateKey(long keyId) {
        try {
            makeSureKeysAreLoaded();
            PGPSecretKey pgpSecKey = privateKeySubmissionAddress.getSecretKey(keyId);

            if (pgpSecKey == null) {
                return null;
            }

            return pgpSecKey.extractPrivateKey(
                new BcPBESecretKeyDecryptorBuilder(new BcPGPDigestCalculatorProvider())
                    .build(PRIVATE_KEY_PASSWORD.toCharArray()));
        } catch (PGPException | IOException | DomainListException e) {
            LOGGER.error("Could not receive pgp private key", e);
            return null;
        }
    }

    /**
     * A simple routine that opens a key ring file and loads the first available key
     * suitable for encryption.
     * 
     * @param input data stream containing the public key data
     * @return the first public key found.
     * @throws IOException
     * @throws PGPException
     */
    static PGPPublicKeyRing readPublicKey(InputStream input) throws IOException, PGPException {
        PGPPublicKeyRingCollection pgpPub = new PGPPublicKeyRingCollection(
            PGPUtil.getDecoderStream(input), new JcaKeyFingerprintCalculator());

        //
        // we just loop through the collection till we find a key suitable for
        // encryption, in the real
        // world you would probably want to be a bit smarter about this.
        //

        Iterator<PGPPublicKeyRing> keyRingIter = pgpPub.getKeyRings();
        while (keyRingIter.hasNext()) {
            PGPPublicKeyRing keyRing = keyRingIter.next();
            return keyRing;
        }

        throw new IllegalArgumentException("Can't find encryption key in key ring.");
    }

    /**
     * A simple routine that opens a key ring file and loads the first available key
     * suitable for signature generation.
     * 
     * @param input stream to read the secret key ring collection from.
     * @return a secret key.
     * @throws IOException  on a problem with using the input stream.
     * @throws PGPException if there is an issue parsing the input stream.
     */
    static PGPSecretKeyRing readSecretKey(InputStream input) throws IOException, PGPException {
        PGPSecretKeyRingCollection pgpSec = new PGPSecretKeyRingCollection(
            PGPUtil.getDecoderStream(input), new JcaKeyFingerprintCalculator());

        //
        // we just loop through the collection till we find a key suitable for
        // encryption, in the real
        // world you would probably want to be a bit smarter about this.
        //

        Iterator<PGPSecretKeyRing> keyRingIter = pgpSec.getKeyRings();
        while (keyRingIter.hasNext()) {
            PGPSecretKeyRing keyRing = keyRingIter.next();
            return keyRing;
        }

        throw new IllegalArgumentException("Can't find signing key in key ring.");
    }
}
