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
package org.apache.camel.component.servicenow.springboot;

import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.servicenow.ServiceNowComponent;
import org.apache.camel.component.servicenow.ServiceNowRelease;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * The servicenow component is used to integrate Camel with ServiceNow cloud
 * services.
 * 
 * Generated by camel-package-maven-plugin - do not edit this file!
 */
@ConfigurationProperties(prefix = "camel.component.servicenow")
public class ServiceNowComponentConfiguration {

    /**
     * The ServiceNow default configuration
     */
    private ServiceNowConfigurationNestedConfiguration configuration;
    /**
     * The ServiceNow REST API url
     */
    private String apiUrl;
    /**
     * ServiceNow user account name MUST be provided
     */
    private String userName;
    /**
     * ServiceNow account password MUST be provided
     */
    private String password;
    /**
     * OAuth2 ClientID
     */
    private String oauthClientId;
    /**
     * OAuth2 ClientSecret
     */
    private String oauthClientSecret;
    /**
     * OAuth token Url
     */
    private String oauthTokenUrl;

    public ServiceNowConfigurationNestedConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(
            ServiceNowConfigurationNestedConfiguration configuration) {
        this.configuration = configuration;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getOauthClientId() {
        return oauthClientId;
    }

    public void setOauthClientId(String oauthClientId) {
        this.oauthClientId = oauthClientId;
    }

    public String getOauthClientSecret() {
        return oauthClientSecret;
    }

    public void setOauthClientSecret(String oauthClientSecret) {
        this.oauthClientSecret = oauthClientSecret;
    }

    public String getOauthTokenUrl() {
        return oauthTokenUrl;
    }

    public void setOauthTokenUrl(String oauthTokenUrl) {
        this.oauthTokenUrl = oauthTokenUrl;
    }

    public static class ServiceNowConfigurationNestedConfiguration {
        public static final Class CAMEL_NESTED_CLASS = org.apache.camel.component.servicenow.ServiceNowConfiguration.class;
        /**
         * ServiceNow user account name, MUST be provided
         */
        private String userName;
        /**
         * The ServiceNow REST API url
         */
        private String apiUrl;
        /**
         * ServiceNow account password, MUST be provided
         */
        private String password;
        /**
         * OAuth2 ClientID
         */
        private String oauthClientId;
        /**
         * OAuth2 ClientSecret
         */
        private String oauthClientSecret;
        /**
         * OAuth token Url
         */
        private String oauthTokenUrl;
        /**
         * The default resource, can be overridden by header
         * CamelServiceNowResource
         */
        private String resource;
        /**
         * The default table, can be overridden by header CamelServiceNowTable
         */
        private String table;
        /**
         * True to exclude Table API links for reference fields (default: false)
         */
        private Boolean excludeReferenceLink;
        /**
         * True to suppress auto generation of system fields (default: false)
         */
        private Boolean suppressAutoSysField;
        /**
         * Set this value to true to remove the Link header from the response.
         * The Link header allows you to request additional pages of data when
         * the number of records matching your query exceeds the query limit
         */
        private Boolean suppressPaginationHeader;
        /**
         * Set this parameter to true to return all scores for a scorecard. If a
         * value is not specified, this parameter defaults to false and returns
         * only the most recent score value.
         */
        private Boolean includeScores;
        /**
         * Set this parameter to true to always return all available aggregates
         * for an indicator, including when an aggregate has already been
         * applied. If a value is not specified, this parameter defaults to
         * false and returns no aggregates.
         */
        private Boolean includeAggregates;
        /**
         * Set this parameter to true to return all available breakdowns for an
         * indicator. If a value is not specified, this parameter defaults to
         * false and returns no breakdowns.
         */
        private Boolean includeAvailableBreakdowns;
        /**
         * Set this parameter to true to return all available aggregates for an
         * indicator when no aggregate has been applied. If a value is not
         * specified, this parameter defaults to false and returns no
         * aggregates.
         */
        private Boolean includeAvailableAggregates;
        /**
         * Set this parameter to true to return all notes associated with the
         * score. The note element contains the note text as well as the author
         * and timestamp when the note was added.
         */
        private Boolean includeScoreNotes;
        /**
         * Set this parameter to true to return only scorecards that are
         * favorites of the querying user.
         */
        private Boolean favorites;
        /**
         * Set this parameter to true to return only scorecards for key
         * indicators.
         */
        private Boolean key;
        /**
         * Set this parameter to true to return only scorecards that have a
         * target.
         */
        private Boolean target;
        /**
         * Set this parameter to true to return only scorecards where the
         * indicator Display field is selected. Set this parameter to all to
         * return scorecards with any Display field value. This parameter is
         * true by default.
         */
        private String display = "true";
        /**
         * Enter the maximum number of scorecards each query can return. By
         * default this value is 10, and the maximum is 100.
         */
        private Integer perPage;
        /**
         * Specify the value to use when sorting results. By default, queries
         * sort records by value.
         */
        private String sortBy;
        /**
         * Specify the sort direction, ascending or descending. By default,
         * queries sort records in descending order. Use sysparm_sortdir=asc to
         * sort in ascending order.
         */
        private String sortDir;
        /**
         * Return the display value (true), actual value (false), or both (all)
         * for reference fields (default: false)
         */
        private String displayValue = "false";
        /**
         * True to set raw value of input fields (default: false)
         */
        private Boolean inputDisplayValue;
        /**
         * Defines the request model
         */
        private Map requestModels;
        /**
         * Sets Jackson's ObjectMapper to use for request/reply
         */
        @NestedConfigurationProperty
        private ObjectMapper mapper;
        /**
         * The ServiceNow release to target, default to Helsinki See
         * https://docs.servicenow.com
         */
        private ServiceNowRelease release = ServiceNowRelease.HELSINKI;
        /**
         * Gets only those categories whose parent is a catalog.
         */
        private Boolean topLevelOnly;
        private Map models;
        /**
         * Defines the response model
         */
        private Map responseModels;

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public String getApiUrl() {
            return apiUrl;
        }

        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getOauthClientId() {
            return oauthClientId;
        }

        public void setOauthClientId(String oauthClientId) {
            this.oauthClientId = oauthClientId;
        }

        public String getOauthClientSecret() {
            return oauthClientSecret;
        }

        public void setOauthClientSecret(String oauthClientSecret) {
            this.oauthClientSecret = oauthClientSecret;
        }

        public String getOauthTokenUrl() {
            return oauthTokenUrl;
        }

        public void setOauthTokenUrl(String oauthTokenUrl) {
            this.oauthTokenUrl = oauthTokenUrl;
        }

        public String getResource() {
            return resource;
        }

        public void setResource(String resource) {
            this.resource = resource;
        }

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }

        public Boolean getExcludeReferenceLink() {
            return excludeReferenceLink;
        }

        public void setExcludeReferenceLink(Boolean excludeReferenceLink) {
            this.excludeReferenceLink = excludeReferenceLink;
        }

        public Boolean getSuppressAutoSysField() {
            return suppressAutoSysField;
        }

        public void setSuppressAutoSysField(Boolean suppressAutoSysField) {
            this.suppressAutoSysField = suppressAutoSysField;
        }

        public Boolean getSuppressPaginationHeader() {
            return suppressPaginationHeader;
        }

        public void setSuppressPaginationHeader(Boolean suppressPaginationHeader) {
            this.suppressPaginationHeader = suppressPaginationHeader;
        }

        public Boolean getIncludeScores() {
            return includeScores;
        }

        public void setIncludeScores(Boolean includeScores) {
            this.includeScores = includeScores;
        }

        public Boolean getIncludeAggregates() {
            return includeAggregates;
        }

        public void setIncludeAggregates(Boolean includeAggregates) {
            this.includeAggregates = includeAggregates;
        }

        public Boolean getIncludeAvailableBreakdowns() {
            return includeAvailableBreakdowns;
        }

        public void setIncludeAvailableBreakdowns(
                Boolean includeAvailableBreakdowns) {
            this.includeAvailableBreakdowns = includeAvailableBreakdowns;
        }

        public Boolean getIncludeAvailableAggregates() {
            return includeAvailableAggregates;
        }

        public void setIncludeAvailableAggregates(
                Boolean includeAvailableAggregates) {
            this.includeAvailableAggregates = includeAvailableAggregates;
        }

        public Boolean getIncludeScoreNotes() {
            return includeScoreNotes;
        }

        public void setIncludeScoreNotes(Boolean includeScoreNotes) {
            this.includeScoreNotes = includeScoreNotes;
        }

        public Boolean getFavorites() {
            return favorites;
        }

        public void setFavorites(Boolean favorites) {
            this.favorites = favorites;
        }

        public Boolean getKey() {
            return key;
        }

        public void setKey(Boolean key) {
            this.key = key;
        }

        public Boolean getTarget() {
            return target;
        }

        public void setTarget(Boolean target) {
            this.target = target;
        }

        public String getDisplay() {
            return display;
        }

        public void setDisplay(String display) {
            this.display = display;
        }

        public Integer getPerPage() {
            return perPage;
        }

        public void setPerPage(Integer perPage) {
            this.perPage = perPage;
        }

        public String getSortBy() {
            return sortBy;
        }

        public void setSortBy(String sortBy) {
            this.sortBy = sortBy;
        }

        public String getSortDir() {
            return sortDir;
        }

        public void setSortDir(String sortDir) {
            this.sortDir = sortDir;
        }

        public String getDisplayValue() {
            return displayValue;
        }

        public void setDisplayValue(String displayValue) {
            this.displayValue = displayValue;
        }

        public Boolean getInputDisplayValue() {
            return inputDisplayValue;
        }

        public void setInputDisplayValue(Boolean inputDisplayValue) {
            this.inputDisplayValue = inputDisplayValue;
        }

        public Map getRequestModels() {
            return requestModels;
        }

        public void setRequestModels(Map requestModels) {
            this.requestModels = requestModels;
        }

        public ObjectMapper getMapper() {
            return mapper;
        }

        public void setMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        public ServiceNowRelease getRelease() {
            return release;
        }

        public void setRelease(ServiceNowRelease release) {
            this.release = release;
        }

        public Boolean getTopLevelOnly() {
            return topLevelOnly;
        }

        public void setTopLevelOnly(Boolean topLevelOnly) {
            this.topLevelOnly = topLevelOnly;
        }

        public Map getModels() {
            return models;
        }

        public void setModels(Map models) {
            this.models = models;
        }

        public Map getResponseModels() {
            return responseModels;
        }

        public void setResponseModels(Map responseModels) {
            this.responseModels = responseModels;
        }
    }
}