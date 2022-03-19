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


package org.apache.mailet;

import jakarta.mail.MessagingException;

/**
 * Defines a general exception a mailet can throw when it encounters difficulty.
 */
public class MailetException extends MessagingException {

    /**
     * @since Mailet API 2.5
     */
    private static final long serialVersionUID = -2753505469139276160L;

    /**
     * Constructs a new mailet exception.
     */
    public MailetException() {
        super();
    }

    /**
     * Constructs a new mailet exception with the specified message.
     *
     * @param message the exception message
     */
    public MailetException(String message) {
        super(message);
    }

    /**
     * Constructs a new mailet exception with the specified message
     * and an exception which is the "root cause" of the exception.
     *
     * @param message the exception message
     * @param e the root cause exception
     */
    public MailetException(String message, Exception e) {
        super(message, e);
    }

}
