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
package org.apache.camel.swagger;


import io.apicurio.datamodels.core.models.Extension;
import io.apicurio.datamodels.openapi.v2.models.Oas20Definitions;
import io.apicurio.datamodels.openapi.v2.models.Oas20Document;
import io.apicurio.datamodels.openapi.v2.models.Oas20SchemaDefinition;


/**
 * A Camel extended {@link ModelConverters} where we appending vendor extensions
 * to include the java class name of the model classes.
 */
public class RestModelConverters {

    public Oas20Definitions readClass(Oas20Document oas20Document, Class clazz) {
        String name = clazz.getName();
        Oas20Definitions resolved = oas20Document.definitions;
        if (resolved != null) {
            for (Oas20SchemaDefinition model : resolved.getDefinitions()) {
                // enrich with the class name of the model
                Extension extension = new Extension();
                extension.name = "x-className";
                extension.value = name;
                model.getExtensions().add(extension);
            }

        }
        return resolved;
    }
}
