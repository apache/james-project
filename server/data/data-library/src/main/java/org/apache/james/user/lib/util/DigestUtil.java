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

package org.apache.james.user.lib.util;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

import javax.mail.MessagingException;
import javax.mail.internet.MimeUtility;

import org.apache.james.user.lib.model.Algorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Computes and verifies digests of files and strings
 */
public class DigestUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(DigestUtil.class);

    /**
     * Command line interface. Use -help for arguments.
     * 
     * @param args
     *            the arguments passed in on the command line
     */
    public static void main(String[] args) {

        String alg = "SHA";
        boolean file = false;

        if (args.length == 0 || args.length > 4) {
            printUsage();
            return;
        }

        for (int i = 0; i < args.length; i++) {
            String currArg = args[i].toLowerCase(Locale.US);
            if (currArg.equals("-help") || currArg.equals("-usage")) {
                printUsage();
                return;
            }
            if (currArg.equals("-alg")) {
                alg = args[i + 1];
            }
            if (currArg.equals("-file")) {
                file = true;
            }
        }

        if (file) {
            digestFile(args[args.length - 1], alg);
        } else {
            try {
                String hash = digestString(args[args.length - 1], Algorithm.DEFAULT_FACTORY.of(alg));
                System.out.println("Hash is: " + hash);
            } catch (NoSuchAlgorithmException nsae) {
                System.out.println("No such algorithm available");
            }
        }
    }

    /**
     * Print the command line usage string.
     */
    public static void printUsage() {
        System.out.println("Usage: " + "java org.apache.james.security.DigestUtil" + " [-alg algorithm]" + " [-file] filename|string");
    }

    /**
     * Calculate digest of given file with given algorithm. Writes digest to
     * file named filename.algorithm .
     * 
     * @param filename
     *            the String name of the file to be hashed
     * @param algorithm
     *            the algorithm to be used to compute the digest
     */
    public static void digestFile(String filename, String algorithm) {
        byte[] b = new byte[65536];
        int read;
        try (FileInputStream fis = new FileInputStream(filename)) {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            while (fis.available() > 0) {
                read = fis.read(b);
                md.update(b, 0, read);
            }
            byte[] digest = md.digest();
            String fileNameBuffer = filename + "." + algorithm;
            try (FileOutputStream fos = new FileOutputStream(fileNameBuffer)) {
                OutputStream encodedStream = MimeUtility.encode(fos, "base64");
                encodedStream.write(digest);
                fos.flush();
            }
        } catch (Exception e) {
            LOGGER.error("Error computing Digest", e);
        }
    }

    /**
     * Calculate digest of given String using given algorithm. Encode digest in
     * MIME-like base64.
     * 
     * @param pass
     *            the String to be hashed
     * @param algorithm
     *            the algorithm to be used
     * @return String Base-64 encoding of digest
     * 
     * @throws NoSuchAlgorithmException
     *             if the algorithm passed in cannot be found
     */
    public static String digestString(String pass, Algorithm algorithm) throws NoSuchAlgorithmException {

        MessageDigest md;
        ByteArrayOutputStream bos;

        try {
            md = MessageDigest.getInstance(algorithm.algorithmName());
            byte[] digest = md.digest(pass.getBytes(ISO_8859_1));
            bos = new ByteArrayOutputStream();
            OutputStream encodedStream = MimeUtility.encode(bos, "base64");
            encodedStream.write(digest);
            if (!algorithm.isLegacy()) {
                encodedStream.close();
            }
            return bos.toString(ISO_8859_1);
        } catch (IOException | MessagingException e) {
            throw new RuntimeException("Fatal error", e);
        }
    }

    /**
     * Private constructor to prevent instantiation of the class
     */
    private DigestUtil() {
    }
}
