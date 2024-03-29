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



package org.apache.james.transport.matchers;

import java.io.IOException;
import java.util.regex.PatternSyntaxException;

import jakarta.mail.MessagingException;

import org.apache.mailet.Experimental;

/**
 * Initializes RegexMatcher with regular expressions from a file.
 *
 */
@Experimental
public class FileRegexMatcher extends GenericRegexMatcher {
    
    @Override
    public void init() throws MessagingException {
        try (java.io.RandomAccessFile patternSource = new java.io.RandomAccessFile(getCondition(), "r")) {
            int lines = 0;
            while (patternSource.readLine() != null) {
                lines++;
            }
            patterns = new Object[lines][2];
            patternSource.seek(0);
            for (int i = 0; i < lines; i++) {
                String line = patternSource.readLine();
                patterns[i][0] = line.substring(0, line.indexOf(':'));
                patterns[i][1] = line.substring(line.indexOf(':') + 1);
            }
            compile(patterns);

        } catch (java.io.FileNotFoundException fnfe) {
            throw new MessagingException("Could not locate patterns.", fnfe);
        } catch (IOException ioe) {
            throw new MessagingException("Could not read patterns.", ioe);
        } catch (PatternSyntaxException mp) {
            throw new MessagingException("Could not initialize regex patterns", mp);
        }
        // close the file
        // just ignore on close
    }
}
