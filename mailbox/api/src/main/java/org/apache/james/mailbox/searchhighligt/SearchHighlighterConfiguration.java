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

package org.apache.james.mailbox.searchhighligt;

import com.google.common.base.Preconditions;

public record SearchHighlighterConfiguration(String preTagFormatter,
                                             String postTagFormatter,
                                             int fragmentSize) {

    public static final String PRE_TAG_FORMATTER_DEFAULT = "<mark>";
    public static final String POST_TAG_FORMATTER_DEFAULT = "</mark>";
    public static final int FRAGMENT_SIZE_DEFAULT = 100;

    public static final SearchHighlighterConfiguration DEFAULT = new SearchHighlighterConfiguration(PRE_TAG_FORMATTER_DEFAULT, POST_TAG_FORMATTER_DEFAULT, FRAGMENT_SIZE_DEFAULT);

    public SearchHighlighterConfiguration(String preTagFormatter, String postTagFormatter, int fragmentSize) {
        Preconditions.checkArgument(fragmentSize > 0, "fragmentSize should be positive");
        Preconditions.checkArgument(fragmentSize <= 255, "fragmentSize should be less than 256 (rfc8621#section-5)");
        this.preTagFormatter = preTagFormatter;
        this.postTagFormatter = postTagFormatter;
        this.fragmentSize = fragmentSize;
    }
}
