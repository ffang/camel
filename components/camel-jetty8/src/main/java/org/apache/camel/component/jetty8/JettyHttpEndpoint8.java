/**
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
package org.apache.camel.component.jetty8;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.camel.component.jetty.JettyContentExchange;
import org.apache.camel.component.jetty.JettyHttpComponent;
import org.apache.camel.component.jetty.JettyHttpEndpoint;
import org.apache.camel.http.common.HttpBinding;
import org.apache.camel.http.common.HttpConsumer;
import org.apache.camel.spi.UriEndpoint;

/**
 * The jetty component provides HTTP-based endpoints for consuming and producing HTTP requests.
 */
@UriEndpoint(scheme = "jetty", extendsScheme = "http", title = "Jetty",
        syntax = "jetty:httpUri", consumerClass = HttpConsumer.class, label = "http", lenientProperties = true)
public class JettyHttpEndpoint8 extends JettyHttpEndpoint {
    private HttpBinding binding;

    public JettyHttpEndpoint8(JettyHttpComponent component, String uri, URI httpURL) throws URISyntaxException {
        super(component, uri, httpURL);
    }

    @Override
    public HttpBinding getHttpBinding() {
        // make sure we include jetty9 variant of the http binding
        if (this.binding == null) {
            this.binding = new AttachmentHttpBinding();
            this.binding.setTransferException(isTransferException());
            if (getComponent() != null) {
                this.binding.setAllowJavaSerializedObject(getComponent().isAllowJavaSerializedObject());
            }
            this.binding.setHeaderFilterStrategy(getHeaderFilterStrategy());
        }
        return this.binding;
    }

    @Override
    public void setHttpBinding(HttpBinding binding) {
        super.setHttpBinding(binding);
        this.binding = binding;
    }

    @Override
    public JettyContentExchange createContentExchange() {
        return new JettyContentExchange8();
    }

}
