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
package org.apache.james.mailbox.postgres.mail.model.openjpa;

import org.jasypt.encryption.pbe.StandardPBEByteEncryptor;

/**
 * Helper class for encrypt and de-crypt data
 * 
 *
 */
public class EncryptDecryptHelper {    

    // Use one static instance as it is thread safe
    private static final StandardPBEByteEncryptor encryptor = new StandardPBEByteEncryptor();
    
    
    /**
     * Set the password for encrypt / de-crypt. This MUST be done before
     * the usage of {@link #getDecrypted(byte[])} and {@link #getEncrypted(byte[])}.
     * 
     * So to be safe its the best to call this in a constructor
     * 
     * @param pass
     */
    public static void init(String pass) {
        encryptor.setPassword(pass);
    }

    /**
     * Encrypt the given array and return the encrypted one
     * 
     * @param array
     * @return enc-array
     */
    public static byte[] getEncrypted(byte[] array) {
        return encryptor.encrypt(array);
    }

    /**
     * Decrypt the given array and return the de-crypted one
     * 
     * @param array
     * @return dec-array
     */
    public static byte[] getDecrypted(byte[] array) {
        return encryptor.decrypt(array);
    }

}
