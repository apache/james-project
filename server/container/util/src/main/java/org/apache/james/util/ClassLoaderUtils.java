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

package org.apache.james.util;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import javax.mail.util.SharedByteArrayInputStream;

import org.apache.commons.io.IOUtils;

public class ClassLoaderUtils {
    public static String getSystemResourceAsString(String filename, Charset charset) {
        try {
            return IOUtils.toString(ClassLoader.getSystemResourceAsStream(filename), charset);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getSystemResourceAsString(String filename) {
        return getSystemResourceAsString(filename, StandardCharsets.US_ASCII);
    }

    public static byte[] getSystemResourceAsByteArray(String filename) {
        try {
            return IOUtils.toByteArray(ClassLoader.getSystemResourceAsStream(filename));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static SharedByteArrayInputStream getSystemResourceAsSharedStream(String filename) {
        return new SharedByteArrayInputStream(getSystemResourceAsByteArray(filename));
    }
}
