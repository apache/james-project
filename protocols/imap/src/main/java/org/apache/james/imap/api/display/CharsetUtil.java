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
 * Utility class which can be used to get a list of supported {@link Charset}'s 
 * 
 * Beside this it has some methods included which helps to encode/decode modified UTF7
 */
public class CharsetUtil {

    private static final String X_MODIFIED_UTF_7 = "X-MODIFIED-UTF-7";
    private static final Charset X_MODIFIED_UTF_7_CHARSET = new CharsetProvider().charsetForName(X_MODIFIED_UTF_7);

    /**
     * Decode the given UTF7 encoded <code>String</code>
     *
     * @return decoded
     */
    public static String decodeModifiedUTF7(String string) {
        return X_MODIFIED_UTF_7_CHARSET.decode(ByteBuffer.wrap(string.getBytes())).toString();

    }
    

    /**
     * Encode the given <code>String</code> to modified UTF7. 
     * See RFC3501 for more details
     *
     * @return encoded
     */
    
    public static String encodeModifiedUTF7(String string) {
        ByteBuffer encode = X_MODIFIED_UTF_7_CHARSET.encode(string);
        return new String(encode.array(), 0, encode.remaining());

    }
}
