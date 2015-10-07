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
package org.apache.james.protocols.pop3.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import junit.framework.TestCase;


public abstract class AbstractInputStreamTest extends TestCase{

    protected void checkRead(InputStream in, String expected) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int i = -1;
        while ((i = in.read()) != -1) {
            out.write(i);
        }
        in.close();
        out.close();        
        assertEquals(expected, new String(out.toByteArray()));

        
        
    }
    
    protected void checkReadViaArray(InputStream in, String expected) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        byte[] buf = new byte[3];
        int i = 0;
        while ((i = in.read(buf)) != -1) {
            out.write(buf, 0, i);
        }
        
       
        in.close();
        out.close();
        assertEquals(expected, new String(out.toByteArray()));
        
        
    }
}
