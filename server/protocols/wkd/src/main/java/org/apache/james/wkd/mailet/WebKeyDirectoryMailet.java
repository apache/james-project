package org.apache.james.wkd.mailet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import javax.inject.Inject;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.MimeMessage;

import org.apache.james.domainlist.api.DomainList;
import org.apache.james.wkd.crypto.WebKeyDirectorySubmissionAddressKeyPairManager;
import org.apache.james.wkd.store.PublicKeyEntry;
import org.apache.james.wkd.store.WebKeyDirectoryStore;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.jcajce.JcaPGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.operator.PublicKeyDataDecryptorFactory;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyDataDecryptorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebKeyDirectoryMailet extends GenericMailet {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebKeyDirectoryMailet.class);

    private DomainList domainList;
    private WebKeyDirectoryStore webKeyDirectoryStore;
    private WebKeyDirectorySubmissionAddressKeyPairManager webKeyDirectorySubmissionAddressKeyPairManager;

    @Inject
    public WebKeyDirectoryMailet(DomainList domainList, WebKeyDirectoryStore webKeyDirectoryStore,
        WebKeyDirectorySubmissionAddressKeyPairManager webKeyDirectorySubmissionAddressKeyPairManager) {
        this.domainList = domainList;
        this.webKeyDirectoryStore = webKeyDirectoryStore;
        this.webKeyDirectorySubmissionAddressKeyPairManager = webKeyDirectorySubmissionAddressKeyPairManager;
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        if (!mail.getState().equals(Mail.GHOST)) {
            doService(mail);
            mail.setState(Mail.GHOST);
        }
    }

    void doService(Mail mail) {
        try {
            MimeMessage message = mail.getMessage();
            Object content = message.getContent();
            if (!(content instanceof Multipart)) {
                LOGGER.error("Expects a mail content of class javax.mail.Multipart but found: "
                    + content.getClass().getName());
                return;
            }
            Multipart parts = (Multipart) content;
            boolean pgpPubliyKeyFound = false;
            for (int i = 0; i < parts.getCount(); i++) {
                BodyPart bodyPart = parts.getBodyPart(i);
                if (bodyPart.isMimeType("application/pgp-encrypted")) {
                    byte[] publicKeyBytesArmored = decryptBodyPartWithPublicKey(
                        bodyPart.getInputStream());

                    JcaPGPPublicKeyRingCollection pgpPub = new JcaPGPPublicKeyRingCollection(
                        PGPUtil.getDecoderStream(new ByteArrayInputStream(publicKeyBytesArmored)));
                    ByteArrayOutputStream boas = new ByteArrayOutputStream();
                    pgpPub.encode(boas);
                    PublicKeyEntry publicKeyEntry = new PublicKeyEntry(
                        mail.getMaybeSender().get().getLocalPart(), boas.toByteArray());
                    webKeyDirectoryStore.put(publicKeyEntry);
                    pgpPubliyKeyFound = true;
                }
            }
            if (!pgpPubliyKeyFound) {
                LOGGER.warn("Did not find public key in email from: "
                    + mail.getMaybeSender().asPrettyString());
            }
        } catch (PGPException | MessagingException | IOException e) {
            LOGGER.error("Could not process key submission request", e);
        }

    }

    byte[] decryptBodyPartWithPublicKey(InputStream inputStream)
        throws IOException, MessagingException, PGPException {
        inputStream = PGPUtil.getDecoderStream(inputStream);

        PGPPrivateKey pGPPrivateKey = null;

        PGPObjectFactory pGPObjectFactory = new PGPObjectFactory(inputStream,
            new BcKeyFingerprintCalculator());
        Object maybeEncryptedData = pGPObjectFactory.nextObject();
        PGPEncryptedDataList pGPEncryptedDataList;
        if (maybeEncryptedData instanceof PGPEncryptedDataList) {
            pGPEncryptedDataList = (PGPEncryptedDataList) maybeEncryptedData;
        } else {
            pGPEncryptedDataList = (PGPEncryptedDataList) pGPObjectFactory.nextObject();
        }

        PGPPublicKeyEncryptedData pGPPublicKeyEncryptedData = null;

        for (Iterator<PGPPublicKeyEncryptedData> iterator = pGPEncryptedDataList
            .getEncryptedDataObjects(); iterator.hasNext();) {
            pGPPublicKeyEncryptedData = iterator.next();
            pGPPrivateKey = webKeyDirectorySubmissionAddressKeyPairManager
                .receivePGPPrivateKey(pGPPublicKeyEncryptedData.getKeyID());
            continue;
        }
        if (pGPPrivateKey == null) {
            throw new IllegalArgumentException("Unable to find secret key to decrypt the message");
        }
        /*
         * Iterator<PGPPublicKeyEncryptedData> pGPPublicKeyEncryptedDataIterator =
         * pGPEncryptedDataList .getEncryptedDataObjects(); PGPPublicKeyEncryptedData
         * pGPPublicKeyEncryptedData = pGPPublicKeyEncryptedDataIterator .next();
         */

        PublicKeyDataDecryptorFactory decryptorFactory = new BcPublicKeyDataDecryptorFactory(
            pGPPrivateKey);
        InputStream decryptedInputStream = pGPPublicKeyEncryptedData
            .getDataStream(decryptorFactory);

        JcaPGPObjectFactory jcaPGPObjectFactory = new JcaPGPObjectFactory(decryptedInputStream);

        Object message = jcaPGPObjectFactory.nextObject();
        if (message instanceof PGPCompressedData) {
            PGPCompressedData cData = (PGPCompressedData) message;
            PGPObjectFactory pgpFact = new PGPObjectFactory(cData.getDataStream(),
                new BcKeyFingerprintCalculator());
            message = pgpFact.nextObject();
        }

        PGPLiteralData pGPLiteralData = (PGPLiteralData) message;

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        InputStream inLd = pGPLiteralData.getDataStream();

        int ch;
        while ((ch = inLd.read()) >= 0) {
            byteArrayOutputStream.write(ch);
        }
        return byteArrayOutputStream.toByteArray();
    }

    PGPPublicKey getKeyFromArmoredBytes(byte[] armoredKey) throws IOException, PGPException {
        InputStream in = new ByteArrayInputStream(armoredKey);
        in = PGPUtil.getDecoderStream(in);

        JcaPGPPublicKeyRingCollection pgpPub = new JcaPGPPublicKeyRingCollection(in);
        in.close();

        PGPPublicKey key = null;
        Iterator<PGPPublicKeyRing> rIt = pgpPub.getKeyRings();
        while (key == null && rIt.hasNext()) {
            PGPPublicKeyRing kRing = rIt.next();
            Iterator<PGPPublicKey> kIt = kRing.getPublicKeys();
            while (key == null && kIt.hasNext()) {
                PGPPublicKey k = kIt.next();

                if (k.isEncryptionKey()) {
                    key = k;
                }
            }
        }
        return key;
    }

}
