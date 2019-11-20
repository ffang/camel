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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;


import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.openapi.v2.models.Oas20Document;

public class RestSwaggerReaderApiDocsOverrideTest extends CamelTestSupport {

    @BindToRegistry("dummy-rest")
    private DummyRestConsumerFactory factory = new DummyRestConsumerFactory();

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                rest("/hello").apiDocs(false).consumes("application/json").produces("application/json").get("/hi/{name}").description("Saying hi").param().name("name")
                    .type(RestParamType.path).dataType("string").description("Who is it").endParam().to("log:hi").get("/bye/{name}").apiDocs(true).description("Saying bye").param()
                    .name("name").type(RestParamType.path).dataType("string").description("Who is it").endParam().responseMessage().code(200).message("A reply message")
                    .endResponseMessage().to("log:bye").post("/bye").description("To update the greeting message").consumes("application/xml").produces("application/xml").param()
                    .name("greeting").type(RestParamType.body).dataType("string").description("Message to use as greeting").endParam().to("log:bye");
            }
        };
    }

    @Test
    public void testReaderRead() throws Exception {
        BeanConfig config = new BeanConfig();
        config.setHost("localhost:8080");
        config.setSchemes(new String[] {"http"});
        config.setBasePath("/api");
        RestSwaggerReader reader = new RestSwaggerReader();
        Oas20Document swagger = null;
        try {
            swagger = reader.read(context.getRestDefinitions(), null, config, context.getName(), new DefaultClassResolver());
        
        assertNotNull(swagger);

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        Object dump = Library.writeNode(swagger);
        String json = mapper.writeValueAsString(dump);
        System.out.println("the json is =====>" + json);
        log.info(json);

        assertFalse(json.contains("\"/hello/bye\""));
        assertFalse(json.contains("\"summary\" : \"To update the greeting message\""));
        assertTrue(json.contains("\"/hello/bye/{name}\""));
        assertFalse(json.contains("\"/hello/hi/{name}\""));
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        context.stop();
    }

}
