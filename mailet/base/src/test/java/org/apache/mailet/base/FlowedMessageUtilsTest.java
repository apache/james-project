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

package org.apache.mailet.base;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class FlowedMessageUtilsTest {
    private static final boolean DEL_SP_NO = false;
    private static final boolean DEL_SP_YES = true;

    @Test
    void deflowWithSimpleText() {
        String input = "Text that should be \r\n" +
            "displayed on one line";

        String result = FlowedMessageUtils.deflow(input, DEL_SP_NO);

        assertThat(result).isEqualTo("Text that should be displayed on one line");
    }

    @Test
    void deflowWithOnlySomeLinesEndingInSpace() {
        String input = "Text that \r\n" +
            "should be \r\n" +
            "displayed on \r\n" +
            "one line.\r\n" +
            "Text that should retain\r\n" +
            "its line break.";

        String result = FlowedMessageUtils.deflow(input, DEL_SP_NO);

        assertThat(result).isEqualTo(
            "Text that should be displayed on one line.\r\n" +
                "Text that should retain\r\n" +
                "its line break.");
    }

    @Test
    void deflowWithNothingToDo() {
        String input = "Line one\r\nLine two\r\n";

        String result = FlowedMessageUtils.deflow(input, DEL_SP_NO);

        assertThat(result).isEqualTo(input);
    }

    @Test
    void deflowWithQuotedText() {
        String input = "On [date], [user] wrote:\r\n" +
            "> Text that should be displayed \r\n" +
            "> on one line\r\n" +
            "\r\n" +
            "Some more text that should be \r\n" +
            "displayed on one line.\r\n";

        String result = FlowedMessageUtils.deflow(input, DEL_SP_NO);

        assertThat(result).isEqualTo(
            "On [date], [user] wrote:\r\n" +
                "> Text that should be displayed on one line\r\n" +
                "\r\n" +
                "Some more text that should be displayed on one line.\r\n");
    }

    @Test
    void deflowWithQuotedTextEndingInSpace() {
        String input = "> Quoted text \r\n" +
            "Some other text";

        String result = FlowedMessageUtils.deflow(input, DEL_SP_NO);

        assertThat(result).isEqualTo("> Quoted text \r\nSome other text");
    }

    @Test
    void deflowWithQuotedTextEndingInSpaceBeforeQuotedTextOfDifferentQuoteDepth() {
        String input = ">> Depth 2 \r\n" +
            "> Depth 1 \r\n" +
            "> is here\r\n" +
            "Some other text";

        String result = FlowedMessageUtils.deflow(input, DEL_SP_NO);

        assertThat(result).isEqualTo(
            ">> Depth 2 \r\n" +
                "> Depth 1 is here\r\n" +
                "Some other text");
    }

    @Test
    void deflowWithQuotedTextEndingInSpaceFollowedByEmptyLine() {
        String input = "> Quoted \r\n" +
            "\r\n" +
            "Text";

        String result = FlowedMessageUtils.deflow(input, DEL_SP_NO);

        assertThat(result).isEqualTo(input);
    }

    @Test
    void deflowWithDelSp() {
        String input = "Text that is wrapped mid wo \r\nrd";

        String result = FlowedMessageUtils.deflow(input, DEL_SP_YES);

        assertThat(result).isEqualTo("Text that is wrapped mid word");
    }

    @Test
    void deflowWithQuotedTextAndSpaceStuffingAndDelSp() {
        String input = "> Quoted te \r\n" +
            "> xt";

        String result = FlowedMessageUtils.deflow(input, DEL_SP_YES);

        assertThat(result).isEqualTo("> Quoted text");
    }

    @Test
    void deflowWithSpaceStuffedSecondLine() {
        String input = "Text that should be \r\n" +
            " displayed on one line";

        String result = FlowedMessageUtils.deflow(input, DEL_SP_NO);

        assertThat(result).isEqualTo("Text that should be displayed on one line");
    }

    @Test
    void deflowWithOnlySpaceStuffing() {
        String input = "Line 1\r\n" +
            " Line 2\r\n" +
            " Line 3\r\n";

        String result = FlowedMessageUtils.deflow(input, DEL_SP_NO);

        assertThat(result).isEqualTo("Line 1\r\nLine 2\r\nLine 3\r\n");
    }

    @Test
    void deflowWithQuotedSpaceStuffedSecondLine() {
        String input = "> Text that should be \r\n" +
            "> displayed on one line";

        String result = FlowedMessageUtils.deflow(input, DEL_SP_NO);

        assertThat(result).isEqualTo("> Text that should be displayed on one line");
    }

    @Test
    void deflowWithTextContainingSignature() {
        String input = "Text that should be \r\n" +
            "displayed on one line.\r\n" +
            "\r\n" +
            "-- \r\n" +
            "Signature \r\n" +
            "text";

        String result = FlowedMessageUtils.deflow(input, DEL_SP_NO);

        assertThat(result).isEqualTo(
            "Text that should be displayed on one line.\r\n" +
                "\r\n" +
                "-- \r\n" +
                "Signature text");
    }

    @Test
    void deflowWithQuotedTextContainingSignature() {
        String input = "> Text that should be \r\n" +
            "> displayed on one line.\r\n" +
            "> \r\n" +
            "> -- \r\n" +
            "> Signature \r\n" +
            "> text";

        String result = FlowedMessageUtils.deflow(input, DEL_SP_NO);

        assertThat(result).isEqualTo(
            "> Text that should be displayed on one line.\r\n" +
                "> \r\n" +
                "> -- \r\n" +
                "> Signature text");
    }

    @Test
    void flowWithShortLine() {
        String input = "A single line not exceeding 78 characters.";

        String result = FlowedMessageUtils.flow(input, DEL_SP_NO);

        assertThat(result).isEqualTo(input);
    }

    @Test
    void flowWithLongLine() {
        String input = "A single line with quite a bit of text so the maximum width of 78 characters is exceeded.";

        String result = FlowedMessageUtils.flow(input, DEL_SP_NO);

        assertThat(result).isEqualTo(
            "A single line with quite a bit of text so the maximum width of 78 characters \r\n" +
                "is exceeded."
        );
    }

    @Test
    void flowWithShortQuotedLine() {
        String input = "> A single line not exceeding 78 characters.";

        String result = FlowedMessageUtils.flow(input, DEL_SP_NO);

        assertThat(result).isEqualTo(input);
    }

    @Test
    void flowWithLongQuotedLine() {
        String input = ">>> A single line with quite a bit of text so the maximum width of 78 characters is exceeded.";

        String result = FlowedMessageUtils.flow(input, DEL_SP_NO);

        assertThat(result).isEqualTo(
            ">>> A single line with quite a bit of text so the maximum width of 78 \r\n" +
                ">>> characters is exceeded."
        );
    }

    @Test
    void flowWithLineStartingWithFrom() {
        String input = "From me to you";

        String result = FlowedMessageUtils.flow(input, DEL_SP_NO);

        assertThat(result).isEqualTo(" From me to you");
    }

    @Test
    void flowWithLineStartingWithSpace() {
        String input = " Leading space";

        String result = FlowedMessageUtils.flow(input, DEL_SP_NO);

        assertThat(result).isEqualTo("  Leading space");
    }

    @Test
    void flowWithQuotedTextStartingWithQuoteCharacter() {
        String input = ">> >Quoted text\n";

        String result = FlowedMessageUtils.flow(input, DEL_SP_NO);

        assertThat(result).isEqualTo(">> >Quoted text\r\n");
    }

    @Test
    void flowWithSignature() {
        String input = "Text\r\n" +
            "\r\n" +
            "-- \r\n" +
            "Signature";

        String result = FlowedMessageUtils.flow(input, DEL_SP_NO);

        assertThat(result).isEqualTo(input);
    }

    @Test
    void flowWithQuotedSignature() {
        String input = "> Text\r\n" +
            ">\r\n" +
            "> -- \r\n" +
            "> Signature";

        String result = FlowedMessageUtils.flow(input, DEL_SP_NO);

        assertThat(result).isEqualTo(input);
    }

    @Test
    void flowWithComplexMultiLineText() {
        String input = "On [date], [user] wrote:\n" +
            ">A single line with quite a bit of text so the maximum width of 78 characters is exceeded.\n" +
            "\n" +
            "A rather short line.\n" +
            "Followed by another short line.\n" +
            "Followed by a line containing a lot more words. Enough words so it exceeds the maximum line length.\n" +
            "\n" +
            "-- \n" +
            "Signature text that is sufficiently long so it exceeds the maximum width of 78 characters.";

        String result = FlowedMessageUtils.flow(input, DEL_SP_NO);

        assertThat(result).isEqualTo(
            "On [date], [user] wrote:\r\n" +
                "> A single line with quite a bit of text so the maximum width of 78 \r\n" +
                "> characters is exceeded.\r\n" +
                "\r\n" +
                "A rather short line.\r\n" +
                "Followed by another short line.\r\n" +
                "Followed by a line containing a lot more words. Enough words so it exceeds \r\n" +
                "the maximum line length.\r\n" +
                "\r\n" +
                "-- \r\n" +
                "Signature text that is sufficiently long so it exceeds the maximum width of \r\n" +
                "78 characters."
        );
    }

    @Test
    void flowWithQuoteCharactersExceedingMaximumLineWidth() {
        String input = ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> A few words";

        String result = FlowedMessageUtils.flow(input, DEL_SP_NO);

        assertThat(result).isEqualTo(
            ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> A \r\n" +
                ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> few \r\n" +
                ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> words"
        );
    }

    @Test
    void flowWithDelSpAndLongLineWithAsciiCharactersButWithoutAnySpaces() {
        String input = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

        String result = FlowedMessageUtils.flow(input, DEL_SP_YES);

        assertThat(result).isEqualTo(input);
    }

    @Test
    void flowWithDelSpAndLongLineWithNonAsciiCharacters() {
        String input = "äääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääää";

        String result = FlowedMessageUtils.flow(input, DEL_SP_YES);

        assertThat(result).isEqualTo(
            "äääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääää \r\n" +
                "ääääääääääää"
        );
    }

    @Test
    @SuppressWarnings("checkstyle:avoidescapedunicodecharacters")
    void flowWithDelSpAndLongLineWithNonAsciiCharactersAndSurrogatePairAtSplitPoint() {
        String input = "ääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääää\uD83C\uDF69ääääää";

        String result = FlowedMessageUtils.flow(input, DEL_SP_YES);

        assertThat(result).isEqualTo(
            "ääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääää\uD83C\uDF69 \r\n" +
                "ääääää"
        );
    }

    @Test
    void flowWithDelSpAndQuotedLineExactly77CharactersLong() {
        String input = ">AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

        String result = FlowedMessageUtils.flow(input, DEL_SP_YES);

        assertThat(result).isEqualTo(
            "> AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        );
    }

    @Test
    void flowWithDelSpAndQuoteCharactersExceedingMaximumLineWidth() {
        String input = ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\r\n";

        String result = FlowedMessageUtils.flow(input, DEL_SP_YES);

        assertThat(result).isEqualTo(input);
    }
}
