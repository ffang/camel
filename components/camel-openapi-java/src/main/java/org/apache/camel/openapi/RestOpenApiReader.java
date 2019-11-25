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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.invoke.MethodHandles.publicLookup;

import io.apicurio.datamodels.core.models.Extension;
import io.apicurio.datamodels.core.models.common.SecurityRequirement;
import io.apicurio.datamodels.openapi.models.OasOperation;
import io.apicurio.datamodels.openapi.models.OasParameter;
import io.apicurio.datamodels.openapi.models.OasPathItem;
import io.apicurio.datamodels.openapi.v2.models.Oas20Definitions;
import io.apicurio.datamodels.openapi.v2.models.Oas20Document;
import io.apicurio.datamodels.openapi.v2.models.Oas20Header;
import io.apicurio.datamodels.openapi.v2.models.Oas20Items;
import io.apicurio.datamodels.openapi.v2.models.Oas20Operation;
import io.apicurio.datamodels.openapi.v2.models.Oas20Parameter;
import io.apicurio.datamodels.openapi.v2.models.Oas20Response;
import io.apicurio.datamodels.openapi.v2.models.Oas20Schema;
import io.apicurio.datamodels.openapi.v2.models.Oas20SchemaDefinition;
import io.apicurio.datamodels.openapi.v2.models.Oas20SecurityScheme;

import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestOperationParamDefinition;
import org.apache.camel.model.rest.RestOperationResponseHeaderDefinition;
import org.apache.camel.model.rest.RestOperationResponseMsgDefinition;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.model.rest.RestPropertyDefinition;
import org.apache.camel.model.rest.RestSecuritiesDefinition;
import org.apache.camel.model.rest.RestSecurityApiKey;
import org.apache.camel.model.rest.RestSecurityBasicAuth;
import org.apache.camel.model.rest.RestSecurityDefinition;
import org.apache.camel.model.rest.RestSecurityOAuth2;
import org.apache.camel.model.rest.SecurityDefinition;
import org.apache.camel.model.rest.VerbDefinition;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.util.FileUtil;

/**
 * A Camel REST-DSL swagger reader that parse the rest-dsl into a swagger model representation.
 * <p/>
 * This reader supports the <a href="http://swagger.io/specification/">Swagger Specification 2.0</a>
 */
public class RestOpenApiReader {

    /**
     * Read the REST-DSL definition's and parse that as a Swagger model representation
     *
     * @param rests             the rest-dsl
     * @param route             optional route path to filter the rest-dsl to only include from the chose route
     * @param config            the swagger configuration
     * @param classResolver     class resolver to use
     * @return the swagger model
     * @throws ClassNotFoundException 
     */
    public Oas20Document read(List<RestDefinition> rests, String route, BeanConfig config, String camelContextId, ClassResolver classResolver) throws ClassNotFoundException {
        Oas20Document swagger = new Oas20Document();

        for (RestDefinition rest : rests) {

            if (org.apache.camel.util.ObjectHelper.isNotEmpty(route) && !route.equals("/")) {
                // filter by route
                if (!rest.getPath().equals(route)) {
                    continue;
                }
            }

            parse(swagger, rest, camelContextId, classResolver);
        }

        // configure before returning
        swagger = config.configure(swagger);
        return swagger;
    }

    private void parse(Oas20Document swagger, RestDefinition rest, String camelContextId, ClassResolver classResolver) throws ClassNotFoundException {
        List<VerbDefinition> verbs = new ArrayList<>(rest.getVerbs());
        // must sort the verbs by uri so we group them together when an uri has multiple operations
        Collections.sort(verbs, new VerbOrdering());

        // we need to group the operations within the same tag, so use the path as default if not configured
        String pathAsTag = rest.getTag() != null ? rest.getTag() : FileUtil.stripLeadingSeparator(rest.getPath());
        String summary = rest.getDescriptionText();

        if (org.apache.camel.util.ObjectHelper.isNotEmpty(pathAsTag)) {
            // add rest as tag
            swagger.addTag(pathAsTag, summary);
        }

        // setup security definitions
        RestSecuritiesDefinition sd = rest.getSecurityDefinitions();
        if (swagger.securityDefinitions == null) {
            swagger.securityDefinitions = swagger.createSecurityDefinitions();
        }
        if (sd != null) {
            for (RestSecurityDefinition def : sd.getSecurityDefinitions()) {
                if (def instanceof RestSecurityBasicAuth) {
                    Oas20SecurityScheme auth = swagger.securityDefinitions.createSecurityScheme(def.getKey());
                    auth.type = "basicAuth";
                    auth.description = def.getDescription();
                    swagger.securityDefinitions.addSecurityScheme("BasicAuth", auth);
                } else if (def instanceof RestSecurityApiKey) {
                    RestSecurityApiKey rs = (RestSecurityApiKey) def;
                    Oas20SecurityScheme auth = swagger.securityDefinitions.createSecurityScheme(def.getKey());
                    auth.type = "apiKey";
                    auth.description = rs.getDescription();
                    auth.name = rs.getName();
                    if (rs.getInHeader() != null && rs.getInHeader()) {
                        auth.in = "header";
                    } else {
                        auth.in = "query";
                    }
                    swagger.securityDefinitions.addSecurityScheme(def.getKey(), auth);
                } else if (def instanceof RestSecurityOAuth2) {
                    RestSecurityOAuth2 rs = (RestSecurityOAuth2) def;
                    
                    Oas20SecurityScheme auth = swagger.securityDefinitions.createSecurityScheme(def.getKey());
                    auth.type = "oauth2";
                    auth.description = rs.getDescription();
                    String flow = rs.getFlow();
                    if (flow == null) {
                        if (rs.getAuthorizationUrl() != null && rs.getTokenUrl() != null) {
                            flow = "accessCode";
                        } else if (rs.getTokenUrl() == null && rs.getAuthorizationUrl() != null) {
                            flow = "implicit";
                        }
                    }
                    auth.flow = flow;
                    auth.authorizationUrl = rs.getAuthorizationUrl();
                    auth.tokenUrl = rs.getTokenUrl();
                    for (RestPropertyDefinition scope : rs.getScopes()) {
                        auth.scopes.addScope(scope.getKey(), scope.getValue());
                    }
                    if (swagger.securityDefinitions == null) {
                        swagger.securityDefinitions = swagger.createSecurityDefinitions();
                    }
                    swagger.securityDefinitions.addSecurityScheme(def.getKey(), auth);
                }
            }
        }

        // gather all types in use
        Set<String> types = new LinkedHashSet<>();
        for (VerbDefinition verb : verbs) {

            // check if the Verb Definition must be excluded from documentation
            Boolean apiDocs;
            if (verb.getApiDocs() != null) {
                apiDocs = verb.getApiDocs();
            } else {
                // fallback to option on rest
                apiDocs = rest.getApiDocs();
            }
            if (apiDocs != null && !apiDocs) {
                continue;
            }

            String type = verb.getType();
            if (org.apache.camel.util.ObjectHelper.isNotEmpty(type)) {
                if (type.endsWith("[]")) {
                    type = type.substring(0, type.length() - 2);
                }
                types.add(type);
            }
            type = verb.getOutType();
            if (org.apache.camel.util.ObjectHelper.isNotEmpty(type)) {
                if (type.endsWith("[]")) {
                    type = type.substring(0, type.length() - 2);
                }
                types.add(type);
            }
            // there can also be types in response messages
            if (verb.getResponseMsgs() != null) {
                for (RestOperationResponseMsgDefinition def : verb.getResponseMsgs()) {
                    type = def.getResponseModel();
                    if (org.apache.camel.util.ObjectHelper.isNotEmpty(type)) {
                        if (type.endsWith("[]")) {
                            type = type.substring(0, type.length() - 2);
                        }
                        types.add(type);
                    }
                }
            }
        }

        // use annotation scanner to find models (annotated classes)
        for (String type : types) {
            Class<?> clazz = classResolver.resolveMandatoryClass(type);
            appendModels(clazz, swagger);
        }

        doParseVerbs(swagger, rest, camelContextId, verbs, pathAsTag);
    }

    private void doParseVerbs(Oas20Document swagger, RestDefinition rest, String camelContextId, List<VerbDefinition> verbs, String pathAsTag) {
        // used during gathering of apis
        
        String basePath = rest.getPath();

        for (VerbDefinition verb : verbs) {
            // check if the Verb Definition must be excluded from documentation
            Boolean apiDocs;
            if (verb.getApiDocs() != null) {
                apiDocs = verb.getApiDocs();
            } else {
                // fallback to option on rest
                apiDocs = rest.getApiDocs();
            }
            if (apiDocs != null && !apiDocs) {
                continue;
            }
            // the method must be in lower case
            String method = verb.asVerb().toLowerCase(Locale.US);
            // operation path is a key
            String opPath = OpenApiHelper.buildUrl(basePath, verb.getUri());

            
            if (swagger.paths == null) {
                swagger.paths = swagger.createPaths();
            }
            OasPathItem path = swagger.paths.getPathItem(opPath);
            if (path == null) {
                path = swagger.paths.createPathItem(opPath);
            }
            
            Oas20Operation op = (Oas20Operation)path.createOperation(method);
            if (org.apache.camel.util.ObjectHelper.isNotEmpty(pathAsTag)) {
                // group in the same tag
                if (op.tags == null) {
                    op.tags = new ArrayList<String>();
                }
                op.tags.add(pathAsTag);
            }

            final String routeId = verb.getRouteId();
            final String operationId = Optional.ofNullable(rest.getId()).orElse(routeId);
            op.operationId = operationId;
            

            // add id as vendor extensions
            Extension extension = op.createExtension();
            extension.name = "x-camelContextId";
            extension.value = camelContextId;
            op.addExtension(extension.name, extension);
            extension = op.createExtension();
            extension.name = "x-routeId";
            extension.value = routeId;
            op.addExtension(extension.name, extension);
            path = setPathOperation(path, op, method);

            String consumes = verb.getConsumes() != null ? verb.getConsumes() : rest.getConsumes();
            if (consumes != null) {
                String[] parts = consumes.split(",");
                if (op.consumes == null) {
                    op.consumes = new ArrayList<String>();
                }
                for (String part : parts) {
                    op.consumes.add(part);
                }
            }

            String produces = verb.getProduces() != null ? verb.getProduces() : rest.getProduces();
            if (produces != null) {
                String[] parts = produces.split(",");
                if (op.produces == null) {
                    op.produces = new ArrayList<String>();
                }
                for (String part : parts) {
                    op.produces.add(part);
                }
            }

            if (verb.getDescriptionText() != null) {
                op.summary = verb.getDescriptionText();
            }

            // security
            for (SecurityDefinition sd : verb.getSecurity()) {
                List<String> scopes = new ArrayList<>();
                if (sd.getScopes() != null) {
                    for (String scope : ObjectHelper.createIterable(sd.getScopes())) {
                        scopes.add(scope);
                    }
                }
                SecurityRequirement securityRequirement = op.createSecurityRequirement();
                securityRequirement.addSecurityRequirementItem(sd.getKey(), scopes);
                op.addSecurityRequirement(securityRequirement);
            }
            
            

            for (RestOperationParamDefinition param : verb.getParams()) {
                OasParameter parameter = null;
                if (param.getType().equals(RestParamType.body)) {
                    parameter = op.createParameter();
                    parameter.in = "body";
                } else if (param.getType().equals(RestParamType.formData)) {
                    parameter = op.createParameter();
                    parameter.in = "formData";
                } else if (param.getType().equals(RestParamType.header)) {
                    parameter = op.createParameter();
                    parameter.in = "header";
                } else if (param.getType().equals(RestParamType.path)) {
                    parameter = op.createParameter();
                    parameter.in = "path";
                } else if (param.getType().equals(RestParamType.query)) {
                    parameter = op.createParameter();
                    parameter.in = "query";
                }

                if (parameter != null) {
                    parameter.name = param.getName();
                    if (org.apache.camel.util.ObjectHelper.isNotEmpty(param.getDescription())) {
                        parameter.description = param.getDescription();
                    }
                    parameter.required = param.getRequired();

                    // set type on parameter
                    if (!parameter.in.equals("body")) {
                        Oas20Parameter serializableParameter = (Oas20Parameter)parameter;

                        final boolean isArray = param.getDataType().equalsIgnoreCase("array");
                        final List<String> allowableValues = param.getAllowableValues();
                        final boolean hasAllowableValues = allowableValues != null && !allowableValues.isEmpty();
                        if (param.getDataType() != null) {
                            serializableParameter.type = param.getDataType();
                            if (param.getDataFormat() != null) {
                                serializableParameter.format = param.getDataFormat();
                            }
                            if (isArray) {
                                if (param.getArrayType() != null) {
                                    if (param.getArrayType().equalsIgnoreCase("string")) {
                                        defineItems(serializableParameter, allowableValues, new Oas20Items(), String.class);
                                    }
                                    if (param.getArrayType().equalsIgnoreCase("int") || param.getArrayType().equalsIgnoreCase("integer")) {
                                        defineItems(serializableParameter, allowableValues, new Oas20Items(), Integer.class);
                                    }
                                    if (param.getArrayType().equalsIgnoreCase("long")) {
                                        defineItems(serializableParameter, allowableValues, new Oas20Items(), Long.class);
                                    }
                                    if (param.getArrayType().equalsIgnoreCase("float")) {
                                        defineItems(serializableParameter, allowableValues, new Oas20Items(), Float.class);
                                    }
                                    if (param.getArrayType().equalsIgnoreCase("double")) {
                                        defineItems(serializableParameter, allowableValues, new Oas20Items(), Double.class);
                                    }
                                    if (param.getArrayType().equalsIgnoreCase("boolean")) {
                                        defineItems(serializableParameter, allowableValues, new Oas20Items(), Boolean.class);
                                    }
                                }
                            }
                        }
                        if (param.getCollectionFormat() != null) {
                            serializableParameter.collectionFormat = param.getCollectionFormat().name();
                        }
                        if (hasAllowableValues && !isArray) {
                            serializableParameter.enum_ = allowableValues;
                        }
                    }

                    if (!parameter.in.equals("body")) {
                        Oas20Parameter qp = (Oas20Parameter)parameter;
                        // set default value on parameter
                        if (org.apache.camel.util.ObjectHelper.isNotEmpty(param.getDefaultValue())) {
                            qp.default_ = param.getDefaultValue();
                        }
                        // add examples
                        if (param.getExamples() != null && param.getExamples().size() >= 1) {
                            // we can only set one example on the parameter
                            Extension exampleExtension = qp.createExtension();
                            boolean emptyKey = param.getExamples().get(0).getKey().length() == 0;
                            if (emptyKey) {
                                exampleExtension.name = "x-example";
                                exampleExtension.value = param.getExamples().get(0).getValue();
                                qp.addExtension("x-example", exampleExtension);
                            } else {
                                Map<String, String> exampleValue = new HashMap<String, String>();
                                exampleValue.put(param.getExamples().get(0).getKey(), param.getExamples().get(0).getValue());
                                exampleExtension.name = "x-examples";
                                exampleExtension.value = exampleValue;
                                qp.addExtension("x-examples", exampleExtension);
                            }
                        }
                    }

                    // set schema on body parameter
                    if (parameter.in.equals("body")) {
                        Oas20Parameter bp = (Oas20Parameter) parameter;

                        String type = param.getDataType() != null ? param.getDataType() : verb.getType();
                        if (type != null) {
                            if (type.endsWith("[]")) {
                                type = type.substring(0, type.length() - 2);
                                    
                                    Oas20Schema arrayModel = (Oas20Schema)bp.createSchema();
                                    arrayModel = modelTypeAsProperty(type, swagger, arrayModel);
                                    bp.schema = arrayModel;
                                
                            } else {
                                String ref = modelTypeAsRef(type, swagger);
                                if (ref != null) {
                                    Oas20Schema refModel = (Oas20Schema)bp.createSchema();
                                    refModel.$ref = "#/definitions/" + ref;
                                    bp.schema = refModel;
                                } else {
                                    Oas20Schema model = (Oas20Schema)bp.createSchema();
                                    model = modelTypeAsProperty(type, swagger, model);
                                       
                                    bp.schema = model;
                                    
                                }
                            }
                        }
                        // add examples
                        if (param.getExamples() != null) {
                            for (RestPropertyDefinition prop : param.getExamples()) {
                                Extension exampleExtension = bp.createExtension();
                                boolean emptyKey = param.getExamples().get(0).getKey().length() == 0;
                                if (emptyKey) {
                                    exampleExtension.name = "x-example";
                                    exampleExtension.value = param.getExamples().get(0).getValue();
                                    bp.addExtension("x-example", exampleExtension);
                                } else {
                                    Map<String, String> exampleValue = new HashMap<String, String>();
                                    exampleValue.put(param.getExamples().get(0).getKey(), param.getExamples().get(0).getValue());
                                    exampleExtension.name = "x-examples";
                                    exampleExtension.value = exampleValue;
                                    bp.addExtension("x-examples", exampleExtension);
                                }
                            }
                        }
                    }

                    op.addParameter(parameter);
                }
            }

            // clear parameters if its empty
            if (op.getParameters() != null && op.getParameters().isEmpty()) {
                op.parameters.clear();
            }

            // if we have an out type then set that as response message
            if (verb.getOutType() != null) {
                if (op.responses == null) {
                    op.responses = op.createResponses();
                }
                Oas20Response response = (Oas20Response)op.responses.createResponse("200");
                Oas20Schema model = response.createSchema();
                model = modelTypeAsProperty(verb.getOutType(), swagger, model);
                
                response.schema = model;
                response.description = "Output type";
                op.responses.addResponse("200", response);
            }

            // enrich with configured response messages from the rest-dsl
            doParseResponseMessages(swagger, verb, op);

            // add path
            swagger.paths.addPathItem(opPath, path);
            
        }
    }

    private OasPathItem setPathOperation(OasPathItem path, OasOperation operation, String method) {
        if (method.equals("post")) {
            path.post = operation;
        } else if (method.equals("get")) {
            path.get = operation;
        } else if (method.equals("put")) {
            path.put = operation;
        } else if (method.equals("patch")) {
            path.patch = operation;
        } else if (method.equals("delete")) {
            path.delete = operation;
        } else if (method.equals("head")) {
            path.head = operation;
        } else if (method.equals("options")) {
            path.options = operation;
        }
        return path;
    }
    
    private static void defineItems(final Oas20Parameter serializableParameter,
        final List<String> allowableValues, final Oas20Items items, final Class<?> type) {
        serializableParameter.items = items;
        if (allowableValues != null && !allowableValues.isEmpty()) {
            if (String.class.equals(type)) {
                items.enum_ = allowableValues;
            } else {
                convertAndSetItemsEnum(items, allowableValues, type);
            }
        }
    }

    private static void convertAndSetItemsEnum(final Oas20Items items, final List<String> allowableValues, final Class<?> type) {
        try {
            final MethodHandle valueOf = publicLookup().findStatic(type, "valueOf", MethodType.methodType(type, String.class));
            final MethodHandle setEnum = publicLookup().bind(items, "setEnum",
                MethodType.methodType(void.class, List.class));
            final List<?> values = allowableValues.stream().map(v -> {
                try {
                    return valueOf.invoke(v);
                } catch (Throwable e) {
                    if (e instanceof RuntimeException) {
                        throw (RuntimeException) e;
                    }

                    throw new IllegalStateException(e);
                }
            }).collect(Collectors.toList());
            setEnum.invoke(values);
        } catch (Throwable e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }

            throw new IllegalStateException(e);
        }
    }

    private void doParseResponseMessages(Oas20Document swagger, VerbDefinition verb, Oas20Operation op) {
        if (op.responses == null) {
            op.responses = op.createResponses();
        }
        for (RestOperationResponseMsgDefinition msg : verb.getResponseMsgs()) {
            Oas20Response response = null;
            
            if (op.responses != null && op.responses.getResponses() != null) {
                response = (Oas20Response)op.responses.getResponse(msg.getCode());
            }
            if (response == null) {
                response = (Oas20Response)op.responses.createResponse(msg.getCode());
                op.responses.addResponse(msg.getCode(), response);
            }
            if (org.apache.camel.util.ObjectHelper.isNotEmpty(msg.getResponseModel())) {
                Oas20Schema model = response.createSchema();
                model = modelTypeAsProperty(msg.getResponseModel(), swagger, model);
                
                response.schema = model;
            }
            if (org.apache.camel.util.ObjectHelper.isNotEmpty(msg.getMessage())) {
                response.description = msg.getMessage();
            }

            // add headers
            if (msg.getHeaders() != null) {
                for (RestOperationResponseHeaderDefinition header : msg.getHeaders()) {
                    String name = header.getName();
                    String type = header.getDataType();
                    String format = header.getDataFormat();
                    if (response.headers == null) {
                        response.headers = response.createHeaders();
                    }
                    if ("string".equals(type)) {
                        Oas20Header sp = response.headers.createHeader(name);
                        sp.type = "string";
                        if (format != null) {
                            sp.format = format;
                        }
                        sp.description = header.getDescription();
                        if (header.getAllowableValues() != null) {
                            sp.enum_ = header.getAllowableValues();
                        }
                        // add example
                        if (header.getExample() != null) {
                            Extension exampleExtension = sp.createExtension();
                            exampleExtension.name = "x-example";
                            exampleExtension.value = header.getExample();
                            sp.getExtensions().add(exampleExtension);
                                
                        }
                        response.headers.addHeader(name, sp);
                    } else if ("int".equals(type) || "integer".equals(type)) {
                        Oas20Header ip = response.headers.createHeader(name);
                        ip.type = "integer";
                        if (format != null) {
                            ip.format = format;
                        }
                        ip.description = header.getDescription();
                            
                        List<String> values;
                        if (!header.getAllowableValues().isEmpty()) {
                            values = new ArrayList<>();
                            for (String text : header.getAllowableValues()) {
                                values.add(text);
                            }
                            ip.enum_ = values;
                        }
                        // add example
                        if (header.getExample() != null) {
                            Extension exampleExtension = ip.createExtension();
                            exampleExtension.name = "x-example";
                            exampleExtension.value = header.getExample();
                            ip.getExtensions().add(exampleExtension);
                        }
                        response.headers.addHeader(name, ip);
                    } else if ("long".equals(type)) {
                        Oas20Header lp = response.headers.createHeader(name);
                        lp.type = type;
                        if (format != null) {
                            lp.format = format;
                        }
                        lp.description = header.getDescription();
                        
                        List<String> values;
                        if (!header.getAllowableValues().isEmpty()) {
                            values = new ArrayList<>();
                            for (String text : header.getAllowableValues()) {
                                values.add(text);
                            }
                            lp.enum_ = values;
                        }
                        // add example
                        if (header.getExample() != null) {
                            Extension exampleExtension = lp.createExtension();
                            exampleExtension.name = "x-example";
                            exampleExtension.value = header.getExample();
                            lp.getExtensions().add(exampleExtension);
                        }
                        
                        response.headers.addHeader(name, lp);
                    } else if ("float".equals(type)) {
                        Oas20Header fp = response.headers.createHeader(name);
                        fp.type = "float";
                        if (format != null) {
                            fp.format = format;
                        }
                        fp.description = header.getDescription();
                        
                        List<String> values;
                        if (!header.getAllowableValues().isEmpty()) {
                            values = new ArrayList<>();
                            for (String text : header.getAllowableValues()) {
                                values.add(text);
                            }
                            fp.enum_ = values;
                        }
                        // add example
                        if (header.getExample() != null) {
                            Extension exampleExtension = fp.createExtension();
                            exampleExtension.name = "x-example";
                            exampleExtension.value = header.getExample();
                            fp.getExtensions().add(exampleExtension);
                        }
                        response.headers.addHeader(name, fp);
                    } else if ("double".equals(type)) {
                        Oas20Header dp = response.headers.createHeader(name);
                        dp.type = "double";
                        if (format != null) {
                            dp.format = format;
                        }
                        dp.description = header.getDescription();
                        
                        List<String> values;
                        if (!header.getAllowableValues().isEmpty()) {
                            values = new ArrayList<>();
                            for (String text : header.getAllowableValues()) {
                                values.add(text);
                            }
                            dp.enum_ = values;
                        }
                        // add example
                        if (header.getExample() != null) {
                            Extension exampleExtension = dp.createExtension();
                            exampleExtension.name = "x-example";
                            exampleExtension.value = header.getExample();
                            dp.getExtensions().add(exampleExtension);
                        }
                        response.headers.addHeader(name, dp);
                    } else if ("boolean".equals(type)) {
                        Oas20Header bp = response.headers.createHeader(name);
                        bp.type = "boolean";
                        if (format != null) {
                            bp.format = format;
                        }
                        bp.description = header.getDescription();
                        // add example
                        if (header.getExample() != null) {
                            Extension exampleExtension = bp.createExtension();
                            exampleExtension.name = "x-example";
                            exampleExtension.value = header.getExample();
                            bp.getExtensions().add(exampleExtension);
                        }
                        response.headers.addHeader(name, bp);
                    } else if ("array".equals(type)) {
                        Oas20Header ap = response.headers.createHeader(name);
                        
                        if (org.apache.camel.util.ObjectHelper.isNotEmpty(header.getDescription())) {
                            ap.description = header.getDescription();
                        }
                        if (header.getArrayType() != null) {
                            if (header.getArrayType().equalsIgnoreCase("string")) {
                                Oas20Items items = ap.createItems();
                                items.type = "string";
                                ap.items = items;
                            }
                            if (header.getArrayType().equalsIgnoreCase("int") || header.getArrayType().equalsIgnoreCase("integer")) {
                                Oas20Items items = ap.createItems();
                                items.type = "integer";
                                ap.items = items;
                            }
                            if (header.getArrayType().equalsIgnoreCase("long")) {
                                Oas20Items items = ap.createItems();
                                items.type = "long";
                                ap.items = items;
                            }
                            if (header.getArrayType().equalsIgnoreCase("float")) {
                                Oas20Items items = ap.createItems();
                                items.type = "float";
                                ap.items = items;
                            }
                            if (header.getArrayType().equalsIgnoreCase("double")) {
                                Oas20Items items = ap.createItems();
                                items.type = "double";
                                ap.items = items;
                            }
                            if (header.getArrayType().equalsIgnoreCase("boolean")) {
                                Oas20Items items = ap.createItems();
                                items.type = "boolean";
                                ap.items = items;
                            }
                        }
                        // add example
                        if (header.getExample() != null) {
                            Extension exampleExtension = ap.createExtension();
                            exampleExtension.name = "x-example";
                            exampleExtension.value = header.getExample();
                            ap.getExtensions().add(exampleExtension);
                        }
                        response.headers.addHeader(name, ap);
                    }
                }
            }

            // add examples
            if (msg.getExamples() != null) {
                Extension exampleExtension = response.createExtension();
                exampleExtension.name = "examples";
                Map<String, String> examplesValue = new HashMap<String, String>();
                for (RestPropertyDefinition prop : msg.getExamples()) {
                    examplesValue.put(prop.getKey(), prop.getValue());
                    
                }
                exampleExtension.value = examplesValue;
                response.addExtension(exampleExtension.name, exampleExtension);
            }
            
        }

        // must include an empty noop response if none exists
        if (op.responses == null || op.responses.getResponses().isEmpty()) {
            op.responses.addResponse("200", op.responses.createResponse("200"));
        }
    }

    private Oas20SchemaDefinition asModel(String typeName, Oas20Document swagger) {
        boolean array = typeName.endsWith("[]");
        if (array) {
            typeName = typeName.substring(0, typeName.length() - 2);
        }

        if (swagger.definitions != null) {
            for (Oas20SchemaDefinition model : swagger.definitions.getDefinitions()) {
                @SuppressWarnings("rawtypes")
                Map modelType = (Map)model.getExtension("x-className").value;
                
                if (modelType != null && typeName.equals(modelType.get("format"))) {
                    return model;
                }
            }
        }
        return null;
    }
    


    private String modelTypeAsRef(String typeName, Oas20Document swagger) {
        boolean array = typeName.endsWith("[]");
        if (array) {
            typeName = typeName.substring(0, typeName.length() - 2);
        }

        Oas20SchemaDefinition model = asModel(typeName, swagger);
        if (model != null) {
            typeName = model.type;
            return typeName;
        }

        return null;
    }

    private Oas20Schema modelTypeAsProperty(String typeName, Oas20Document swagger, Oas20Schema prop) {
        boolean array = typeName.endsWith("[]");
        if (array) {
            typeName = typeName.substring(0, typeName.length() - 2);
        }

        String ref = modelTypeAsRef(typeName, swagger);

            
        if (ref != null) {
            prop.$ref = "#/definitions/" + ref;
        } else {
            // special for byte arrays
            if (array && ("byte".equals(typeName) || "java.lang.Byte".equals(typeName))) {
                prop.format = "byte";
                prop.type = "number";
                array = false;
            } else if ("string".equalsIgnoreCase(typeName) || "java.lang.String".equals(typeName)) {
                prop.format = "string";
                prop.type = "sting";
            } else if ("int".equals(typeName) || "java.lang.Integer".equals(typeName)) {
                prop.format = "integer";
                prop.type = "number";
            } else if ("long".equals(typeName) || "java.lang.Long".equals(typeName)) {
                prop.format = "long";
                prop.type = "number";
            } else if ("float".equals(typeName) || "java.lang.Float".equals(typeName)) {
                prop.format = "float";
                prop.type = "number";
            } else if ("double".equals(typeName) || "java.lang.Double".equals(typeName)) {
                prop.format = "double";
                prop.type = "number";
            } else if ("boolean".equals(typeName) || "java.lang.Boolean".equals(typeName)) {
                prop.format = "boolean";
                prop.type = "number";
            } else {
                prop.type = "string";
            }
        }

        if (array) {
            Oas20Schema ret = (Oas20Schema)prop.createItemsSchema();
            ret.items = prop;
            ret.type = "array";
            return ret;
        } else {
            return prop;
        }
    }

    /**
     * If the class is annotated with swagger annotations its parsed into a Swagger model representation
     * which is added to swagger
     *
     * @param clazz   the class such as pojo with swagger annotation
     * @param swagger the swagger model
     */
    private void appendModels(Class clazz, Oas20Document swagger) {
        RestModelConverters converters = new RestModelConverters();
        Oas20Definitions models = converters.readClass(swagger, clazz);
        if (models == null) {
            return;
        }
        for (Oas20SchemaDefinition entry : models.getDefinitions()) {

            // favor keeping any existing model that has the vendor extension in the model
            boolean oldExt = false;
            if (swagger.definitions != null && swagger.definitions.getDefinition(entry.getName()) != null) {
                Oas20SchemaDefinition oldModel = swagger.definitions.getDefinition(entry.getName());
                if (oldModel.getExtensions() != null && !oldModel.getExtensions().isEmpty()) {
                    oldExt = oldModel.getExtensions().contains("x-className");
                }
            }

            if (!oldExt) {
                swagger.definitions.addDefinition(entry.getName(), entry);
            }
        }
    }

    /**
     * To sort the rest operations
     */
    private static class VerbOrdering implements Comparator<VerbDefinition> {

        @Override
        public int compare(VerbDefinition a, VerbDefinition b) {

            String u1 = "";
            if (a.getUri() != null) {
                // replace { with _ which comes before a when soring by char
                u1 = a.getUri().replace("{", "_");
            }
            String u2 = "";
            if (b.getUri() != null) {
                // replace { with _ which comes before a when soring by char
                u2 = b.getUri().replace("{", "_");
            }

            int num = u1.compareTo(u2);
            if (num == 0) {
                // same uri, so use http method as sorting
                num = a.asVerb().compareTo(b.asVerb());
            }
            return num;
        }
    }

}
