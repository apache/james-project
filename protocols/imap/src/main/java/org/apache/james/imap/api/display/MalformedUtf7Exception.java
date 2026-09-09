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
package org.apache.james.imap.api.display;

/**
 * Thrown when a value can not be decoded as modified UTF-7, as defined by RFC3501.
 *
 * This denotes a client side protocol error: such a value needs to be rejected rather than
 * to be treated as an internal error.
 */
public class MalformedUtf7Exception extends RuntimeException {
    public MalformedUtf7Exception(String input, Throwable cause) {
        super("'" + input + "' is not a valid modified UTF-7 value", cause);
    }
}
