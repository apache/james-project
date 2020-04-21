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

package org.apache.james.imap.decode;

import java.io.IOException;
import java.util.Objects;

import org.apache.james.imap.api.display.HumanReadableText;

/**
 * <p>
 * Indicates that decoding failured.
 * </p>
 * <p>
 * All decoding exception should be supplied with:
 * </p>
 * <ul>
 * <li>A finely grained descriptive string</li>
 * <li>A coursely grained key for i18n</li>
 * </ul>
 * <p>
 * The following keys are frequently used when decoding:
 * </p>
 * <ul>
 * <li>{@link HumanReadableText#ILLEGAL_ARGUMENTS}</li>
 * <li>{@link HumanReadableText#BAD_IO_ENCODING}</li>
 * </ul>
 */
public class DecodingException extends IOException {

    private static final long serialVersionUID = 8719349386686261422L;

    private final HumanReadableText key;

    private Throwable t;

    /**
     * Constructs a decoding exception
     * 
     * @param key
     *            coursely grained i18n, not null
     * @param s
     *            specific description suitable for logging, not null
     */
    public DecodingException(HumanReadableText key, String s) {
        super(s);
        this.key = key;
    }

    /**
     * Constructs a decoding exception.
     * 
     * @param key
     *            coursely grained i18n, not null
     * @param s
     *            specific description suitable for logging, not null
     * @param t
     *            cause, not null
     */
    public DecodingException(HumanReadableText key, String s, Throwable t) {
        super(s);
        this.key = key;
        this.t = t;
    }

    /**
     * Gets the message key.
     * 
     * @return the key, not null
     */
    public final HumanReadableText getKey() {
        // API specifies not null but best to default to generic message
        return Objects.requireNonNullElse(this.key, HumanReadableText.ILLEGAL_ARGUMENTS);
    }

    @Override
    public Throwable getCause() {
        return t;
    }

}
