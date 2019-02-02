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
package org.apache.james.mailbox.tools.jpa.migrator.exception;

/**
 * Exception to be thrown when a problem occurs in the migration process.
 *
 */
public class JpaMigrateException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public JpaMigrateException() {
    }

    public JpaMigrateException(String message) {
        super(message);
    }

    public JpaMigrateException(Throwable cause) {
        super(cause);
    }

    public JpaMigrateException(String message, Throwable cause) {
        super(message, cause);
    }

}
