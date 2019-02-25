package com.github.ivanocortesini.log4j.elastic.utils;

import org.apache.http.HttpHost;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ElasticUtils {
    RestHighLevelClient client;

    public void openTestConnection() throws IOException {
        Properties configuration = new Properties();
        configuration.load(this.getClass().getResourceAsStream("/elasticsearch.properties"));
        openConnection(
                configuration.getProperty("test.connection.protocol", "http"),
                configuration.getProperty("test.connection.host", "localhost"),
                Integer.parseInt(configuration.getProperty("test.connection.port", "9200"))
        );
    }
    public void openConnection(String protocol, String host, int port) {
        client = new RestHighLevelClient(
                RestClient.builder(
                        new HttpHost(host, port, protocol)
                ));
    }

    public void closeConnection() throws IOException {
        client.close();
    }

    public void deleteIndex(String indexName) throws IOException {
        DeleteIndexRequest deleteRequest = new DeleteIndexRequest(indexName);
        if (client.indices().exists(new GetIndexRequest().indices(indexName), RequestOptions.DEFAULT))
            client.indices().delete(deleteRequest, RequestOptions.DEFAULT);
    }

    public long indexCount(String indexName) throws IOException {
        CountRequest countRequest = new CountRequest(indexName);
        if (client.indices().exists(new GetIndexRequest().indices(indexName), RequestOptions.DEFAULT))
            return client.count(countRequest, RequestOptions.DEFAULT).getCount();
        else
            return -1;
    }

    public void addDocument(String index, XContentBuilder document) throws IOException {
        client.index(new IndexRequest(index, "doc", null).source(document), RequestOptions.DEFAULT);
    }

    public List<Map<String,Object>> loadIndexData(String index) throws IOException {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.matchAllQuery());

        SearchRequest searchRequest = new SearchRequest()
                .source(searchSourceBuilder)
                .indices(index);

        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

        return Arrays.stream(searchResponse.getHits().getHits())
                .map(h -> h.getSourceAsMap())
                .collect(Collectors.toList());
    }

}
