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
package org.apache.james.mailbox.store.search.comparator;

import java.util.Comparator;

import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.james.mailbox.store.search.SearchUtil;

public class BaseSubjectComparator extends AbstractHeaderComparator{



    private final static Comparator<Message<?>> BASESUBJECT = new BaseSubjectComparator();;
    private final static Comparator<Message<?>> REVERSE_BASESUBJECT = new ReverseComparator(BASESUBJECT);

    
    
    private final static String SUBJECT = "subject";
    
    @Override
    public int compare(Message<?> o1, Message<?> o2) {
        String baseSubject1 = SearchUtil.getBaseSubject(getHeaderValue(SUBJECT, o1));
        String baseSubject2 = SearchUtil.getBaseSubject(getHeaderValue(SUBJECT, o2));

        return baseSubject1.compareToIgnoreCase(baseSubject2);
    }


    public static Comparator<Message<?>> baseSubject(boolean reverse){
        if (reverse) {
            return REVERSE_BASESUBJECT;
        } else {
            return BASESUBJECT;
        }
    }
}
