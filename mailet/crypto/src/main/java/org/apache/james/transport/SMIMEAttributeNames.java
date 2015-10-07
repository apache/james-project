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



package org.apache.james.transport;

/**
 * Contains some SMIME related mail attribute names of general use.
 *
 * @version CVS $Revision$ $Date$
 * @since 2.2.1
 */
public interface SMIMEAttributeNames {
    
    /**
     * The attribute contains the server-side signing mailet name as a String.
     */
    public static final String SMIME_SIGNING_MAILET = "org.apache.james.smime.signing.mailetname";
    
    /**
     * The attribute contains the string "valid" or the reason of non-validity of the signature.
     * The status could be non valid either because the signature does not verify
     * or because the certificate could be not valid when the signature was done.
     */    
    public static final String SMIME_SIGNATURE_VALIDITY = "org.apache.james.smime.signature.validity";
    
    /**
     * The attribute contains the signer's mail address as a String.
     */
    public static final String SMIME_SIGNER_ADDRESS = "org.apache.james.smime.signer.address";
    
    /**
     * The attribute contains the signature timestamp as a Date object.
     */
    public static final String SMIME_SIGNING_TIME = "org.apache.james.smime.signing.time";
    
}
