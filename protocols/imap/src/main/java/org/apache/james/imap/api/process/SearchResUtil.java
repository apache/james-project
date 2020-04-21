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

package org.apache.james.imap.api.process;

import java.util.Arrays;

import org.apache.james.imap.api.message.IdRange;

/**
 * Utility class which is used to support the SEARCHRES extension
 */
public class SearchResUtil {
    
    private static final String SEARCHRES_SAVED_SET = "SEARCHRES_SAVED_SET";
    
    /**
     * Return the saved sequence-set which you can refer to with $. This method will
     * return a IdRange[0] If no sequence-set is saved
     */
    public static IdRange[] getSavedSequenceSet(ImapSession session) {
        Object obj = session.getAttribute(SEARCHRES_SAVED_SET);
        if (obj != null) {
            return (IdRange[]) obj;
        } else {
            return new IdRange[0];
        }
    }
    
    /**
     * Save the given sequence-set which you can refer to later with $.
     */
    public static void saveSequenceSet(ImapSession session, IdRange[] ranges) {
        session.setAttribute(SEARCHRES_SAVED_SET, IdRange.mergeRanges(Arrays.asList(ranges)).toArray(IdRange[]::new));
    }
    
    /**
     * Reset the saved sequence-set
     */
    public static void resetSavedSequenceSet(ImapSession session) {
        session.setAttribute(SEARCHRES_SAVED_SET, null);
    }
}
