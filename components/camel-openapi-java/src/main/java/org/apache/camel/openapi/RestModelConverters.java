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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.apicurio.datamodels.core.models.Extension;
import io.apicurio.datamodels.openapi.models.OasDocument;
import io.apicurio.datamodels.openapi.models.OasSchema;
import io.apicurio.datamodels.openapi.v2.models.Oas20Definitions;
import io.apicurio.datamodels.openapi.v2.models.Oas20Document;
import io.apicurio.datamodels.openapi.v2.models.Oas20SchemaDefinition;
import io.apicurio.datamodels.openapi.v3.models.Oas30Document;
import io.apicurio.datamodels.openapi.v3.models.Oas30SchemaDefinition;

/**
 * A Camel extended {@link ModelConverters} where we appending vendor extensions to include the java class
 * name of the model classes.
 */
public class RestModelConverters {

    public List<? extends OasSchema> readClass(OasDocument oasDocument, Class<?> clazz) {
        if (oasDocument instanceof Oas20Document) {
            String name = clazz.getName();
            if (!name.contains(".")) {
                return null;
            }
            if (((Oas20Document)oasDocument).definitions == null) {
                ((Oas20Document)oasDocument).definitions = ((Oas20Document)oasDocument).createDefinitions();
            }
            Oas20Definitions resolved = ((Oas20Document)oasDocument).definitions;
            Oas20SchemaDefinition model = resolved.createSchemaDefinition(clazz.getSimpleName());
            resolved.addDefinition(clazz.getSimpleName(), model);
            model.type = clazz.getSimpleName();
            Extension extension = model.createExtension();
            extension.name = "x-className";
            Map<String, String> value = new HashMap<String, String>();
            value.put("type", "string");
            value.put("format", name);
            extension.value = value;
            model.addExtension("x-className", extension);
            return resolved.getDefinitions();
        } else if (oasDocument instanceof Oas30Document){
            String name = clazz.getName();
            if (!name.contains(".")) {
                return null;
            }
            if (((Oas30Document)oasDocument).components == null) {
                ((Oas30Document)oasDocument).components = ((Oas30Document)oasDocument).createComponents();
            }
            Oas30SchemaDefinition model = ((Oas30Document)oasDocument).components.createSchemaDefinition(clazz.getSimpleName());
            ((Oas30Document)oasDocument).components.addSchemaDefinition(clazz.getSimpleName(), model);
            model.type = clazz.getSimpleName();
            Extension extension = model.createExtension();
            extension.name = "x-className";
            Map<String, String> value = new HashMap<String, String>();
            value.put("type", "string");
            value.put("format", name);
            extension.value = value;
            model.addExtension("x-className", extension);
            return ((Oas30Document)oasDocument).components.getSchemaDefinitions();
        } else {
            return null;
        }
    }
}
