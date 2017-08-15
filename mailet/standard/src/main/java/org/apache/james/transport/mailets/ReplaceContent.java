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

package org.apache.james.transport.mailets;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

import org.apache.mailet.Mail;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.GenericMailet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

/**
 * Replace text contents
 * <p>This mailet allow to specific regular expression to replace text in subject and content.
 * 
 * <p>
 * Each expression is defined as:
 * <code>/REGEX_PATTERN/SUBSTITUTION_PATTERN/FLAGS/</code>
 * </p>
 * 
 * <p>
 * <code>REGEX_PATTERN</code> is a regex used for the match<br>
 * <code>SUBSTITUTION_PATTERN</code> is a substitution pattern<br>
 * <code>FLAGS</code> flags supported for the pattern:<br>
 *   i: case insensitive<br>
 *   m: multi line<br>
 *   x: extended (N/A)<br>
 *   r: repeat - keep matching until a substitution is possible<br>
 * </p>
 * 
 * <p>To identify subject and body pattern we use the tags &lt;subjectPattern&gt; and &lt;bodyPattern&gt;</p>
 *
 * <p>
 * Rules can be specified in external files.
 * Lines must be CRLF terminated and lines starting with # are considered commments.
 * Tags used to include external files are &lt;subjectPatternFile&gt; and 
 * &lt;bodyPatternFile&gt;
 * If file path starts with # then the file is loaded as a reasource.
 * </p>
 * 
 * <p>
 * Use of both files and direct patterns at the same time is allowed.
 * </p>
 * 
 * <p>
 * This mailet allow also to enforce the resulting charset for messages processed, when a replacement has been done.
 * To do that the tag &lt;charset&gt; must be specified.
 * </p>
 * 
 * <p>
 * NOTE:
 * Regexp rules must be escaped by regexp excaping rules and applying this 2 additional rules:<br>
 * - "/" char inside an expression must be prefixed with "\":
 *   e.g: "/\//-//" replaces "/" with "-"<br>
 * - when the rules are specified using &lt;subjectPattern&gt; or &lt;bodyPattern&gt; and
 *   "/,/" has to be used in a pattern string it must be prefixed with a "\".
 *   E.g: "/\/\/,//" replaces "/" with "," (the rule would be "/\//,//" but the "/,/" must
 *   be escaped.<br>
 * </p>
 */
public class ReplaceContent extends GenericMailet {

    private static final String PARAMETER_NAME_SUBJECT_PATTERN = "subjectPattern";
    private static final String PARAMETER_NAME_BODY_PATTERN = "bodyPattern";
    private static final String PARAMETER_NAME_SUBJECT_PATTERNFILE = "subjectPatternFile";
    private static final String PARAMETER_NAME_BODY_PATTERNFILE = "bodyPatternFile";
    private static final String PARAMETER_NAME_CHARSET = "charset";

    private Optional<Charset> charset;
    private boolean debug;
    @VisibleForTesting ReplaceConfig replaceConfig;

    @Override
    public String getMailetInfo() {
        return "ReplaceContent";
    }

    @Override
    public void init() throws MailetException {
        charset = initCharset();
        debug = isDebug();
        replaceConfig = initPatterns();
    }

    private Optional<Charset> initCharset() {
        String charsetName = getInitParameter(PARAMETER_NAME_CHARSET);
        if (Strings.isNullOrEmpty(charsetName)) {
            return Optional.absent();
        }
        return Optional.of(Charset.forName(charsetName));
    }

    private boolean isDebug() {
        return Integer.valueOf(getInitParameter("debug", "0")) == 1;
    }

    private ReplaceConfig initPatterns() throws MailetException {
        try {
            return ReplaceConfig.builder()
                    .addAllSubjectReplacingUnits(subjectPattern())
                    .addAllBodyReplacingUnits(bodyPattern())
                    .addAllSubjectReplacingUnits(subjectPatternFile())
                    .addAllBodyReplacingUnits(bodyPatternFile())
                    .build();
        } catch (MailetException | IOException e) {
            throw new MailetException("Failed initialization", e);
        }
    }

    private List<ReplacingPattern> subjectPattern() throws MailetException {
        String pattern = getInitParameter(PARAMETER_NAME_SUBJECT_PATTERN);
        if (pattern != null) {
            return new PatternExtractor().getPatternsFromString(pattern);
        }
        return ImmutableList.of();
    }

    private List<ReplacingPattern> bodyPattern() throws MailetException {
        String pattern = getInitParameter(PARAMETER_NAME_BODY_PATTERN);
        if (pattern != null) {
            return new PatternExtractor().getPatternsFromString(pattern);
        }
        return ImmutableList.of();
    }

    private List<ReplacingPattern> subjectPatternFile() throws MailetException, IOException {
        String filePattern = getInitParameter(PARAMETER_NAME_SUBJECT_PATTERNFILE);
        if (filePattern != null) {
            return new PatternExtractor().getPatternsFromFileList(filePattern);
        }
        return ImmutableList.of();
    }

    private List<ReplacingPattern> bodyPatternFile() throws MailetException, IOException {
        String filePattern = getInitParameter(PARAMETER_NAME_BODY_PATTERNFILE);
        if (filePattern != null) {
            return new PatternExtractor().getPatternsFromFileList(filePattern);
        }
        return ImmutableList.of();
    }

    @Override
    public void service(Mail mail) throws MailetException {
        new ContentReplacer(debug).replaceMailContentAndSubject(mail, replaceConfig, charset);
    }
}
