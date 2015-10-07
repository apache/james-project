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

public class HeaderDisplayComparator extends AbstractHeaderComparator{


    private final static Comparator<Message<?>> FROM_COMPARATOR = new HeaderDisplayComparator(FROM);
    private final static Comparator<Message<?>> REVERSE_FROM_COMPARATOR = new ReverseComparator(FROM_COMPARATOR);


    private final static Comparator<Message<?>> TO_COMPARATOR = new HeaderDisplayComparator(TO);
    private final static Comparator<Message<?>> REVERSE_TO_COMPARATOR = new ReverseComparator(TO_COMPARATOR);

    
    private String headerName;

    public HeaderDisplayComparator(String headerName) {
        this.headerName = headerName;
    }
    
    @Override
    public int compare(Message<?> o1, Message<?> o2) {
        String display1 = SearchUtil.getDisplayAddress(getHeaderValue(headerName, o1));
        String display2 = SearchUtil.getDisplayAddress(getHeaderValue(headerName, o2));
        return display1.compareToIgnoreCase(display2);
    }

    
    public static Comparator<Message<?>> from(boolean reverse) {
        if (reverse) {
            return REVERSE_FROM_COMPARATOR;
        } else {
            return FROM_COMPARATOR;
        }
    }

    
    public static Comparator<Message<?>> to(boolean reverse) {
        if (reverse) {
            return REVERSE_TO_COMPARATOR;
        } else {
            return TO_COMPARATOR;
        }
    }
}
