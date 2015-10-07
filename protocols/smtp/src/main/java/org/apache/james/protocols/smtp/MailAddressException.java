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


package org.apache.james.protocols.smtp;

public class MailAddressException extends Exception{

    /**
     * 
     */
    private static final long serialVersionUID = -2845113080526615146L;
    private final String address;
    private final int pos;

   

    public MailAddressException(String message, String address, int pos) {
        super(message);
        this.address = address;
        this.pos = pos;
    }
    
    public String getAddress() {
        return address;
    }
    
    public int getPos() {
        return pos;
    }


}
