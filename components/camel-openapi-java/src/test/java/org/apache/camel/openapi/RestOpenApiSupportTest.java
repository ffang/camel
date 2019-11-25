/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.openapi;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.camel.openapi.RestOpenApiSupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.apicurio.datamodels.openapi.v2.models.Oas20Document;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyZeroInteractions;

public class RestOpenApiSupportTest {

    @Test
    public void shouldAdaptFromXForwardHeaders() {
        Oas20Document doc = new Oas20Document();
        doc.basePath = "/base";
        final Oas20Document swagger = spy(doc);

        final Map<String, Object> headers = new HashMap<>();
        headers.put(RestOpenApiSupport.HEADER_X_FORWARDED_PREFIX, "/prefix");
        headers.put(RestOpenApiSupport.HEADER_X_FORWARDED_HOST, "host");
        headers.put(RestOpenApiSupport.HEADER_X_FORWARDED_PROTO, "http, HTTPS ");
        RestOpenApiSupport.setupXForwardedHeaders(swagger, headers);

        
        assertEquals(swagger.basePath, "/prefix/base");
        assertEquals(swagger.host, "host");
        assertTrue(swagger.schemes.contains("http"));
        assertTrue(swagger.schemes.contains("https"));
            
    }

    @ParameterizedTest
    @MethodSource("basePathAndPrefixVariations")
    public void shouldAdaptWithVaryingBasePathsAndPrefixes(final String prefix, final String basePath,
        final String expected) {
        Oas20Document doc = new Oas20Document();
        doc.basePath = basePath;
        final Oas20Document swagger = spy(doc);

        final Map<String, Object> headers = new HashMap<>();
        headers.put(RestOpenApiSupport.HEADER_X_FORWARDED_PREFIX, prefix);
        RestOpenApiSupport.setupXForwardedHeaders(swagger, headers);

        assertEquals(swagger.basePath, expected);
    }

    @ParameterizedTest
    @MethodSource("schemeVariations")
    public void shouldAdaptWithVaryingSchemes(final String xForwardedScheme, final String[] expected) {
        final Oas20Document swagger = spy(new Oas20Document());

        RestOpenApiSupport.setupXForwardedHeaders(swagger,
            Collections.singletonMap(RestOpenApiSupport.HEADER_X_FORWARDED_PROTO, xForwardedScheme));

        for (final String scheme : expected) {
            assertTrue(swagger.schemes.contains(scheme));
        }

    }

    @Test
    public void shouldNotAdaptFromXForwardHeadersWhenNoHeadersSpecified() {
        final Oas20Document swagger = spy(new Oas20Document());

        RestOpenApiSupport.setupXForwardedHeaders(swagger, Collections.emptyMap());

        verifyZeroInteractions(swagger);
    }

    static Stream<Arguments> basePathAndPrefixVariations() {
        return Stream.of(//
            arguments("/prefix", "/base", "/prefix/base"), //
            arguments("/prefix", "/base/", "/prefix/base/"), //
            arguments("/prefix", "base", "/prefix/base"), //
            arguments("/prefix", "base/", "/prefix/base/"), //
            arguments("/prefix", "", "/prefix"), //
            arguments("/prefix", null, "/prefix"), //
            arguments("/prefix/", "/base", "/prefix/base"), //
            arguments("/prefix/", "/base/", "/prefix/base/"), //
            arguments("/prefix/", "base", "/prefix/base"), //
            arguments("/prefix/", "base/", "/prefix/base/"), //
            arguments("/prefix/", "", "/prefix/"), //
            arguments("/prefix/", null, "/prefix/"), //
            arguments("prefix", "/base", "prefix/base"), //
            arguments("prefix", "/base/", "prefix/base/"), //
            arguments("prefix", "base", "prefix/base"), //
            arguments("prefix", "base/", "prefix/base/"), //
            arguments("prefix", "", "prefix"), //
            arguments("prefix", null, "prefix"), //
            arguments("prefix/", "/base", "prefix/base"), //
            arguments("prefix/", "/base/", "prefix/base/"), //
            arguments("prefix/", "base", "prefix/base"), //
            arguments("prefix/", "base/", "prefix/base/"), //
            arguments("prefix/", "", "prefix/"), //
            arguments("prefix/", null, "prefix/") //
        );
    }

    static Stream<Arguments> schemeVariations() {
        final String[] none = new String[0];

        return Stream.of(//
            arguments(null, none), //
            arguments("", none), //
            arguments(",", none), //
            arguments(" , ", none), //
            arguments("HTTPS,http", new String[] {"https", "http"}), //
            arguments(" HTTPS,  http ", new String[] {"https", "http"}), //
            arguments(",http,", new String[] {"http"}), //
            arguments("hTtpS", new String[] {"https"})//
        );
    }
}
