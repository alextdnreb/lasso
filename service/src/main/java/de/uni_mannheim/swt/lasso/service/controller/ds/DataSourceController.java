/*
 * LASSO - an Observatorium for the Dynamic Selection, Analysis and Comparison of Software
 * Copyright (C) 2024 Marcus Kessel (University of Mannheim) and LASSO contributers
 *
 * This file is part of LASSO.
 *
 * LASSO is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LASSO is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LASSO.  If not, see <https://www.gnu.org/licenses/>.
 */
package de.uni_mannheim.swt.lasso.service.controller.ds;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.uni_mannheim.swt.lasso.cluster.ClusterEngine;
import de.uni_mannheim.swt.lasso.core.datasource.DataSource;
import de.uni_mannheim.swt.lasso.core.dto.DataSourceResponse;
import de.uni_mannheim.swt.lasso.core.dto.ImplementationRequest;
import de.uni_mannheim.swt.lasso.core.dto.ImplementationResponse;
import de.uni_mannheim.swt.lasso.core.dto.SearchQueryRequest;
import de.uni_mannheim.swt.lasso.core.dto.SearchRequestResponse;
import de.uni_mannheim.swt.lasso.core.model.CodeUnit;
import de.uni_mannheim.swt.lasso.datasource.maven.MavenCodeUnitUtils;
import de.uni_mannheim.swt.lasso.datasource.maven.MavenDataSource;
import de.uni_mannheim.swt.lasso.datasource.maven.support.MavenCentralIndex;
import de.uni_mannheim.swt.lasso.engine.LassoConfiguration;
import de.uni_mannheim.swt.lasso.index.CandidateQueryResult;
import de.uni_mannheim.swt.lasso.index.SearchOptions;
import de.uni_mannheim.swt.lasso.index.repo.SolrCandidateDocument;
import de.uni_mannheim.swt.lasso.service.controller.BaseApi;
import de.uni_mannheim.swt.lasso.service.controller.ds.query.QueryStrategy;
import de.uni_mannheim.swt.lasso.service.controller.ds.query.ScriptQueryStrategy;
import de.uni_mannheim.swt.lasso.service.controller.ds.query.TextualQueryStrategy;
import de.uni_mannheim.swt.lasso.service.dto.UserInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * LASSO's DataSource API.
 * 
 * @author Marcus Kessel
 *
 */
@RestController
@RequestMapping(value = "/api/v1/lasso/datasource")
@Tag(name = "DataSource API")
public class DataSourceController extends BaseApi {

    private static final Logger LOG = LoggerFactory
            .getLogger(DataSourceController.class);

    @Autowired
    private Environment env;
    @Autowired
    private LassoConfiguration lassoConfiguration;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    ClusterEngine clusterEngine;

    private QueryStrategy decideQueryStrategy(SearchQueryRequest request) {
        if(StringUtils.isNotBlank(request.getExecutionId())) {
            return new ScriptQueryStrategy(clusterEngine, lassoConfiguration);
        } 
        // classic search
        return new TextualQueryStrategy(clusterEngine, lassoConfiguration);
    }

    @Operation(summary = "Get implementations", description = "Get implementations")
    @RequestMapping(value = "/implementations", method = RequestMethod.POST, consumes = "application/json;charset=UTF-8", produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<ImplementationResponse> getImplementations(
            @RequestBody ImplementationRequest request,
            /*@ApiIgnore*/ @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse) {
        // fix to default
        return getImplementations(request, "mavenCentral2023", userDetails, httpServletRequest, httpServletResponse);
    }

    @Operation(summary = "Get implementations", description = "Get implementations")
    @RequestMapping(value = "/{dataSource}/implementations", method = RequestMethod.POST, consumes = "application/json;charset=UTF-8", produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<ImplementationResponse> getImplementations(
            @RequestBody ImplementationRequest request,
            @PathVariable("dataSource") String dataSource,
            /*@ApiIgnore*/ @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse) {
        // get user details
        UserInfo userInfo = getUserInfo(httpServletRequest, userDetails);

        try {
            //
            SearchOptions searchOptions = new SearchOptions();

            MavenDataSource mavenDataSource = (MavenDataSource) lassoConfiguration.getDataSource(dataSource);

            MavenCentralIndex mavenCentralIndex = mavenDataSource.getMavenCentralIndex();

            Map<String, Map> implementationsRaw = new HashMap<>();

            Map<String, CodeUnit> implementations = request.getIds().stream().map(id -> {
                try {
                    CandidateQueryResult result = mavenCentralIndex.query("*:*", searchOptions, new String[]{String.format("id:\"%s\"", id)}, 0, 1, Collections.emptyList());
                    List<CodeUnit> impls = result.getCandidates().stream().map(c -> {
                        CodeUnit implementation = MavenCodeUnitUtils.toImplementation(((SolrCandidateDocument) c).getSolrDocument());
                        implementation.setDataSource(dataSource);

                        // raw
                        implementationsRaw.put(id, ((SolrCandidateDocument) c).getSolrDocument());

                        return implementation;
                    }).collect(Collectors.toList());

                    return impls.get(0);
                } catch (IOException e) {
                    if (LOG.isWarnEnabled()) {
                        LOG.warn(String.format("Could not get implementations"), e);
                    }
                }

                return null;
            }).filter(Objects::nonNull).collect(Collectors.toMap(CodeUnit::getId,
                    i -> i,
                    (e1, e2) -> e1,
                    LinkedHashMap::new));
            //.collect(Collectors.toMap(CodeUnit::getId, i -> i));

            if (LOG.isInfoEnabled()) {
                LOG.info("Returning getImplementations response to '{}'",
                        userInfo.getRemoteIpAddress());
            }

            ImplementationResponse response = new ImplementationResponse();
            response.setImplementations(implementations);
            response.setImplementationsRaw(implementationsRaw);

            // 200
            return ResponseEntity.ok(response);
        } catch (Throwable e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn(String.format("Could not get implementations response"), e);
            }

            // bad request
            throw new RuntimeException(String.format("Could not get implementations response"), e);
        }
    }

    @Operation(summary = "Query implementations", description = "Query implementations")
    @RequestMapping(value = "/{dataSource}/query", method = RequestMethod.POST, consumes = "application/json;charset=UTF-8", produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<SearchRequestResponse> query(
            @RequestBody SearchQueryRequest request,
            @PathVariable("dataSource") String dataSource,
            /*@ApiIgnore*/ @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse) {
        // get user details
        UserInfo userInfo = getUserInfo(httpServletRequest, userDetails);

        SearchRequestResponse response = new SearchRequestResponse();

        try {
            QueryStrategy queryStrategy = decideQueryStrategy(request);
            response = queryStrategy.query(request, dataSource);
            
            if (LOG.isInfoEnabled()) {
                LOG.info("Returning query Implementations response to '{}'",
                        userInfo.getRemoteIpAddress());
            }

            // 200
            return ResponseEntity.ok(response);
        } catch (Throwable e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn(String.format("Could not get implementations response"), e);
            }

            // bad request
            throw new RuntimeException(String.format("Could not get implementations response"), e);
        }
    }

    private List<String> getEmbeddingsResponse(SearchQueryRequest request) {
        HttpClient client = HttpClient.newHttpClient();
        JSONObject json = new JSONObject();
        json.put("input", request.getQuery());
        LOG.info(String.format("Querying embeddings for request"));
        HttpRequest embeddingRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://192.168.1.4:4999/search"))
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .header("Content-Type", "application/json")
                .build();

        try {
            HttpResponse<String> embeddingResponse = client.send(embeddingRequest, HttpResponse.BodyHandlers.ofString());
            // Parse the response body to JSON using Jackson
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonResponse = objectMapper.readTree(embeddingResponse.body());

            // Extract solr_ids
            JsonNode implementations = jsonResponse.get("implementations");

            String solrIds = StreamSupport.stream(implementations.spliterator(), false)
            .map(node -> node.get("solr_id").asText())
            .collect(Collectors.joining(" ", "(", ")"));

            LOG.info("Request sent" + solrIds);

            List<String> idFilter = new ArrayList();
            idFilter.add("id:"+solrIds);    

            return idFilter;
        }
        catch (Throwable e) {
            LOG.warn(String.format("Could not get embedding response"), e);
            throw new RuntimeException(String.format("Could not get embedding response"), e);
        }
    }

    @Operation(summary = "Query implementations using embedding-search", description = "Query implementations using embedding-search")
    @RequestMapping(value = "/{dataSource}/query/fusion", method = RequestMethod.POST, consumes = "application/json;charset=UTF-8", produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<SearchRequestResponse> fusionQuery(
            @RequestBody SearchQueryRequest request,
            @PathVariable("dataSource") String dataSource,
            /*@ApiIgnore*/ @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse) {
        // get user details
        UserInfo userInfo = getUserInfo(httpServletRequest, userDetails);

        SearchRequestResponse response = new SearchRequestResponse();

        try {
            List<String> idFilter = getEmbeddingsResponse(request);
            List<String> currentFilters = request.getFilters();
            request.setFilters(Stream.concat(currentFilters.stream(), idFilter.stream()).collect(Collectors.toList()));

            QueryStrategy queryStrategy = decideQueryStrategy(request);
            response = queryStrategy.query(request, dataSource);

            if (LOG.isInfoEnabled()) {
                LOG.info("Returning query Implementations response to '{}'",
                        userInfo.getRemoteIpAddress());
            }

            // 200
            return ResponseEntity.ok(response);
        } catch (Throwable e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn(String.format("Could not get implementations response"), e);
            }

            // bad request
            throw new RuntimeException(String.format("Could not get implementations response"), e);
        }
    }

    @Operation(summary = "Query implementations using embedding-search", description = "Query implementations using embedding-search")
    @RequestMapping(value = "/{dataSource}/query/semantic", method = RequestMethod.POST, consumes = "application/json;charset=UTF-8", produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<SearchRequestResponse> semanticQuery(
            @RequestBody SearchQueryRequest request,
            @PathVariable("dataSource") String dataSource,
            /*@ApiIgnore*/ @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse) {
        // get user details
        UserInfo userInfo = getUserInfo(httpServletRequest, userDetails);

        SearchRequestResponse response = new SearchRequestResponse();

        try {
            List<String> idFilter = getEmbeddingsResponse(request);
            List<String> currentFilters = request.getFilters();
            request.setFilters(Stream.concat(currentFilters.stream(), idFilter.stream()).collect(Collectors.toList()));

            QueryStrategy queryStrategy = decideQueryStrategy(request);
            request.setQuery("*:*");
            response = queryStrategy.query(request, dataSource);

            if (LOG.isInfoEnabled()) {
                LOG.info("Returning query Implementations response to '{}'",
                        userInfo.getRemoteIpAddress());
            }

            // 200
            return ResponseEntity.ok(response);
        } catch (Throwable e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn(String.format("Could not get implementations response"), e);
            }

            // bad request
            throw new RuntimeException(String.format("Could not get implementations response"), e);
        }
    }


    @Operation(summary = "Data sources Info", description = "Get info about available data sources")
    @RequestMapping(value = "/info", method = RequestMethod.GET, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<DataSourceResponse> getDataSources(
            /*@ApiIgnore*/ @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpServletRequest) {
        // get user details
        UserInfo userInfo = getUserInfo(httpServletRequest, userDetails);

        try {
            Map<String, DataSource> dataSourceMap = applicationContext.getBeansOfType(DataSource.class);

            Map<String, de.uni_mannheim.swt.lasso.core.dto.DataSource> dataSources = dataSourceMap.values().stream().map(ds -> {
                de.uni_mannheim.swt.lasso.core.dto.DataSource dsDesc = new de.uni_mannheim.swt.lasso.core.dto.DataSource();
                dsDesc.setId(ds.getId());
                dsDesc.setName(ds.getName());
                dsDesc.setDescription(ds.getDescription());

                if(ds instanceof MavenDataSource) {
                    MavenDataSource mds = (MavenDataSource) ds;
                    HttpSolrClient solrClient = (HttpSolrClient) mds.getMavenCentralIndex().getMavenCentralRepository().getSolrClient();
                    dsDesc.setUrl(solrClient.getBaseURL());
                } else {
                    dsDesc.setUrl("none");
                }

                return dsDesc;
            }).collect(Collectors.toMap(k -> k.getId(), v -> v));

            DataSourceResponse response = new DataSourceResponse();
            response.setDataSources(dataSources);

            if (LOG.isInfoEnabled()) {
                LOG.info("Returning data source info response to '{}':\n{}",
                        userInfo.getRemoteIpAddress(),
                        ToStringBuilder
                                .reflectionToString(response));
            }

            // 200
            return ResponseEntity.ok(response);
        } catch (Throwable e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn(String.format("Could not get data source info"), e);
            }

            // bad request
            throw new RuntimeException(String.format("Could not get LSL info"), e);
        }
    }
}
