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

package org.apache.james.transport.mailets.utils;

import com.google.common.base.Optional;
import com.google.common.base.Strings;

public class CharsetFromSubjectMailHeader {


    /**
     * It attempts to determine the charset used to encode an "unstructured" RFC
     * 822 header (like Subject). The encoding is specified in RFC 2047. If it
     * cannot determine or the the text is not encoded then it returns null.
     * <p/>
     * Here is an example raw text: Subject:
     * =?iso-8859-2?Q?leg=FAjabb_pr=F3ba_l=F5elemmel?=
     *
     * @param subject the raw (not decoded) value of the header. Null means that the
     *                header was not present (in this case it always return null).
     * @return the MIME charset name or null if no encoding applied
     */
    public Optional<String> parse(String subject) {
        if (Strings.isNullOrEmpty(subject)) {
            return Optional.absent();
        }
        int iEncodingPrefix = subject.indexOf("=?");
        if (iEncodingPrefix == -1) {
            return Optional.absent();
        }
        int iCharsetBegin = iEncodingPrefix + 2;
        int iSecondQuestionMark = subject.indexOf('?', iCharsetBegin);
        if (iSecondQuestionMark == -1) {
            return Optional.absent();
        }
        if (iSecondQuestionMark == iCharsetBegin) {
            return Optional.absent();
        }
        int iThirdQuestionMark = subject.indexOf('?', iSecondQuestionMark + 1);
        if (iThirdQuestionMark == -1) {
            return Optional.absent();
        }
        if (subject.indexOf("?=", iThirdQuestionMark + 1) == -1) {
            return Optional.absent();
        }
        return Optional.of(subject.substring(iCharsetBegin, iSecondQuestionMark));
    }
}
