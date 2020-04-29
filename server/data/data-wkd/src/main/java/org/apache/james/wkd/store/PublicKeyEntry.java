package org.apache.james.wkd.store;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Represents a PublicKeyEntry for the Web Key Directory.
 * 
 * @author manuel
 *
 */
public class PublicKeyEntry {
    /**
     * The so mapped local-part is hashed using the SHA-1 algorithm. The resulting
     * 160 bit digest is encoded using the Z-Base-32 method as described in
     * [RFC6189], section 5.1.6. The resulting string has a fixed length of 32
     * octets.
     */
    private String hash;
    private Status status;
    private byte[] publicKey;
    
    public PublicKeyEntry() {
        
    }
    
    public PublicKeyEntry(String localPart, byte[] publicKey) {
        setHash(sha1ZBase32(localPart));
        setPublicKey(publicKey);
    }

    public enum Status {
        UNCONFIRMED, CONFIRMED
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(byte[] publicKey) {
        this.publicKey = publicKey;
    }

    public static String sha1ZBase32(String localPart) {
        MessageDigest crypt;
        try {
            crypt = MessageDigest.getInstance("SHA-1");
            crypt.reset();
            crypt.update(localPart.toLowerCase().getBytes("UTF-8"));
            byte[] sha1 = crypt.digest();
            return zBase32(sha1);
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    // Taken from:
    // https://github.com/open-keychain/open-keychain/blob/master/OpenKeychain/src/main/java/org/sufficientlysecure/keychain/util/ZBase32.java

    private static final char[] ALPHABET = "ybndrfg8ejkmcpqxot1uwisza345h769".toCharArray();
    private static final int SHIFT = Integer.numberOfTrailingZeros(ALPHABET.length);
    private static final int MASK = ALPHABET.length - 1;

    /**
     * Function that encodes data according to the
     * following RFC:
     * https://tools.ietf.org/html/rfc6189#section-5.1.6
     * @param data
     * @return
     */
    private static String zBase32(byte[] data) {
        if (data.length == 0) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        int buffer = data[0];
        int index = 1;
        int bitsLeft = 8;
        while (bitsLeft > 0 || index < data.length) {
            if (bitsLeft < SHIFT) {
                if (index < data.length) {
                    buffer <<= 8;
                    buffer |= (data[index++] & 0xff);
                    bitsLeft += 8;
                } else {
                    int pad = SHIFT - bitsLeft;
                    buffer <<= pad;
                    bitsLeft += pad;
                }
            }
            bitsLeft -= SHIFT;
            result.append(ALPHABET[MASK & (buffer >> bitsLeft)]);
        }
        return result.toString();
    }
}
