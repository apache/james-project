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
package org.apache.james.server.core.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

public class SimpleUrlTest {

    @Test()
    public void simplifyPathShouldThrowOnNull() {
        assertThatThrownBy(() -> SimpleUrl.simplifyPath(null)).isInstanceOf(NullPointerException.class);
    }
    
    @Test
    public void simplifyPathShouldReturnEmptyOnEmptyArray() {
        String actual = SimpleUrl.simplifyPath("");
        assertThat(actual).isEmpty();
    }

    @Test
    public void simplifyPathShoudReturnEmptyWhenSimplifyingCurrentDir() {
        String actual = SimpleUrl.simplifyPath("./bar/.././foo/..");
        assertThat(actual).isEmpty();
    }

    @Test
    public void simplifyPathShoudReturnSimplifiedDirectory() {
        String actual = SimpleUrl.simplifyPath("../foo/../bar/./baz");
        assertThat(actual).isEqualTo("../bar/baz");
    }

    @Test
    public void simplifiedShouldReturnEmptyWhenEmptyInput() {
        assertThat(new SimpleUrl("").getSimplified()).isEmpty();
    }

    @Test
    public void simplifiedShouldThrowWhenNullInput() {
        assertThatThrownBy(() -> new SimpleUrl(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void simplifiedShouldReturnInputValueWhenProtocolOnlyInput() {
        assertThat(new SimpleUrl("file:").getSimplified()).isEqualTo("file:");
    }

    @Test
    public void simplifiedShouldReturnInputValueWhenRelativePath() {
        assertThat(new SimpleUrl("abcd/ef/gh").getSimplified()).isEqualTo("abcd/ef/gh");
    }

    @Test
    public void simplifiedShouldReturnInputValueWhenAbsolutePath() {
        assertThat(new SimpleUrl("/abcd/ef/gh").getSimplified()).isEqualTo("/abcd/ef/gh");
    }

    @Test
    public void simplifiedShouldReturnInputValueWhenHttpUrl() {
        assertThat(new SimpleUrl("http://example.com/ef/gh").getSimplified()).isEqualTo("http://example.com/ef/gh");
    }

    @Test
    public void simplifiedShouldReturnInputValueWhenPathContainsColumn() {
        assertThat(new SimpleUrl("ab/cd:ef/gh").getSimplified()).isEqualTo("ab/cd:ef/gh");
    }

    @Test
    public void simplifiedShouldCollapseComplexePathWhenContainingParentDirElement() {
        assertThat(new SimpleUrl("file:///home/user/./foo/../.bar/baz").getSimplified()).isEqualTo("file:///home/user/.bar/baz");
    }

    @Test
    public void simplifiedShouldCollapseComplexePathWhenContainingParentDirElementInRelativePath() {
        assertThat(new SimpleUrl("file://../.././foo/../.bar/baz").getSimplified()).isEqualTo("file://../../.bar/baz");
    }

    @Test
    public void simplifiedShouldCollapseComplexePathWhenContainingParentDirElementWithoutDoubleSlashes() {
        assertThat(new SimpleUrl("file:/home/user/./foo/../.bar/baz").getSimplified()).isEqualTo("file:/home/user/.bar/baz");
    }

    @Test
    public void simplifiedShouldCollapseComplexePathWhenContainingParentDirElementInRelativePathWithoutDoubleSlashes() {
        assertThat(new SimpleUrl("file:../.././foo/../.bar/baz").getSimplified()).isEqualTo("file:../../.bar/baz");
    }

    @Test
    public void simplifiedShouldReplaceASingleWindowSeperatorByASlash() {
        assertThat(new SimpleUrl("\\").getSimplified()).isEqualTo("/");
    }

    @Test
    public void simplifiedShouldReplaceAllWindowsSeperatorBySlashes() {
        assertThat(new SimpleUrl("file:c:\\\\programs\\run.exe").getSimplified()).isEqualTo("file:c://programs/run.exe");
    }
}
