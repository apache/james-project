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

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import com.beetstra.jutf7.CharsetProvider;

/**
 * This class has some methods included which helps to encode/decode modified UTF7
 */
public class ModifiedUtf7 {

    private static final Charset X_MODIFIED_UTF_7_CHARSET = new CharsetProvider().charsetForName("X-MODIFIED-UTF-7");

    /**
     * Decode the given UTF7 encoded <code>String</code>
     * 
     * @param input utf7-encoded value
     * @return decoded value
     */
    public static String decodeModifiedUTF7(String input) {
        return X_MODIFIED_UTF_7_CHARSET.decode(ByteBuffer.wrap(input.getBytes())).toString();
    }

    /**
     * Encode the given <code>String</code> to modified UTF7. 
     * See RFC3501 for more details
     * 
     * @param input
     * @return utf7-encoded value
     */
    public static String encodeModifiedUTF7(String input) {
        ByteBuffer encode = X_MODIFIED_UTF_7_CHARSET.encode(input);
        return new String(encode.array(), 0, encode.remaining());
    }
}
