/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.mailbox.exception;

import org.apache.james.mailbox.model.MailboxACL.MailboxACLRight;

/**
 * Thrown when the current system does not support the given right.
 * 
 */
public class UnsupportedRightException extends MailboxSecurityException {

    private static final char INVALID_RIGHT = 0;
    private static final long serialVersionUID = 2959248897018370078L;
    private char unsupportedRight = INVALID_RIGHT;

    public UnsupportedRightException() {
        super();
    }

    public UnsupportedRightException(char right) {
        super("Unsupported right flag '"+ right +"'.");
        this.unsupportedRight  = right;
    }
    
    public UnsupportedRightException(MailboxACLRight unsupportedRight) {
        this(unsupportedRight.asCharacter());
    }

    public UnsupportedRightException(String msg, Exception cause) {
        super(msg, cause);
    }

    public char getUnsupportedRight() {
        return unsupportedRight;
    }

}
