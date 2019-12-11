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
package org.apache.camel.generator.openapi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.apicurio.datamodels.openapi.models.OasOperation;
import io.apicurio.datamodels.openapi.models.OasParameter;
import io.apicurio.datamodels.openapi.models.OasResponse;
import io.apicurio.datamodels.openapi.v2.models.Oas20Items;
import io.apicurio.datamodels.openapi.v2.models.Oas20Operation;
import io.apicurio.datamodels.openapi.v2.models.Oas20Parameter;
import io.apicurio.datamodels.openapi.v3.models.Oas30Operation;
import io.apicurio.datamodels.openapi.v3.models.Oas30Parameter;
import io.apicurio.datamodels.openapi.v3.models.Oas30Response;
import io.apicurio.datamodels.openapi.v3.models.Oas30Schema;

import org.apache.camel.model.rest.CollectionFormat;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.util.ObjectHelper;

class OperationVisitor<T> {

    private final DestinationGenerator destinationGenerator;

    private final CodeEmitter<T> emitter;

    private final OperationFilter filter;

    private final String path;

    OperationVisitor(final CodeEmitter<T> emitter, final OperationFilter filter, final String path, final DestinationGenerator destinationGenerator) {
        this.emitter = emitter;
        this.filter = filter;
        this.path = path;
        this.destinationGenerator = destinationGenerator;
    }

    List<String> asStringList(final List<?> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        final List<String> stringList = new ArrayList<>();
        values.forEach(v -> stringList.add(String.valueOf(v)));

        return stringList;
    }

    CodeEmitter<T> emit(final OasParameter parameter) {
        emitter.emit("param");
        emit("name", parameter.getName());
        final String parameterType = parameter.in;
        if (ObjectHelper.isNotEmpty(parameterType)) {
            emit("type", RestParamType.valueOf(parameterType));
        }
        if (!parameterType.equals("body")) {
            if (parameter instanceof Oas20Parameter) {
                final Oas20Parameter serializableParameter = (Oas20Parameter)parameter;

                final String dataType = serializableParameter.type;
                emit("dataType", dataType);
                emit("allowableValues", asStringList(serializableParameter.enum_));
                final String collectionFormat = serializableParameter.collectionFormat;
                if (ObjectHelper.isNotEmpty(collectionFormat)) {
                    emit("collectionFormat", CollectionFormat.valueOf(collectionFormat));
                }
                if (ObjectHelper.isNotEmpty(serializableParameter.default_)) {
                    String value = serializableParameter.default_.toString();
                    emit("defaultValue", value);
                }

                final Oas20Items items = serializableParameter.items;
                if ("array".equals(dataType) && items != null) {
                    emit("arrayType", items.type);
                }
            } else if (parameter instanceof Oas30Parameter) {
                final Oas30Parameter serializableParameter = (Oas30Parameter)parameter;
                Oas30Schema schema = (Oas30Schema)serializableParameter.schema;
                if (schema != null) {
                    final String dataType = schema.type;
                    if (ObjectHelper.isNotEmpty(dataType)) {
                        emit("dataType", dataType);
                    }
                    emit("allowableValues", asStringList(schema.enum_));
                    final String collectionFormat = serializableParameter.style;
                    if (ObjectHelper.isNotEmpty(collectionFormat)) {
                        emit("collectionFormat", CollectionFormat.valueOf(collectionFormat));
                    }
                    if (ObjectHelper.isNotEmpty(schema.default_)) {
                        String value = schema.default_.toString();
                        emit("defaultValue", value);
                    }

                    if ("array".equals(dataType)) {
                        emit("arrayType", schema.type);
                    }
                }
            }
        }
        if (parameter.required != null) {
            emit("required", parameter.required);
        } else {
            emit("required", Boolean.FALSE);
        }
        emit("description", parameter.description);
        emitter.emit("endParam");

        return emitter;
    }

    CodeEmitter<T> emit(final String method, final List<String> values) {
        if (values == null || values.isEmpty()) {
            return emitter;
        }

        return emitter.emit(method, new Object[] {values.toArray(new String[values.size()])});
    }

    CodeEmitter<T> emit(final String method, final Object value) {
        if (ObjectHelper.isEmpty(value)) {
            return emitter;
        }

        return emitter.emit(method, value);
    }

    void visit(final PathVisitor.HttpMethod method, final OasOperation operation) {
        if (filter.accept(operation.operationId)) {
            final String methodName = method.name().toLowerCase();
            emitter.emit(methodName, path);

            emit("id", operation.operationId);
            emit("description", operation.description);
            List<String> operationLevelConsumes = new ArrayList<String>();
            if (operation instanceof Oas20Operation) {
                operationLevelConsumes = ((Oas20Operation)operation).consumes;
            } else if (operation instanceof Oas30Operation) {
                Oas30Operation oas30Operation = (Oas30Operation)operation;
                if (oas30Operation.requestBody != null 
                    && oas30Operation.requestBody.content != null) { 
                    for (String ct : oas30Operation.requestBody.content.keySet()) {
                        operationLevelConsumes.add(ct);
                    }
                }
                    
            }
            emit("consumes", operationLevelConsumes);
            List<String> operationLevelProduces = new ArrayList<String>();
            if (operation instanceof Oas20Operation) {
                operationLevelProduces = ((Oas20Operation)operation).produces;
            } else if (operation instanceof Oas30Operation) {
                Oas30Operation oas30Operation = (Oas30Operation)operation;
                if (oas30Operation.responses != null) {
                    for (OasResponse response : oas30Operation.responses.getResponses()) {
                        Oas30Response oas30Response = (Oas30Response)response;
                        for (String ct : oas30Response.content.keySet()) {
                            operationLevelProduces.add(ct);
                        }
                    }
                }
            }
            emit("produces", operationLevelProduces);
            if (operation.getParameters() != null) {
                operation.getParameters().forEach(parameter -> {
                    emit((Oas20Parameter)parameter);
                });
            }

            emitter.emit("to", destinationGenerator.generateDestinationFor(operation));
        }
    }
}