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
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.UnsafeUriCharactersEncoder;

import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.core.models.Document;
import io.apicurio.datamodels.core.models.common.SecurityRequirement;
import io.apicurio.datamodels.openapi.models.OasDocument;
import io.apicurio.datamodels.openapi.models.OasOperation;
import io.apicurio.datamodels.openapi.models.OasParameter;
import io.apicurio.datamodels.openapi.models.OasPathItem;
import io.apicurio.datamodels.openapi.models.OasPaths;
import io.apicurio.datamodels.openapi.models.OasResponse;
import io.apicurio.datamodels.openapi.v2.models.Oas20Document;
import io.apicurio.datamodels.openapi.v2.models.Oas20Operation;
import io.apicurio.datamodels.openapi.v2.models.Oas20Parameter;
import io.apicurio.datamodels.openapi.v2.models.Oas20SecurityDefinitions;
import io.apicurio.datamodels.openapi.v2.models.Oas20SecurityScheme;
import io.apicurio.datamodels.openapi.v3.models.Oas30Document;
import io.apicurio.datamodels.openapi.v3.models.Oas30Operation;
import io.apicurio.datamodels.openapi.v3.models.Oas30Response;
import io.apicurio.datamodels.openapi.v3.models.Oas30SecurityScheme;

import static org.apache.camel.component.rest.swagger.RestSwaggerHelper.isHostParam;
import static org.apache.camel.component.rest.swagger.RestSwaggerHelper.isMediaRange;
import static org.apache.camel.util.ObjectHelper.isNotEmpty;
import static org.apache.camel.util.ObjectHelper.notNull;
import static org.apache.camel.util.StringHelper.after;
import static org.apache.camel.util.StringHelper.before;
import static org.apache.camel.util.StringHelper.notEmpty;

/**
 * An awesome REST endpoint backed by Swagger specifications.
 */
@UriEndpoint(firstVersion = "2.19.0", scheme = "rest-swagger", title = "REST Swagger",
    syntax = "rest-swagger:specificationUri#operationId", label = "rest,swagger,http", producerOnly = true)
public final class RestSwaggerEndpoint extends DefaultEndpoint {

    /**
     * Remaining parameters specified in the Endpoint URI.
     */
    Map<String, Object> parameters = Collections.emptyMap();

    /** The name of the Camel component, be it `rest-swagger` or `petstore` */
    private String assignedComponentName;

    @UriParam(
        description = "API basePath, for example \"`/v2`\". Default is unset, if set overrides the value present in"
            + " Swagger specification and in the component configuration.",
        defaultValue = "", label = "producer")
    private String basePath;

    @UriParam(description = "Name of the Camel component that will perform the requests. The component must be present"
        + " in Camel registry and it must implement RestProducerFactory service provider interface. If not set"
        + " CLASSPATH is searched for single component that implements RestProducerFactory SPI. Overrides"
        + " component configuration.", label = "producer")
    private String componentName;

    @UriParam(
        description = "What payload type this component capable of consuming. Could be one type, like `application/json`"
            + " or multiple types as `application/json, application/xml; q=0.5` according to the RFC7231. This equates"
            + " to the value of `Accept` HTTP header. If set overrides any value found in the Swagger specification and."
            + " in the component configuration",
        label = "producer")
    private String consumes;

    @UriParam(description = "Scheme hostname and port to direct the HTTP requests to in the form of"
        + " `http[s]://hostname[:port]`. Can be configured at the endpoint, component or in the corresponding"
        + " REST configuration in the Camel Context. If you give this component a name (e.g. `petstore`) that"
        + " REST configuration is consulted first, `rest-swagger` next, and global configuration last. If set"
        + " overrides any value found in the Swagger specification, RestConfiguration. Overrides all other "
        + " configuration.", label = "producer")
    private String host;

    @UriPath(description = "ID of the operation from the Swagger specification.", label = "producer")
    @Metadata(required = true)
    private String operationId;

    @UriParam(description = "What payload type this component is producing. For example `application/json`"
        + " according to the RFC7231. This equates to the value of `Content-Type` HTTP header. If set overrides"
        + " any value present in the Swagger specification. Overrides all other configuration.", label = "producer")
    private String produces;

    @UriPath(description = "Path to the Swagger specification file. The scheme, host base path are taken from this"
        + " specification, but these can be overridden with properties on the component or endpoint level. If not"
        + " given the component tries to load `swagger.json` resource from the classpath. Note that the `host` defined on the"
        + " component and endpoint of this Component should contain the scheme, hostname and optionally the"
        + " port in the URI syntax (i.e. `http://api.example.com:8080`). Overrides component configuration."
        + " The Swagger specification can be loaded from different sources by prefixing with file: classpath: http: https:."
        + " Support for https is limited to using the JDK installed UrlHandler, and as such it can be cumbersome to setup"
        + " TLS/SSL certificates for https (such as setting a number of javax.net.ssl JVM system properties)."
        + " How to do that consult the JDK documentation for UrlHandler.",
        defaultValue = RestSwaggerComponent.DEFAULT_SPECIFICATION_URI_STR,
        defaultValueNote = "By default loads `swagger.json` file", label = "producer")
    private URI specificationUri = RestSwaggerComponent.DEFAULT_SPECIFICATION_URI;

    public RestSwaggerEndpoint() {
        // help tooling instantiate endpoint
    }

    public RestSwaggerEndpoint(final String uri, final String remaining, final RestSwaggerComponent component,
        final Map<String, Object> parameters) {
        super(notEmpty(uri, "uri"), notNull(component, "component"));
        this.parameters = parameters;

        assignedComponentName = before(uri, ":");

        final URI componentSpecificationUri = component.getSpecificationUri();

        specificationUri = before(remaining, "#", StringHelper::trimToNull).map(URI::create)
            .orElse(ofNullable(componentSpecificationUri).orElse(RestSwaggerComponent.DEFAULT_SPECIFICATION_URI));

        operationId = ofNullable(after(remaining, "#")).orElse(remaining);

        setExchangePattern(ExchangePattern.InOut);
    }

    @Override
    public Consumer createConsumer(final Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported");
    }

    @Override
    public Producer createProducer() throws Exception {
        final CamelContext camelContext = getCamelContext();
        final Document openApiDoc = loadSpecificationFrom(camelContext, specificationUri);

        final OasPaths paths = ((OasDocument)openApiDoc).paths;

        for (final OasPathItem path : paths.getItems()) {
            final Optional<Entry<HttpMethod, OasOperation>> maybeOperationEntry = getOperationMap(path).entrySet()
                .stream().filter(operationEntry -> operationId.equals(operationEntry.getValue().operationId))
                .findAny();

            if (maybeOperationEntry.isPresent()) {
                final Entry<HttpMethod, OasOperation> operationEntry = maybeOperationEntry.get();

                final OasOperation operation = operationEntry.getValue();
                final Map<String, OasParameter> pathParameters = operation.getParameters().stream()
                    .filter(p -> "path".equals(p.in))
                    .collect(Collectors.toMap(OasParameter::getName, Function.identity()));
                final String uriTemplate = resolveUri(path.getPath(), pathParameters);

                final HttpMethod httpMethod = operationEntry.getKey();
                final String method = httpMethod.name();

                return createProducerFor(openApiDoc, operation, method, uriTemplate);
            }
        }

        
        String supportedOperations = getSupportedOperations(paths);

        throw new IllegalArgumentException("The specified operation with ID: `" + operationId
            + "` cannot be found in the Swagger specification loaded from `" + specificationUri
            + "`. Operations defined in the specification are: " + supportedOperations);
    }

    private String getSupportedOperations(OasPaths paths) {
        //TODO    
        return "";
    }

    private Map<HttpMethod, OasOperation> getOperationMap(OasPathItem path) {
        Map<HttpMethod, OasOperation> result = new LinkedHashMap<HttpMethod, OasOperation>();

        if (path.get != null) {
            result.put(HttpMethod.GET, path.get);
        }
        if (path.put != null) {
            result.put(HttpMethod.PUT, path.put);
        }
        if (path.post != null) {
            result.put(HttpMethod.POST, path.post);
        }
        if (path.delete != null) {
            result.put(HttpMethod.DELETE, path.delete);
        }
        if (path.patch != null) {
            result.put(HttpMethod.PATCH, path.patch);
        }
        if (path.head != null) {
            result.put(HttpMethod.HEAD, path.head);
        }
        if (path.options != null) {
            result.put(HttpMethod.OPTIONS, path.options);
        }

        return result;
    }

    public String getBasePath() {
        return basePath;
    }

    public String getComponentName() {
        return componentName;
    }

    public String getConsumes() {
        return consumes;
    }

    public String getHost() {
        return host;
    }

    public String getOperationId() {
        return operationId;
    }

    public String getProduces() {
        return produces;
    }

    public URI getSpecificationUri() {
        return specificationUri;
    }

    @Override
    public boolean isLenientProperties() {
        return true;
    }

    public void setBasePath(final String basePath) {
        this.basePath = notEmpty(basePath, "basePath");
    }

    public void setComponentName(final String componentName) {
        this.componentName = notEmpty(componentName, "componentName");
    }

    public void setConsumes(final String consumes) {
        this.consumes = isMediaRange(consumes, "consumes");
    }

    public void setHost(final String host) {
        this.host = isHostParam(host);
    }

    public void setOperationId(final String operationId) {
        this.operationId = notEmpty(operationId, "operationId");
    }

    public void setProduces(final String produces) {
        this.produces = isMediaRange(produces, "produces");
    }

    public void setSpecificationUri(final URI specificationUri) {
        this.specificationUri = notNull(specificationUri, "specificationUri");
    }

    RestSwaggerComponent component() {
        return (RestSwaggerComponent) getComponent();
    }

    Producer createProducerFor(final Document swagger, final OasOperation operation, final String method,
        final String uriTemplate) throws Exception {
        final String basePath = determineBasePath(swagger);

        final StringBuilder componentEndpointUri = new StringBuilder(200).append("rest:").append(method).append(":")
            .append(basePath).append(":").append(uriTemplate);

        final CamelContext camelContext = getCamelContext();

        final Endpoint endpoint = camelContext.getEndpoint(componentEndpointUri.toString());

        Map<String, Object> params = determineEndpointParameters(swagger, operation);
        boolean hasHost = params.containsKey("host");
        if (endpoint instanceof DefaultEndpoint) {
            // let the rest endpoint configure itself
            DefaultEndpoint de = (DefaultEndpoint) endpoint;
            de.setProperties(endpoint, params);
        }

        // if there is a host then we should use this hardcoded host instead of any Header that may have an existing
        // Host header from some other HTTP input, and if so then lets remove it
        return new RestSwaggerProducer(endpoint.createAsyncProducer(), hasHost);
    }

    String determineBasePath(final Document swagger) {
        if (isNotEmpty(basePath)) {
            return basePath;
        }

        final String componentBasePath = component().getBasePath();
        if (isNotEmpty(componentBasePath)) {
            return componentBasePath;
        }

        final String specificationBasePath = "";// TODO swagger.getBasePath();
        if (isNotEmpty(specificationBasePath)) {
            return specificationBasePath;
        }

        final CamelContext camelContext = getCamelContext();
        final RestConfiguration specificConfiguration = camelContext.getRestConfiguration(assignedComponentName, false);
        if (specificConfiguration != null && isNotEmpty(specificConfiguration.getContextPath())) {
            return specificConfiguration.getContextPath();
        }

        final RestConfiguration restConfiguration = camelContext.getRestConfiguration("rest-swagger", true);
        final String restConfigurationBasePath = restConfiguration.getContextPath();
        if (isNotEmpty(restConfigurationBasePath)) {
            return restConfigurationBasePath;
        }

        return RestSwaggerComponent.DEFAULT_BASE_PATH;
    }

    String determineComponentName() {
        return Optional.ofNullable(componentName).orElse(component().getComponentName());
    }

    Map<String, Object> determineEndpointParameters(final Document swagger, final OasOperation operation) {
        final Map<String, Object> parameters = new HashMap<>();

        final String componentName = determineComponentName();
        if (componentName != null) {
            parameters.put("producerComponentName", componentName);
        }

        final String host = determineHost(swagger);
        if (host != null) {
            parameters.put("host", host);
        }

        final RestSwaggerComponent component = component();

        // what we consume is what the API defined by Swagger specification
        // produces
        List<String> specificationLevelConsumers = new ArrayList<String>();
        if (swagger instanceof Oas20Document) {
            specificationLevelConsumers = ((Oas20Document)swagger).produces;
        } 
        List<String> operationLevelConsumers = new ArrayList<String>();
        if (operation instanceof Oas20Operation) {
            operationLevelConsumers = ((Oas20Operation)operation).produces;
        } else if (operation instanceof Oas30Operation) {
            Oas30Operation oas30Operation = (Oas30Operation)operation;
            for (OasResponse response : oas30Operation.responses.getResponses()) {
                Oas30Response oas30Response = (Oas30Response)response;
                for (String ct : oas30Response.content.keySet()) {
                    operationLevelConsumers.add(ct);
                }
            }
            
        }
        final String determinedConsumes = determineOption(specificationLevelConsumers, operationLevelConsumers,
            component.getConsumes(), consumes);

        if (isNotEmpty(determinedConsumes)) {
            parameters.put("consumes", determinedConsumes);
        }

        // what we produce is what the API defined by Swagger specification
        // consumes
        
        List<String> specificationLevelProducers = new ArrayList<String>();
        if (swagger instanceof Oas20Document) {
            specificationLevelProducers = ((Oas20Document)swagger).consumes;
        } 
        List<String> operationLevelProducers = new ArrayList<String>();
        if (operation instanceof Oas20Operation) {
            operationLevelProducers = ((Oas20Operation)operation).consumes;
        } else if (operation instanceof Oas30Operation) {
            Oas30Operation oas30Operation = (Oas30Operation)operation;
            for (String ct : oas30Operation.requestBody.content.keySet()) {
                operationLevelProducers.add(ct);
            }
        }
        
        final String determinedProducers = determineOption(specificationLevelProducers, operationLevelProducers,
            component.getProduces(), produces);

        if (isNotEmpty(determinedProducers)) {
            parameters.put("produces", determinedProducers);
        }

        final String queryParameters = determineQueryParameters(swagger, operation).map(this::queryParameter)
            .collect(Collectors.joining("&"));
        if (isNotEmpty(queryParameters)) {
            parameters.put("queryParameters", queryParameters);
        }

        // pass properties that might be applied if the delegate component is
        // created, i.e. if it's not
        // present in the Camel Context already
        final Map<String, Object> componentParameters = new HashMap<>();

        if (component.isUseGlobalSslContextParameters()) {
            // by default it's false
            componentParameters.put("useGlobalSslContextParameters", component.isUseGlobalSslContextParameters());
        }
        if (component.getSslContextParameters() != null) {
            componentParameters.put("sslContextParameters", component.getSslContextParameters());
        }

        if (!componentParameters.isEmpty()) {
            final Map<Object, Object> nestedParameters = new HashMap<>();
            nestedParameters.put("component", componentParameters);

            // we're trying to set RestEndpoint.parameters['component']
            parameters.put("parameters", nestedParameters);
        }

        return parameters;
    }

    String determineHost(final Document swagger) {
        if (isNotEmpty(host)) {
            return host;
        }

        final String componentHost = component().getHost();
        if (isNotEmpty(componentHost)) {
            return componentHost;
        }

        
        //TODO
        //In OpenApi 3.0, scheme/host are in servers url section
        //But there could be many servers url(like one for production and one for test)
        //So which one could be used become uncertain.
        /*final String swaggerScheme = pickBestScheme(specificationUri.getScheme(), swagger.getSchemes());
        final String swaggerHost = swagger.getHost();

        if (isNotEmpty(swaggerScheme) && isNotEmpty(swaggerHost)) {
            return swaggerScheme + "://" + swaggerHost;
        }*/

        final CamelContext camelContext = getCamelContext();

        final RestConfiguration specificRestConfiguration = camelContext.getRestConfiguration(assignedComponentName,
            false);
        final String specificConfigurationHost = hostFrom(specificRestConfiguration);
        if (specificConfigurationHost != null) {
            return specificConfigurationHost;
        }

        final RestConfiguration componentRestConfiguration = camelContext.getRestConfiguration("rest-swagger", false);
        final String componentConfigurationHost = hostFrom(componentRestConfiguration);
        if (componentConfigurationHost != null) {
            return componentConfigurationHost;
        }

        final RestConfiguration globalRestConfiguration = camelContext.getRestConfiguration();
        final String globalConfigurationHost = hostFrom(globalRestConfiguration);
        if (globalConfigurationHost != null) {
            return globalConfigurationHost;
        }

        final String specificationScheme = specificationUri.getScheme();
        if (specificationUri.isAbsolute() && specificationScheme.toLowerCase().startsWith("http")) {
            try {
                return new URI(specificationUri.getScheme(), specificationUri.getUserInfo(), specificationUri.getHost(),
                    specificationUri.getPort(), null, null, null).toString();
            } catch (final URISyntaxException e) {
                throw new IllegalStateException("Unable to create a new URI from: " + specificationUri, e);
            }
        }

        final boolean areTheSame = "rest-swagger".equals(assignedComponentName);

        throw new IllegalStateException("Unable to determine destination host for requests. The Swagger specification"
            + " does not specify `scheme` and `host` parameters, the specification URI is not absolute with `http` or"
            + " `https` scheme, and no RestConfigurations configured with `scheme`, `host` and `port` were found for `"
            + (areTheSame ? "rest-swagger` component" : assignedComponentName + "` or `rest-swagger` components")
            + " and there is no global RestConfiguration with those properties");
    }

    String literalPathParameterValue(final OasParameter parameter) {
        final String name = parameter.getName();

        final String valueStr = String.valueOf(parameters.get(name));
        final String encoded = UnsafeUriCharactersEncoder.encode(valueStr);

        return encoded;
    }

    String literalQueryParameterValue(final OasParameter parameter) {
        final String name = parameter.getName();

        final String valueStr = String.valueOf(parameters.get(name));
        final String encoded = UnsafeUriCharactersEncoder.encode(valueStr);

        return name + "=" + encoded;
    }

    String queryParameter(final OasParameter parameter) {
        final String name = parameter.getName();
        if (ObjectHelper.isEmpty(name)) {
            return "";
        }

        if (parameters.containsKey(name)) {
            return literalQueryParameterValue(parameter);
        }

        return queryParameterExpression(parameter);
    }

    String resolveUri(final String uriTemplate, final Map<String, OasParameter> pathParameters) {
        if (pathParameters.isEmpty()) {
            return uriTemplate;
        }

        int start = uriTemplate.indexOf('{');

        if (start == -1) {
            return uriTemplate;
        }

        int pos = 0;
        final StringBuilder resolved = new StringBuilder(uriTemplate.length() * 2);
        while (start != -1) {
            resolved.append(uriTemplate.substring(pos, start));

            final int end = uriTemplate.indexOf('}', start);

            final String name = uriTemplate.substring(start + 1, end);

            if (parameters.containsKey(name)) {
                final OasParameter parameter = pathParameters.get(name);
                final Object value = literalPathParameterValue(parameter);
                resolved.append(value);
            } else {
                resolved.append('{').append(name).append('}');
            }

            pos = end + 1;
            start = uriTemplate.indexOf('{', pos);
        }

        if (pos < uriTemplate.length()) {
            resolved.append(uriTemplate.substring(pos));
        }

        return resolved.toString();
    }

    static String determineOption(final List<String> specificationLevel, final List<String> operationLevel,
        final String componentLevel, final String endpointLevel) {
        if (isNotEmpty(endpointLevel)) {
            return endpointLevel;
        }

        if (isNotEmpty(componentLevel)) {
            return componentLevel;
        }

        if (operationLevel != null && !operationLevel.isEmpty()) {
            return String.join(", ", operationLevel);
        }

        if (specificationLevel != null && !specificationLevel.isEmpty()) {
            return String.join(", ", specificationLevel);
        }

        return null;
    }

    static Stream<OasParameter> determineQueryParameters(final Document swagger, final OasOperation operation) {
        final List<SecurityRequirement> securityRequirements = operation.security;
        final List<OasParameter> apiKeyQueryParameters = new ArrayList<>();
        if (securityRequirements != null) {
            if (swagger instanceof Oas20Document) {
                Oas20Document oas20Document = (Oas20Document)swagger;
                Oas20SecurityDefinitions securityDefinitions = oas20Document.securityDefinitions;
                
                for (final SecurityRequirement securityRequirement : securityRequirements) {
                    for (final String securityRequirementName : securityRequirement.getSecurityRequirementNames()) {
                        final Oas20SecurityScheme securitySchemeDefinition = securityDefinitions
                            .getSecurityScheme(securityRequirementName);
                        if (securitySchemeDefinition.in.equals("query")) {
                            Oas20Parameter securityParameter = new Oas20Parameter(securitySchemeDefinition.name);
                            securityParameter.required = true;
                            securityParameter.type = "string";
                            securityParameter.description = securitySchemeDefinition.description;
                            apiKeyQueryParameters.add(securityParameter);
                        }
                        
                    }
                }
            } else if (swagger instanceof Oas30Document) {
                Oas30Document oas30Document = (Oas30Document)swagger;
                for (final SecurityRequirement securityRequirement : securityRequirements) {
                    for (final String securityRequirementName : securityRequirement.getSecurityRequirementNames()) {
                        final Oas30SecurityScheme securitySchemeDefinition = oas30Document.components
                            .getSecurityScheme(securityRequirementName);
                        if (securitySchemeDefinition.in.equals("query")) {
                            Oas20Parameter securityParameter = new Oas20Parameter(securitySchemeDefinition.name);
                            securityParameter.required = true;
                            securityParameter.type = "string";
                            securityParameter.description = securitySchemeDefinition.description;
                            apiKeyQueryParameters.add(securityParameter);
                        }
                        
                    }
                }
            } else {
                throw new IllegalStateException("We only support OpenApi 2.0 or 3.0 document here");
            }
            
        }

        return Stream.concat(apiKeyQueryParameters.stream(),
            operation.getParameters().stream().filter(p -> "query".equals(p.in)));
    }

    static String hostFrom(final RestConfiguration restConfiguration) {
        if (restConfiguration == null) {
            return null;
        }

        final String scheme = restConfiguration.getScheme();
        final String host = restConfiguration.getHost();
        final int port = restConfiguration.getPort();

        if (scheme == null || host == null) {
            return null;
        }

        final StringBuilder answer = new StringBuilder(scheme).append("://").append(host);
        if (port > 0 && !("http".equalsIgnoreCase(scheme) && port == 80)
            && !("https".equalsIgnoreCase(scheme) && port == 443)) {
            answer.append(':').append(port);
        }

        return answer.toString();
    }

    /**
     * Loads the Swagger definition model from the given path. Tries to resolve
     * the resource using Camel's resource loading support, if it fails uses
     * Swagger's resource loading support instead.
     *
     * @param uri URI of the specification
     * @param camelContext context to use
     * @return the specification
     * @throws IOException
     */
    static Document loadSpecificationFrom(final CamelContext camelContext, final URI uri) throws IOException {
        final ObjectMapper mapper = new ObjectMapper();

      
        final String uriAsString = uri.toString();

        try (InputStream stream = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, uriAsString)) {
            final JsonNode node = mapper.readTree(stream);

            return Library.readDocument(node);
        } catch (final Exception e) {
            
            throw new IllegalArgumentException("The given Swagger specification could not be loaded from `" + uri
                + "`. Tried loading using Camel's resource resolution and using Swagger's own resource resolution."
                + " Swagger tends to swallow exceptions while parsing, try specifying Java system property `debugParser`"
                + " (e.g. `-DdebugParser=true`), the exception that occurred when loading using Camel's resource"
                + " loader follows", e);
        }
    }

    static String pickBestScheme(final String specificationScheme, final List<String> schemes) {
        if (schemes != null && !schemes.isEmpty()) {
            if (schemes.contains("https")) {
                return "https";
            }

            if (schemes.contains("http")) {
                return "http";
            }
        }

        if (specificationScheme != null) {
            return specificationScheme;
        }

        // there is no support for WebSocket (Scheme.WS, Scheme.WSS)

        return null;
    }

    static String queryParameterExpression(final OasParameter parameter) {
        final String name = parameter.getName();

        final StringBuilder expression = new StringBuilder(name).append("={").append(name);
        if (!parameter.required) {
            expression.append('?');
        }
        expression.append('}');

        return expression.toString();
    }
    
    enum HttpMethod {
        POST,
        GET,
        PUT,
        PATCH,
        DELETE,
        HEAD,
        OPTIONS
    }

}
