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
package org.apache.james.imap.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.james.imap.api.message.IdRange;
import org.junit.jupiter.api.Test;

class IdRangeTest {
    @Test
    void testNotMerge() {
        IdRange r = new IdRange(0, 2);
        IdRange r2 = new IdRange(4, 5);
        IdRange r3 = new IdRange(7, 10);
        
        List<IdRange> ranges = new ArrayList<>();
        ranges.add(r);
        ranges.add(r2);
        ranges.add(r3);
        
        List<IdRange> merged = IdRange.mergeRanges(ranges);
        assertThat(merged.size()).isEqualTo(3);
        Iterator<IdRange> rIt = merged.iterator();
        checkIdRange(r,rIt.next());
        checkIdRange(r2,rIt.next());
        checkIdRange(r3,rIt.next());
        assertThat(rIt.hasNext()).isFalse();
    }


    @Test
    void testMerge() {
        IdRange r = new IdRange(0, 2);
        IdRange r2 = new IdRange(1, 4);
        IdRange r3 = new IdRange(6, 7);
        
        List<IdRange> ranges = new ArrayList<>();
        ranges.add(r);
        ranges.add(r2);
        ranges.add(r3);
        
        List<IdRange> merged = IdRange.mergeRanges(ranges);
        assertThat(merged.size()).isEqualTo(2);
        Iterator<IdRange> rIt = merged.iterator();
        checkIdRange(new IdRange(0, 4),rIt.next());
        checkIdRange(r3,rIt.next());
        assertThat(rIt.hasNext()).isFalse();
    }
    

    @Test
    void testMerge2() {
        IdRange r = new IdRange(0, 10);
        IdRange r2 = new IdRange(1, 4);
        IdRange r3 = new IdRange(5, 7);
        
        List<IdRange> ranges = new ArrayList<>();
        ranges.add(r);
        ranges.add(r2);
        ranges.add(r3);
        
        List<IdRange> merged = IdRange.mergeRanges(ranges);
        assertThat(merged.size()).isEqualTo(1);
        Iterator<IdRange> rIt = merged.iterator();
        checkIdRange(new IdRange(0, 10),rIt.next());
        assertThat(rIt.hasNext()).isFalse();
    }
    
    @Test
    void testMerge3() {
        IdRange r = new IdRange(0, 10);
        IdRange r2 = new IdRange(1, 4);
        IdRange r3 = new IdRange(10, 15);
        
        List<IdRange> ranges = new ArrayList<>();
        ranges.add(r);
        ranges.add(r2);
        ranges.add(r3);
        
        List<IdRange> merged = IdRange.mergeRanges(ranges);
        assertThat(merged.size()).isEqualTo(1);
        Iterator<IdRange> rIt = merged.iterator();
        checkIdRange(new IdRange(0, 15),rIt.next());
        assertThat(rIt.hasNext()).isFalse();
    }
    
    @Test
    void testMerge4() {
        IdRange r = new IdRange(0, 1);
        IdRange r2 = new IdRange(1, 1);
        IdRange r3 = new IdRange(2, 2);
        
        List<IdRange> ranges = new ArrayList<>();
        ranges.add(r);
        ranges.add(r2);
        ranges.add(r3);
        
        List<IdRange> merged = IdRange.mergeRanges(ranges);
        assertThat(merged.size()).isEqualTo(1);
        Iterator<IdRange> rIt = merged.iterator();
        checkIdRange(new IdRange(0, 2),rIt.next());
        assertThat(rIt.hasNext()).isFalse();
    }
    
    private void checkIdRange(IdRange r1, IdRange r2) {
        assertThat(r2.getLowVal()).isEqualTo(r1.getLowVal());
        assertThat(r2.getHighVal()).isEqualTo(r1.getHighVal());
    }
}
