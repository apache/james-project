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

package org.apache.james.protocols.api;

import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * {@link SequenceInputStream} sub-class which allows direct access to the pair of {@link InputStream}'s.
 * 
 * When ever you need to "combine" two {@link InputStream}'s you should use this class as it may allow the Transport to optimize the transfer of it!
 * 
 *
 */
public class CombinedInputStream extends SequenceInputStream implements Iterable<InputStream>{

    private final InputStream[] streams;

    public CombinedInputStream(InputStream s1, InputStream s2) {
        super(s1, s2);
        streams = new InputStream[] {s1, s2};
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Iterable#iterator()
     */
    public Iterator<InputStream> iterator() {
        return new Iterator<InputStream>() {
            private int count = 0;
           
            /*
             * (non-Javadoc)
             * @see java.util.Iterator#hasNext()
             */
            public boolean hasNext() {
                return count < streams.length;
            }

            /*
             * (non-Javadoc)
             * @see java.util.Iterator#next()
             */
            public InputStream next() {
                if (hasNext())  {
                    return streams[count++];
                } else {
                    throw new NoSuchElementException();
                }
            }
            
            /**
             * Read-Only
             */
            public void remove() {
                throw new UnsupportedOperationException("Read-Only");
            }
        };
    }

}
