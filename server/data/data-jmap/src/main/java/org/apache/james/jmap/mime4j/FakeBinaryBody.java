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

package org.apache.james.jmap.mime4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.mime4j.dom.BinaryBody;
import org.apache.james.mime4j.dom.SingleBody;

public class FakeBinaryBody extends BinaryBody {
    private final long size;

    FakeBinaryBody(long size) {
        this.size = size;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        throw new NotImplementedException();
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        throw new NotImplementedException();
    }

    @Override
    public long size() {
        return size;
    }

    public long getSize() {
        return size;
    }

    @Override
    public void dispose() {
    }

    @Override
    public SingleBody copy() {
        return new FakeBinaryBody(size);
    }
}
