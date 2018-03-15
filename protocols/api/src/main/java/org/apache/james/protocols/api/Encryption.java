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

package org.apache.james.protocols.api;

import javax.net.ssl.SSLContext;

/**
 * This class should be used to setup encrypted protocol handling
 * 
 */
public final class Encryption {

    private final SSLContext context;
    private final boolean starttls;
    private final String[] enabledCipherSuites;

    private Encryption(SSLContext context, boolean starttls, String[] enabledCipherSuites) {
        this.context = context;
        this.starttls = starttls;
        this.enabledCipherSuites = enabledCipherSuites;
    }

    public static Encryption createTls(SSLContext context) {
        return createTls(context, null);
    }

    /**
     * Create a new {@link Encryption} which is TLS based and only allows the
     * given Ciphersuites
     * 
     * @param context
     * @param enabledCipherSuites
     *            or <code>null</code> if all Ciphersuites should be allowed
     * @return enc
     */
    public static Encryption createTls(SSLContext context, String[] enabledCipherSuites) {
        return new Encryption(context, false, enabledCipherSuites);
    }

    public static Encryption createStartTls(SSLContext context) {
        return createStartTls(context, null);
    }

    /**
     * Create a new {@link Encryption} which uses STARTTLS and only allows the
     * given Ciphersuites
     * 
     * @param context
     * @param enabledCipherSuites
     *            or <code>null</code> if all Ciphersuites should be allowed
     * @return enc
     */
    public static Encryption createStartTls(SSLContext context, String[] enabledCipherSuites) {
        return new Encryption(context, true, enabledCipherSuites);
    }

    /**
     * Return the {@link SSLContext} to use
     * 
     * @return context
     */
    public SSLContext getContext() {
        return context;
    }

    /**
     * Return <code>true</code> if this {@link Encryption} should be used for
     * STARTTLS
     * 
     * @return starttls
     */
    public boolean isStartTLS() {
        return starttls;
    }

    /**
     * Return the Ciphersuites that are allowed for the {@link Encryption} or
     * <code>null</code> if all should be allowed
     * 
     * @return ciphersuites
     */
    public String[] getEnabledCipherSuites() {
        return enabledCipherSuites;
    }
}
