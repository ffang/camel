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
package org.apache.camel.component.rest.swagger;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.spi.RestConfiguration;
import org.junit.Test;

import io.apicurio.datamodels.core.models.common.SecurityRequirement;
import io.apicurio.datamodels.openapi.models.OasParameter;
import io.apicurio.datamodels.openapi.v2.models.Oas20Document;
import io.apicurio.datamodels.openapi.v2.models.Oas20Operation;
import io.apicurio.datamodels.openapi.v2.models.Oas20Parameter;
import io.apicurio.datamodels.openapi.v2.models.Oas20SecurityScheme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RestSwaggerEndpointTest {

    URI componentJsonUri = URI.create("component.json");

    URI endpointUri = URI.create("endpoint.json");

    @Test(expected = IllegalArgumentException.class)
    public void shouldComplainForUnknownOperations() throws Exception {
        final CamelContext camelContext = mock(CamelContext.class);
        when(camelContext.getClassResolver()).thenReturn(new DefaultClassResolver());

        final RestSwaggerComponent component = new RestSwaggerComponent(camelContext);

        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("rest-swagger:unknown", "unknown", component,
            Collections.emptyMap());

        endpoint.createProducer();
    }

    @Test
    public void shouldComputeQueryParameters() {
        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint();
        endpoint.parameters = new HashMap<>();
        endpoint.parameters.put("literal", "value");
        assertThat(endpoint.queryParameter(new Oas20Parameter())).isEqualTo("");
        assertThat(endpoint.queryParameter(new Oas20Parameter("param"))).isEqualTo("param={param?}");
        assertThat(endpoint.queryParameter(new Oas20Parameter("literal"))).isEqualTo("literal=value");
    }

    @Test
    public void shouldCreateQueryParameterExpressions() {
        Oas20Parameter oas20Parameter = new Oas20Parameter("q");
        oas20Parameter.required = true;
        assertThat(RestSwaggerEndpoint.queryParameterExpression(oas20Parameter))
            .isEqualTo("q={q}");
        oas20Parameter.required = false;
        assertThat(RestSwaggerEndpoint.queryParameterExpression(oas20Parameter))
            .isEqualTo("q={q?}");
    }

    @Test
    public void shouldDetermineBasePath() {
        final RestConfiguration restConfiguration = new RestConfiguration();

        final CamelContext camelContext = mock(CamelContext.class);
        when(camelContext.getRestConfiguration("rest-swagger", true)).thenReturn(restConfiguration);

        final Oas20Document swagger = new Oas20Document();

        final RestSwaggerComponent component = new RestSwaggerComponent();
        component.setCamelContext(camelContext);
        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("rest-swagger:getPetById", "getPetById", component,
            Collections.emptyMap());

        assertThat(endpoint.determineBasePath(swagger))
            .as("When no base path is specified on component, endpoint or rest configuration it should default to `/`")
            .isEqualTo("/");

        restConfiguration.setContextPath("/rest");
        assertThat(endpoint.determineBasePath(swagger)).as(
            "When base path is specified in REST configuration and not specified in component the base path should be from the REST configuration")
            .isEqualTo("/rest");

        swagger.basePath = "/specification";
        assertThat(endpoint.determineBasePath(swagger)).as(
            "When base path is specified in the specification it should take precedence the one specified in the REST configuration")
            .isEqualTo("/specification");

        component.setBasePath("/component");
        assertThat(endpoint.determineBasePath(swagger)).as(
            "When base path is specified on the component it should take precedence over Swagger specification and REST configuration")
            .isEqualTo("/component");

        endpoint.setBasePath("/endpoint");
        assertThat(endpoint.determineBasePath(swagger))
            .as("When base path is specified on the endpoint it should take precedence over any other")
            .isEqualTo("/endpoint");
    }

    @Test
    public void shouldDetermineEndpointParameters() {
        final CamelContext camelContext = mock(CamelContext.class);

        final RestSwaggerComponent component = new RestSwaggerComponent();
        component.setCamelContext(camelContext);

        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("uri", "remaining", component,
            Collections.emptyMap());
        endpoint.setHost("http://petstore.swagger.io");

        final Oas20Document swagger = new Oas20Document();
        final Oas20Operation operation = new Oas20Operation("get");
        operation.createParameter();
        assertThat(endpoint.determineEndpointParameters(swagger, operation))
            .containsOnly(entry("host", "http://petstore.swagger.io"));
        

        component.setComponentName("xyz");
        assertThat(endpoint.determineEndpointParameters(swagger, operation))
            .containsOnly(entry("host", "http://petstore.swagger.io"), entry("producerComponentName", "xyz"));

        List<String> consumers = new ArrayList<String>();
        consumers.add("application/json");
        List<String> produces = new ArrayList<String>();
        produces.add("application/xml");
        swagger.consumes = consumers;
        swagger.produces = produces;
                
        assertThat(endpoint.determineEndpointParameters(swagger, operation)).containsOnly(
            entry("host", "http://petstore.swagger.io"), entry("producerComponentName", "xyz"),
            entry("consumes", "application/xml"), entry("produces", "application/json"));

        component.setProduces("application/json");
        component.setConsumes("application/atom+xml");
        assertThat(endpoint.determineEndpointParameters(swagger, operation)).containsOnly(
            entry("host", "http://petstore.swagger.io"), entry("producerComponentName", "xyz"),
            entry("consumes", "application/atom+xml"), entry("produces", "application/json"));

        endpoint.setProduces("application/atom+xml");
        endpoint.setConsumes("application/json");
        assertThat(endpoint.determineEndpointParameters(swagger, operation)).containsOnly(
            entry("host", "http://petstore.swagger.io"), entry("producerComponentName", "xyz"),
            entry("consumes", "application/json"), entry("produces", "application/atom+xml"));

        endpoint.setComponentName("zyx");
        assertThat(endpoint.determineEndpointParameters(swagger, operation)).containsOnly(
            entry("host", "http://petstore.swagger.io"), entry("producerComponentName", "zyx"),
            entry("consumes", "application/json"), entry("produces", "application/atom+xml"));

        Oas20Parameter oas20Parameter = new Oas20Parameter("q");
        oas20Parameter.in = "query";
        oas20Parameter.required = true;
        operation.addParameter(oas20Parameter);
        assertThat(endpoint.determineEndpointParameters(swagger, operation)).containsOnly(
            entry("host", "http://petstore.swagger.io"), entry("producerComponentName", "zyx"),
            entry("consumes", "application/json"), entry("produces", "application/atom+xml"),
            entry("queryParameters", "q={q}"));

        oas20Parameter = new Oas20Parameter("o");
        oas20Parameter.in = "query";
        operation.addParameter(oas20Parameter);
        assertThat(endpoint.determineEndpointParameters(swagger, operation)).containsOnly(
            entry("host", "http://petstore.swagger.io"), entry("producerComponentName", "zyx"),
            entry("consumes", "application/json"), entry("produces", "application/atom+xml"),
            entry("queryParameters", "q={q}&o={o?}"));
    }

    @Test
    public void shouldDetermineHostFromRestConfiguration() {
        assertThat(RestSwaggerEndpoint.hostFrom(null)).isNull();

        final RestConfiguration configuration = new RestConfiguration();
        assertThat(RestSwaggerEndpoint.hostFrom(configuration)).isNull();

        configuration.setScheme("ftp");
        assertThat(RestSwaggerEndpoint.hostFrom(configuration)).isNull();

        configuration.setScheme("http");
        assertThat(RestSwaggerEndpoint.hostFrom(configuration)).isNull();

        configuration.setHost("petstore.swagger.io");
        assertThat(RestSwaggerEndpoint.hostFrom(configuration)).isEqualTo("http://petstore.swagger.io");

        configuration.setPort(80);
        assertThat(RestSwaggerEndpoint.hostFrom(configuration)).isEqualTo("http://petstore.swagger.io");

        configuration.setPort(8080);
        assertThat(RestSwaggerEndpoint.hostFrom(configuration)).isEqualTo("http://petstore.swagger.io:8080");

        configuration.setScheme("https");
        configuration.setPort(80);
        assertThat(RestSwaggerEndpoint.hostFrom(configuration)).isEqualTo("https://petstore.swagger.io:80");

        configuration.setPort(443);
        assertThat(RestSwaggerEndpoint.hostFrom(configuration)).isEqualTo("https://petstore.swagger.io");
    }

    @Test
    public void shouldDetermineHostFromSpecification() {
        final RestSwaggerComponent component = new RestSwaggerComponent();

        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("rest-swagger:http://some-uri#getPetById",
            "http://some-uri#getPetById", component, Collections.emptyMap());

        final Oas20Document swagger = new Oas20Document();
        swagger.host = "petstore.swagger.io";

        assertThat(endpoint.determineHost(swagger)).isEqualTo("http://petstore.swagger.io");

        swagger.schemes = Arrays.asList("https");
        assertThat(endpoint.determineHost(swagger)).isEqualTo("https://petstore.swagger.io");
    }

    @Test
    public void shouldDetermineOptions() {
        assertThat(RestSwaggerEndpoint.determineOption(null, null, null, null)).isNull();

        assertThat(RestSwaggerEndpoint.determineOption(Collections.emptyList(), Collections.emptyList(), "", ""))
            .isNull();

        assertThat(RestSwaggerEndpoint.determineOption(Arrays.asList("specification"), null, null, null))
            .isEqualTo("specification");

        assertThat(
            RestSwaggerEndpoint.determineOption(Arrays.asList("specification"), Arrays.asList("operation"), null, null))
                .isEqualTo("operation");

        assertThat(RestSwaggerEndpoint.determineOption(Arrays.asList("specification"), Arrays.asList("operation"),
            "component", null)).isEqualTo("component");

        assertThat(RestSwaggerEndpoint.determineOption(Arrays.asList("specification"), Arrays.asList("operation"),
            "component", "operation")).isEqualTo("operation");
    }

    @Test
    public void shouldHonourComponentSpecificationPathProperty() throws Exception {
        final RestSwaggerComponent component = new RestSwaggerComponent();
        component.setSpecificationUri(componentJsonUri);

        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("rest-swagger:getPetById", "getPetById", component,
            Collections.emptyMap());

        assertThat(endpoint.getSpecificationUri()).isEqualTo(componentJsonUri);
    }

    @Test
    public void shouldHonourEndpointUriPathSpecificationPathProperty() throws Exception {
        final RestSwaggerComponent component = new RestSwaggerComponent();
        component.setSpecificationUri(componentJsonUri);

        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("rest-swagger:endpoint.json#getPetById",
            "endpoint.json#getPetById", component, Collections.emptyMap());

        assertThat(endpoint.getSpecificationUri()).isEqualTo(endpointUri);
    }

    @Test
    public void shouldHonourHostPrecedence() {
        final RestConfiguration globalRestConfiguration = new RestConfiguration();

        final RestConfiguration componentRestConfiguration = new RestConfiguration();
        final RestConfiguration specificRestConfiguration = new RestConfiguration();

        final CamelContext camelContext = mock(CamelContext.class);
        when(camelContext.getRestConfiguration()).thenReturn(globalRestConfiguration);
        when(camelContext.getRestConfiguration("rest-swagger", false)).thenReturn(componentRestConfiguration);
        when(camelContext.getRestConfiguration("petstore", false)).thenReturn(specificRestConfiguration);

        final RestSwaggerComponent component = new RestSwaggerComponent();
        component.setCamelContext(camelContext);

        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("petstore:http://specification-uri#getPetById",
            "http://specification-uri#getPetById", component, Collections.emptyMap());

        final Oas20Document swagger = new Oas20Document();
        assertThat(endpoint.determineHost(swagger)).isEqualTo("http://specification-uri");

        globalRestConfiguration.setHost("global-rest");
        globalRestConfiguration.setScheme("http");
        assertThat(endpoint.determineHost(swagger)).isEqualTo("http://global-rest");

        globalRestConfiguration.setHost("component-rest");
        globalRestConfiguration.setScheme("http");
        assertThat(endpoint.determineHost(swagger)).isEqualTo("http://component-rest");

        specificRestConfiguration.setHost("specific-rest");
        specificRestConfiguration.setScheme("http");
        assertThat(endpoint.determineHost(swagger)).isEqualTo("http://specific-rest");

        swagger.host = "specification";
        swagger.schemes = Arrays.asList("http");
        assertThat(endpoint.determineHost(swagger)).isEqualTo("http://specification");

        component.setHost("http://component");
        assertThat(endpoint.determineHost(swagger)).isEqualTo("http://component");

        endpoint.setHost("http://endpoint");
        assertThat(endpoint.determineHost(swagger)).isEqualTo("http://endpoint");
    }

    @Test
    public void shouldIncludeApiKeysQueryParameters() {
        final CamelContext camelContext = mock(CamelContext.class);

        final RestSwaggerComponent component = new RestSwaggerComponent();
        component.setCamelContext(camelContext);

        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("uri", "remaining", component,
            Collections.emptyMap());
        endpoint.setHost("http://petstore.swagger.io");

        final Oas20Document swagger = new Oas20Document();
        final Oas20SecurityScheme apiKeys = new Oas20SecurityScheme("key");
        apiKeys.name = "key";
        apiKeys.in = "header";
        swagger.securityDefinitions = swagger.createSecurityDefinitions();
        swagger.securityDefinitions.addItem("apiKeys", apiKeys);
        
        final Oas20Operation operation = new Oas20Operation("get");
        Oas20Parameter oas20Parameter = new Oas20Parameter("q");
        oas20Parameter.in = "query";
        oas20Parameter.required = true;
        operation.addParameter(oas20Parameter);
        SecurityRequirement securityRequirement =  operation.createSecurityRequirement();
        securityRequirement.addSecurityRequirementItem("apiKeys", Collections.emptyList());
        operation.addSecurityRequirement(securityRequirement);
        
        
        assertThat(endpoint.determineEndpointParameters(swagger, operation))
            .containsOnly(entry("host", "http://petstore.swagger.io"), entry("queryParameters", "q={q}"));

        apiKeys.in = "query";
        assertThat(endpoint.determineEndpointParameters(swagger, operation))
            .containsOnly(entry("host", "http://petstore.swagger.io"), entry("queryParameters", "key={key}&q={q}"));
    }

    @Test
    public void shouldLoadSwaggerSpecifications() throws IOException {
        final CamelContext camelContext = mock(CamelContext.class);
        when(camelContext.getClassResolver()).thenReturn(new DefaultClassResolver());

        assertThat(
            RestSwaggerEndpoint.loadSpecificationFrom(camelContext, RestSwaggerComponent.DEFAULT_SPECIFICATION_URI))
                .isNotNull();
    }

    @Test
    public void shouldPickBestScheme() {
        assertThat(RestSwaggerEndpoint.pickBestScheme("http", Arrays.asList("http", "https")))
            .isEqualTo("https");

        assertThat(RestSwaggerEndpoint.pickBestScheme("https", Arrays.asList("http"))).isEqualTo("http");

        assertThat(RestSwaggerEndpoint.pickBestScheme("http", Collections.emptyList())).isEqualTo("http");

        assertThat(RestSwaggerEndpoint.pickBestScheme("http", null)).isEqualTo("http");

        assertThat(RestSwaggerEndpoint.pickBestScheme(null, Collections.emptyList())).isNull();

        assertThat(RestSwaggerEndpoint.pickBestScheme(null, null)).isNull();
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRaiseExceptionsForMissingSpecifications() throws IOException {
        final CamelContext camelContext = mock(CamelContext.class);
        when(camelContext.getClassResolver()).thenReturn(new DefaultClassResolver());

        RestSwaggerEndpoint.loadSpecificationFrom(camelContext, URI.create("non-existant.json"));
    }

    @Test
    public void shouldResolveUris() {
        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint();
        endpoint.parameters = new HashMap<>();
        endpoint.parameters.put("param1", "value1");

        final Map<String, OasParameter> pathParameters = new HashMap<>();
        pathParameters.put("param1", new Oas20Parameter("param1"));
        pathParameters.put("param2", new Oas20Parameter("param2"));

        assertThat(endpoint.resolveUri("/path", pathParameters)).isEqualTo("/path");
        assertThat(endpoint.resolveUri("/path/{param1}", pathParameters)).isEqualTo("/path/value1");
        assertThat(endpoint.resolveUri("/{param1}/path", pathParameters)).isEqualTo("/value1/path");
        assertThat(endpoint.resolveUri("/{param1}/path/{param2}", pathParameters)).isEqualTo("/value1/path/{param2}");
        assertThat(endpoint.resolveUri("/{param1}/{param2}", pathParameters)).isEqualTo("/value1/{param2}");
        assertThat(endpoint.resolveUri("/path/{param1}/to/{param2}/rest", pathParameters))
            .isEqualTo("/path/value1/to/{param2}/rest");
    }

    @Test
    public void shouldSerializeGivenLiteralValues() {
        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint();
        endpoint.parameters = new HashMap<>();
        endpoint.parameters.put("param", "va lue");

        final OasParameter queryParameter = new Oas20Parameter("param");

        assertThat(endpoint.literalQueryParameterValue(queryParameter)).isEqualTo("param=va%20lue");

        final Oas20Parameter pathParameter = new Oas20Parameter("param");
        assertThat(endpoint.literalPathParameterValue(pathParameter)).isEqualTo("va%20lue");
    }

    @Test
    public void shouldUseDefaultSpecificationUri() throws Exception {
        final RestSwaggerComponent component = new RestSwaggerComponent();

        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("rest-swagger:getPetById", "getPetById", component,
            Collections.emptyMap());

        assertThat(endpoint.getSpecificationUri()).isEqualTo(RestSwaggerComponent.DEFAULT_SPECIFICATION_URI);
    }

    @Test
    public void shouldUseDefaultSpecificationUriEvenIfHashIsPresent() throws Exception {
        final RestSwaggerComponent component = new RestSwaggerComponent();

        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("rest-swagger:#getPetById", "#getPetById",
            component, Collections.emptyMap());

        assertThat(endpoint.getSpecificationUri()).isEqualTo(RestSwaggerComponent.DEFAULT_SPECIFICATION_URI);
    }

}
