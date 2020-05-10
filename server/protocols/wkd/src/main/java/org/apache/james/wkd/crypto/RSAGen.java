package org.apache.james.wkd.crypto;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Date;

import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.bcpg.sig.Features;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.openpgp.PGPEncryptedData;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.operator.PBESecretKeyEncryptor;
import org.bouncycastle.openpgp.operator.PGPDigestCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyEncryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.bc.BcPGPKeyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Taken from
 * https://stackoverflow.com/questions/28245669/using-bouncy-castle-to-create-public-pgp-key-usable-by-thunderbird
 * 
 * @author manuel
 *
 */
public class RSAGen {

    private static final Logger LOGGER = LoggerFactory.getLogger(RSAGen.class);

    public static final PGPKeyRingGenerator generateKeyRingGenerator(String id, char[] pass) {
        return generateKeyRingGenerator(id, pass, 0xc0);
    }

    // Note: s2kcount is a number between 0 and 0xff that controls the number of
    // times to iterate the password hash before use. More
    // iterations are useful against offline attacks, as it takes more time to check
    // each password. The actual number of iterations is
    // rather complex, and also depends on the hash function in use. Refer to
    // Section 3.7.1.3 in rfc4880.txt. Bigger numbers give
    // you more iterations. As a rough rule of thumb, when using SHA256 as the
    // hashing function, 0x10 gives you about 64
    // iterations, 0x20 about 128, 0x30 about 256 and so on till 0xf0, or about 1
    // million iterations. The maximum you can go to is
    // 0xff, or about 2 million iterations. I'll use 0xc0 as a default -- about
    // 130,000 iterations.

    public static final PGPKeyRingGenerator generateKeyRingGenerator(String id, char[] pass,
        int s2kcount) {
        try {
            // This object generates individual key-pairs.
            RSAKeyPairGenerator kpg = new RSAKeyPairGenerator();

            // Boilerplate RSA parameters, no need to change anything
            // except for the RSA key-size (2048). You can use whatever key-size makes sense
            // for you -- 4096, etc.
            kpg.init(new RSAKeyGenerationParameters(BigInteger.valueOf(0x10001), new SecureRandom(),
                2048, 12));

            // First create the master (signing) key with the generator.
            PGPKeyPair rsakpSign;
            rsakpSign = new BcPGPKeyPair(PGPPublicKey.RSA_SIGN, kpg.generateKeyPair(), new Date());

            // Then an encryption subkey.
            PGPKeyPair rsakpEnc = new BcPGPKeyPair(PGPPublicKey.RSA_ENCRYPT, kpg.generateKeyPair(),
                new Date());

            // Add a self-signature on the id
            PGPSignatureSubpacketGenerator signhashgen = new PGPSignatureSubpacketGenerator();

            // Add signed metadata on the signature.
            // 1) Declare its purpose
            signhashgen.setKeyFlags(false, KeyFlags.SIGN_DATA | KeyFlags.CERTIFY_OTHER);
            // 2) Set preferences for secondary crypto algorithms to use when sending
            // messages to this key.
            signhashgen.setPreferredSymmetricAlgorithms(false,
                new int[] { SymmetricKeyAlgorithmTags.AES_256, SymmetricKeyAlgorithmTags.AES_192,
                    SymmetricKeyAlgorithmTags.AES_128 });
            signhashgen.setPreferredHashAlgorithms(false,
                new int[] { HashAlgorithmTags.SHA256, HashAlgorithmTags.SHA1,
                    HashAlgorithmTags.SHA384, HashAlgorithmTags.SHA512,
                    HashAlgorithmTags.SHA224, });
            // 3) Request senders add additional checksums to the message (useful when
            // verifying unsigned messages.)
            signhashgen.setFeature(false, Features.FEATURE_MODIFICATION_DETECTION);

            // Create a signature on the encryption subkey.
            PGPSignatureSubpacketGenerator enchashgen = new PGPSignatureSubpacketGenerator();
            // Add metadata to declare its purpose
            enchashgen.setKeyFlags(false, KeyFlags.ENCRYPT_COMMS | KeyFlags.ENCRYPT_STORAGE);

            // Objects used to encrypt the secret key.
            PGPDigestCalculator sha1Calc = new BcPGPDigestCalculatorProvider()
                .get(HashAlgorithmTags.SHA1);
            PGPDigestCalculator sha256Calc = new BcPGPDigestCalculatorProvider()
                .get(HashAlgorithmTags.SHA256);

            // bcpg 1.48 exposes this API that includes s2kcount. Earlier versions use a
            // default of 0x60.
            PBESecretKeyEncryptor pske = (new BcPBESecretKeyEncryptorBuilder(
                PGPEncryptedData.AES_256, sha256Calc, s2kcount)).build(pass);

            // Finally, create the keyring itself. The constructor takes parameters that
            // allow it to generate the self signature.
            PGPKeyRingGenerator keyRingGen = new PGPKeyRingGenerator(
                PGPSignature.POSITIVE_CERTIFICATION, rsakpSign, id, sha1Calc,
                signhashgen.generate(), null, new BcPGPContentSignerBuilder(
                    rsakpSign.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA1),
                pske);

            // Add our encryption subkey, together with its signature.
            keyRingGen.addSubKey(rsakpEnc, enchashgen.generate(), null);
            return keyRingGen;
        } catch (PGPException e) {
            LOGGER.error("Could not generate Key Pair", e);
            return null;
        }
    }
}