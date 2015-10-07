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
package org.apache.james.mailbox.store.search;

import static org.junit.Assert.*;

import org.junit.Test;

public class SearchUtilTest {

    @Test
    public void testSimpleSubject() {
        String subject ="This is my subject";
        assertEquals(subject, SearchUtil.getBaseSubject(subject));
    }
    
    @Test
    public void testReplaceSpacesAndTabsInSubject() {
        String subject ="This   is my\tsubject";
        assertEquals("This is my subject", SearchUtil.getBaseSubject(subject));
    }
    
    @Test
    public void testRemoveTrailingSpace() {
        String subject ="This is my subject ";
        assertEquals("This is my subject", SearchUtil.getBaseSubject(subject));
    }
    
    
    @Test
    public void testRemoveTrailingFwd() {
        String subject ="This is my subject (fwd)";
        assertEquals("This is my subject", SearchUtil.getBaseSubject(subject));
    }
    

    @Test
    public void testSimpleExtraction() {
        String expectedSubject = "Test";
        assertEquals(expectedSubject, SearchUtil.getBaseSubject("Re: Test"));
        assertEquals(expectedSubject, SearchUtil.getBaseSubject("re: Test"));
        assertEquals(expectedSubject, SearchUtil.getBaseSubject("Fwd: Test"));
        assertEquals(expectedSubject, SearchUtil.getBaseSubject("fwd: Test"));
        assertEquals(expectedSubject, SearchUtil.getBaseSubject("Fwd: Re: Test"));
        assertEquals(expectedSubject, SearchUtil.getBaseSubject("Fwd: Re: Test (fwd)"));
    }
  
    @Test
    public void testComplexExtraction() {
        assertEquals("Test", SearchUtil.getBaseSubject("Re: re:re: fwd:[fwd: \t  Test]  (fwd)  (fwd)(fwd) "));
    }
}
