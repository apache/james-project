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
package org.apache.james.http.jetty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.servlet.Filter;
import javax.servlet.Servlet;

import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class ConfigurationTest {

    @Test
    public void defaultConfigurationDefinition() {
        Configuration defaultConfiguration = Configuration.defaultConfiguration();
        assertThat(defaultConfiguration.getPort()).isEmpty();
        assertThat(defaultConfiguration.getMappings()).isEmpty();
    }

    @Test
    public void shouldAllowWorkingDefinition() {
        Bad400 bad400 = new Bad400();
        SpyFilter spyFilter = new SpyFilter();
        LambdaFilter anotherFilter = (req, resp, chain) -> chain.doFilter(req, resp);
        Configuration testee = Configuration
                .builder()
                .port(2000)
                .serve("/abc")
                .with(Ok200.class)
                .serve("/def")
                .with(bad400)
                .filter("/123")
                .with(CoolFilter.class)
                .and(anotherFilter).only()
                .filter("/456")
                .with(spyFilter).only()
                .serveAsOneLevelTemplate("/level")
                .with(Ok200.class)
                .build();
        assertThat(testee.getPort()).isPresent().contains(2000);
        assertThat(testee.getMappings())
            .hasSize(3)
            .containsEntry("/abc", Ok200.class)
            .containsEntry("/def", bad400)
            .containsEntry("/level/*", Ok200.class);
        assertThat(testee.getFilters().asMap())
            .hasSize(2)
            .containsEntry("/123", ImmutableList.of(CoolFilter.class, anotherFilter))
            .containsEntry("/456", ImmutableList.of(spyFilter));
    }

    @Test
    public void shouldAllowRandomPort() {
        Configuration testee = Configuration.builder().randomPort().build();
        assertThat(testee.getPort()).isEmpty();
    }
    
    @Test
    public void shouldNotAllowNegativePort() {
        assertThatThrownBy(() -> Configuration.builder().port(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldNotAllowZeroPort() {
        assertThatThrownBy(() -> Configuration.builder().port(0)).isInstanceOf(IllegalArgumentException.class);
    }


    @Test
    public void shouldNotAllowTooLargePort() {
        assertThatThrownBy(() -> Configuration.builder().port(65536)).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void shouldNotAllowOverridingPortWithRandom() {
        Configuration configuration = Configuration.builder().port(143).randomPort().build();
        assertThat(configuration.getPort()).isEmpty();
    }
    
    @Test
    public void shouldNotAllowNullServletMappingUrl() {
        assertThatThrownBy(() -> Configuration.builder().serve(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void shouldNotAllowEmptyServletMappingUrl() {
        assertThatThrownBy(() -> Configuration.builder().serve("")).isInstanceOf(IllegalArgumentException.class);
    }


    @Test
    public void shouldNotAllowWhitespaceOnlyServletMappingUrl() {
        assertThatThrownBy(() -> Configuration.builder().serve("    ")).isInstanceOf(IllegalArgumentException.class);
    }
    

    @Test
    public void shouldNotAllowNullServlet() {
        assertThatThrownBy(() -> Configuration.builder().serve("/").with((Servlet)null)).isInstanceOf(NullPointerException.class);
    }
    
    @Test
    public void shouldNotAllowNullServletClassname() {
        assertThatThrownBy(() -> Configuration.builder().serve("/").with((Class<? extends Servlet>)null)).isInstanceOf(NullPointerException.class);
    }
    
    @Test
    public void shouldNotAllowNullServletAsOneLevelTemplateMappingUrl() {
        assertThatThrownBy(() -> Configuration.builder().serveAsOneLevelTemplate(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void shouldNotAllowEmptyServletAsOneLevelTemplateMappingUrl() {
        assertThatThrownBy(() -> Configuration.builder().serveAsOneLevelTemplate("")).isInstanceOf(IllegalArgumentException.class);
    }


    @Test
    public void shouldNotAllowWhitespaceOnlyServletAsOneLevelTemplateMappingUrl() {
        assertThatThrownBy(() -> Configuration.builder().serveAsOneLevelTemplate("    ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldNotAllowNullFilterMappingUrl() {
        assertThatThrownBy(() -> Configuration.builder().filter(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void shouldNotAllowEmptyFilterMappingUrl() {
        assertThatThrownBy(() -> Configuration.builder().filter("")).isInstanceOf(IllegalArgumentException.class);
    }


    @Test
    public void shouldNotAllowWhitespaceOnlyFilterMappingUrl() {
        assertThatThrownBy(() -> Configuration.builder().filter("    ")).isInstanceOf(IllegalArgumentException.class);
    }
    

    @Test
    public void shouldNotAllowNullFilter() {
        assertThatThrownBy(() -> Configuration.builder().filter("/").with((Filter)null)).isInstanceOf(NullPointerException.class);
    }
    
    @Test
    public void shouldNotAllowNullFilterClassname() {
        assertThatThrownBy(() -> Configuration.builder().filter("/").with((Class<? extends Filter>)null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void shouldNotAllowNullFilterAsOneLevelTemplateMappingUrl() {
        assertThatThrownBy(() -> Configuration.builder().filterAsOneLevelTemplate(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void shouldNotAllowEmptyFilterAsOneLevelTemplateMappingUrl() {
        assertThatThrownBy(() -> Configuration.builder().filterAsOneLevelTemplate("")).isInstanceOf(IllegalArgumentException.class);
    }


    @Test
    public void shouldNotAllowWhitespaceOnlyFilterAsOneLevelTemplateMappingUrl() {
        assertThatThrownBy(() -> Configuration.builder().filterAsOneLevelTemplate("    ")).isInstanceOf(IllegalArgumentException.class);
    }
    

    @Test
    public void shouldNotAllowNullFilterAsOneLevelTemplate() {
        assertThatThrownBy(() -> Configuration.builder().filterAsOneLevelTemplate("/").with((Filter)null)).isInstanceOf(NullPointerException.class);
    }
    
    @Test
    public void shouldNotAllowNullFilterClassnameAsOneLevelTemplate() {
        assertThatThrownBy(() -> Configuration.builder().filterAsOneLevelTemplate("/").with((Class<? extends Filter>)null)).isInstanceOf(NullPointerException.class);
    }
}
