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

package org.apache.james.imap.decode;

import java.util.ArrayList;
import java.util.List;

import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.SectionType;

public class FetchPartPathDecoder {
    /**
     * Going to need to make one array copy so might as well ensure plenty of
     * space
     */
    private static final int ARRAY_INCREMENT = 20;

    /** Embedded RFC882 messages are rare so start size one array */
    private static final int ARRAY_INITIAL_SIZE = 1;

    private SectionType sectionType;

    private int[] path;

    private int partial;

    private int used;

    private List<String> names;

    public FetchPartPathDecoder() {
    }

    public SectionType decode(CharSequence sectionSpecification) throws DecodingException {
        init();
        sectionType = decode(0, sectionSpecification);
        prunePath();
        return sectionType;
    }

    private void prunePath() {
        if (path != null) {
            int length = path.length;
            if (used < length) {
                int[] newPath = new int[used];
                System.arraycopy(path, 0, newPath, 0, used);
                path = newPath;
            }
        }
    }

    private SectionType decode(int at, CharSequence sectionSpecification) throws DecodingException {
        SectionType result;
        int length = sectionSpecification.length();
        if (at < length) {
            final char next = sectionSpecification.charAt(at);
            switch (next) {
            case '.':
                separator();
                result = decode(at + 1, sectionSpecification);
                break;

            case '0':
                result = digit(at, sectionSpecification, 0);
                break;

            case '1':
                result = digit(at, sectionSpecification, 1);
                break;

            case '2':
                result = digit(at, sectionSpecification, 2);
                break;

            case '3':
                result = digit(at, sectionSpecification, 3);
                break;

            case '4':
                result = digit(at, sectionSpecification, 4);
                break;

            case '5':
                result = digit(at, sectionSpecification, 5);
                break;

            case '6':
                result = digit(at, sectionSpecification, 6);
                break;

            case '7':
                result = digit(at, sectionSpecification, 7);
                break;

            case '8':
                result = digit(at, sectionSpecification, 8);
                break;

            case '9':
                result = digit(at, sectionSpecification, 9);
                break;

            case 't':
            case 'T':
                result = text(at, sectionSpecification);
                break;

            case 'h':
            case 'H':
                result = header(at, sectionSpecification);
                break;

            case 'm':
            case 'M':
                result = mime(at, sectionSpecification);
                break;

            default:
                throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Did not expect '" + next + "' here in body specification.");
            }
        } else {
            storePartial();
            result = SectionType.CONTENT;
        }
        return result;
    }

    private SectionType mime(int at, CharSequence sectionSpecification) throws DecodingException {
        if (sectionSpecification.length() == at + 4) {
            mustBeI(sectionSpecification, at + 1);
            mustBeM(sectionSpecification, at + 2);
            mustBeE(sectionSpecification, at + 3);
            storePartial();
        } else {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown body specification");
        }
        return SectionType.MIME;
    }

    private void mustBeI(CharSequence sectionSpecification, int position) throws DecodingException {
        char i = sectionSpecification.charAt(position);
        if (!(i == 'i' || i == 'I')) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown body specification");
        }
    }

    private void mustBeM(CharSequence sectionSpecification, int position) throws DecodingException {
        char next = sectionSpecification.charAt(position);
        if (!(next == 'm' || next == 'M')) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown body specification");
        }
    }

    private void mustBeN(CharSequence sectionSpecification, int position) throws DecodingException {
        char next = sectionSpecification.charAt(position);
        if (!(next == 'n' || next == 'N')) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown body specification");
        }
    }

    private void mustBeO(CharSequence sectionSpecification, int position) throws DecodingException {
        char next = sectionSpecification.charAt(position);
        if (!(next == 'o' || next == 'O')) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown body specification");
        }
    }

    private void mustBeE(CharSequence sectionSpecification, int position) throws DecodingException {
        char next = sectionSpecification.charAt(position);
        if (!(next == 'e' || next == 'E')) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown body specification");
        }
    }

    private void mustBeA(CharSequence sectionSpecification, int position) throws DecodingException {
        char next = sectionSpecification.charAt(position);
        if (!(next == 'a' || next == 'A')) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown body specification");
        }
    }

    private void mustBeD(CharSequence sectionSpecification, int position) throws DecodingException {
        char next = sectionSpecification.charAt(position);
        if (!(next == 'd' || next == 'D')) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown body specification");
        }
    }

    private void mustBeR(CharSequence sectionSpecification, int position) throws DecodingException {
        char next = sectionSpecification.charAt(position);
        if (!(next == 'r' || next == 'R')) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown body specification");
        }
    }

    private void mustBeX(CharSequence sectionSpecification, int position) throws DecodingException {
        char next = sectionSpecification.charAt(position);
        if (!(next == 'x' || next == 'X')) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown body specification");
        }
    }

    private void mustBeT(CharSequence sectionSpecification, int position) throws DecodingException {
        char next = sectionSpecification.charAt(position);
        if (!(next == 't' || next == 'T')) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown body specification");
        }
    }

    private void mustBeF(CharSequence sectionSpecification, int position) throws DecodingException {
        char next = sectionSpecification.charAt(position);
        if (!(next == 'f' || next == 'F')) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown body specification");
        }
    }

    private void mustBeL(CharSequence sectionSpecification, int position) throws DecodingException {
        char next = sectionSpecification.charAt(position);
        if (!(next == 'l' || next == 'L')) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown body specification");
        }
    }

    private void mustBeS(CharSequence sectionSpecification, int position) throws DecodingException {
        char next = sectionSpecification.charAt(position);
        if (!(next == 's' || next == 'S')) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown body specification");
        }
    }

    private void mustBeDot(CharSequence sectionSpecification, int position) throws DecodingException {
        char next = sectionSpecification.charAt(position);
        if (!(next == '.')) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown body specification");
        }
    }

    private void mustBeOpenParen(CharSequence sectionSpecification, int position) throws DecodingException {
        char next = sectionSpecification.charAt(position);
        if (!(next == '(')) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown body specification");
        }
    }

    private SectionType header(int at, CharSequence sectionSpecification) throws DecodingException {
        SectionType result;
        int length = sectionSpecification.length();
        if (length > at + 5) {
            mustBeE(sectionSpecification, at + 1);
            mustBeA(sectionSpecification, at + 2);
            mustBeD(sectionSpecification, at + 3);
            mustBeE(sectionSpecification, at + 4);
            mustBeR(sectionSpecification, at + 5);
            storePartial();
            if (length == at + 6) {
                result = SectionType.HEADER;
            } else {
                result = headerFields(at + 6, sectionSpecification);
            }
        } else {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown body specification");
        }
        return result;
    }

    private SectionType headerFields(int at, CharSequence sectionSpecification) throws DecodingException {
        SectionType result;
        int length = sectionSpecification.length();
        if (length > at + 7) {
            mustBeDot(sectionSpecification, at);
            mustBeF(sectionSpecification, at + 1);
            mustBeI(sectionSpecification, at + 2);
            mustBeE(sectionSpecification, at + 3);
            mustBeL(sectionSpecification, at + 4);
            mustBeD(sectionSpecification, at + 5);
            mustBeS(sectionSpecification, at + 6);
            char next = sectionSpecification.charAt(at + 7);
            int namesStartAt;
            switch (next) {
            case ' ':
                result = SectionType.HEADER_FIELDS;
                namesStartAt = skipSpaces(at + 7, sectionSpecification);
                break;
            case '.':
                if (length > at + 10) {
                    mustBeN(sectionSpecification, at + 8);
                    mustBeO(sectionSpecification, at + 9);
                    mustBeT(sectionSpecification, at + 10);
                    result = SectionType.HEADER_NOT_FIELDS;
                    namesStartAt = skipSpaces(at + 11, sectionSpecification);
                } else {
                    throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown body specification");
                }
                break;
            default:
                throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown body specification");
            }
            mustBeOpenParen(sectionSpecification, namesStartAt);
            readHeaderNames(namesStartAt + 1, sectionSpecification);

        } else {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown body specification");
        }
        return result;
    }

    private void readHeaderNames(int at, CharSequence sectionSpecification) throws DecodingException {
        names = new ArrayList<>();
        int firstWordStart = skipSpaces(at, sectionSpecification);
        readHeaderNames(firstWordStart, firstWordStart, sectionSpecification);
    }

    private void readHeaderNames(int at, int lastWordStart, CharSequence sectionSpecification) throws DecodingException {
        if (at < sectionSpecification.length()) {
            final char next = sectionSpecification.charAt(at);
            switch (next) {
            case ' ':
                readName(lastWordStart, at, sectionSpecification);
                int nextWord = skipSpaces(at, sectionSpecification);
                readHeaderNames(nextWord, nextWord, sectionSpecification);
                break;
            case ')':
                readName(lastWordStart, at, sectionSpecification);
                break;
            default:
                readHeaderNames(at + 1, lastWordStart, sectionSpecification);
            }
        } else {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Closing parenthesis missing.");
        }
    }

    private void readName(int wordStart, int wordFinish, CharSequence sectionSpecification) {
        if (wordStart <= wordFinish) {
            CharSequence word = sectionSpecification.subSequence(wordStart, wordFinish);
            String name = word.toString();
            names.add(name);
        }
    }

    private int skipSpaces(int at, CharSequence sectionSpecification) {
        int result;
        if (at < sectionSpecification.length()) {
            char next = sectionSpecification.charAt(at);
            if (next == ' ') {
                result = skipSpaces(at + 1, sectionSpecification);
            } else {
                result = at;
            }
        } else {
            result = at;
        }
        return result;
    }

    private SectionType text(int at, CharSequence sectionSpecification) throws DecodingException {
        if (sectionSpecification.length() == at + 4) {
            mustBeE(sectionSpecification, at + 1);
            mustBeX(sectionSpecification, at + 2);
            mustBeT(sectionSpecification, at + 3);
            storePartial();
        } else {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown body specification");
        }
        return SectionType.TEXT;
    }

    private SectionType digit(int at, CharSequence sectionSpecification, int digit) throws DecodingException {
        digit(digit);
        return decode(at + 1, sectionSpecification);
    }

    private void init() {
        sectionType = SectionType.CONTENT;
        resetPartial();
        path = null;
        used = 0;
        names = null;
    }

    private void resetPartial() {
        partial = 0;
    }

    private void separator() {
        storePartial();
    }

    private void storePartial() {
        if (partial > 0) {
            ensureSpaceForOneInPath();
            path[used++] = partial;
            resetPartial();
        }
    }

    private void ensureSpaceForOneInPath() {
        if (path == null) {
            path = new int[ARRAY_INITIAL_SIZE];
        } else {
            int length = path.length;
            if (used >= length) {
                int[] newPath = new int[length + ARRAY_INCREMENT];
                System.arraycopy(path, 0, newPath, 0, length);
                path = newPath;
            }
        }
    }

    private void digit(int digit) {
        partial = (partial * 10) + digit;
    }

    /**
     * Gets the decoded path.
     * 
     * @return the path
     */
    public final int[] getPath() {
        return path;
    }

    /**
     * Gets the{@link SectionType}
     */
    public final SectionType getSpecifier() {
        return sectionType;
    }

    /**
     * Gets field names.
     * 
     * @return <code>List</code> of <code>String</code> names when
     *         {@link SectionType#HEADER_FIELDS} or {@link SectionType#HEADER_NOT_FIELDS}, null
     *         otherwise
     */
    public final List<String> getNames() {
        return names;
    }

}
