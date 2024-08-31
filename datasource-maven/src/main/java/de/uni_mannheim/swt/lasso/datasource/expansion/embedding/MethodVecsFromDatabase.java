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
package de.uni_mannheim.swt.lasso.datasource.expansion.embedding;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MethodVecsFromDatabase {
    private static final Logger LOG = LoggerFactory.getLogger(Word2VecRaw.class);
    private HttpClient client;

    public MethodVecsFromDatabase() {
        client = HttpClient.newHttpClient();
    }

    public LinkedHashMap<String, Double> getNearestWords(String queryWord, int topN) {
        
        JSONObject json = new JSONObject();
        json.put("input", queryWord);
        LOG.info(String.format("Querying embeddings for request"));
        HttpRequest embeddingRequest = HttpRequest.newBuilder()
        .uri(URI.create("http://10.13.77.232:4999/searchMethods"))
        .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
        .header("Content-Type", "application/json")
        .build();

        try {
            HttpResponse<String> embeddingResponse = client.send(embeddingRequest, HttpResponse.BodyHandlers.ofString());
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonResponse = objectMapper.readTree(embeddingResponse.body());

            JsonNode implementations = jsonResponse.get("implementations");

            Map<String, Double> similarityMap = StreamSupport.stream(implementations.spliterator(), false)
                .collect(Collectors.toMap(
                    node -> node.get("name").asText(),  // Key: solr_id
                    node -> node.get("score").asDouble()      // Value: score
                ));


            return similarityMap.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Double>comparingByValue())
                .limit(topN)
                .collect(Collectors.toMap(k -> k.getKey(), v -> v.getValue(),
                        (v1,v2) ->{ throw new RuntimeException(String.format("Duplicate key for values %s and %s", v1, v2));},
                        LinkedHashMap::new));   
        }
        catch (Throwable e) {
            LOG.warn(String.format("Could not get embedding response"), e);
            throw new RuntimeException(String.format("Could not get embedding response"), e);
        }

        
    }
}
