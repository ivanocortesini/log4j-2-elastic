package com.github.ivanocortesini.log4j.elastic.client;


import com.github.ivanocortesini.log4j.elastic.config.ElasticConfig;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.appender.AppenderLoggingException;
import org.apache.logging.log4j.status.StatusLogger;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentType;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public final class ElasticClient {
    private static final Logger LOGGER = StatusLogger.getLogger();

    private static final Map<String, ElasticClient> clientByLoggerName = new HashMap<>();

    ElasticConfig config;
    RestHighLevelClient client;

    boolean bulkMode;
    BulkRequest bulkRequest;
    long bulkRequestCreationTime;
    ScheduledExecutorService bulkFlushTimeOutCheckerExecutor;


    //Life cycle and configuration
    public static ElasticClient getInstance(ElasticConfig config) throws IOException {
        ElasticClient client = clientByLoggerName.get(config.getAppenderName());
        if (client==null)
            clientByLoggerName.put(config.getAppenderName(), client = new ElasticClient(config));
        return client;
    }

    ElasticClient(ElasticConfig config) throws IOException {
        this.config = config;
        startup();
    }

    void startup() throws IOException {
        try { shutdown(); } catch (Exception e) {}

        HttpHost[] hosts = config.getCluster().stream()
                .map(node -> new HttpHost(node.getHost(), node.getPort(), node.getProtocol()))
                .toArray(s -> new HttpHost[s] );

        RestClientBuilder clienBuilder = RestClient.builder(hosts);
        basicAuthentication(clienBuilder);

        client = new RestHighLevelClient(clienBuilder);

        if (!client.indices().exists(new GetIndexRequest().indices(config.getIndexName()), RequestOptions.DEFAULT))
            client.indices().create(new CreateIndexRequest(config.getIndexName()), RequestOptions.DEFAULT);
    }

    void basicAuthentication(RestClientBuilder builder) {
        if (config.getUserName()!=null && config.getUserName().trim().length()>0 && config.getPassword()!=null && config.getPassword().trim().length()>0) {
            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials("user", "password"));
            builder.setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        }
    }

    void shutdown() throws IOException {
        try {
            if (bulkRequest!=null)
                sendBulkRequest();
            stopBulkFlushTimeOutChecker();
        } finally {
            if (client!=null)
                client.close();
        }
    }

    //Store function implementations
    public void storeJsonDocument(String document, boolean closeBatch) throws IOException {
        storeDocument(
                new IndexRequest(config.getIndexName(), "doc", null).source(document, XContentType.JSON),
                closeBatch
        );
    }
    public void storeMapDocument(Map<String, Object> document, boolean closeBatch) throws IOException {
        storeDocument(
                new IndexRequest(config.getIndexName(), "doc", null).source(document),
                closeBatch
        );
    }
    public void storeXContentDocument(XContentBuilder document, boolean closeBatch) throws IOException {
        storeDocument(
                new IndexRequest(config.getIndexName(), "doc", null).source(document),
                closeBatch
        );
    }
    synchronized void storeDocument(IndexRequest indexRequest, boolean closeBatch) throws IOException {
        if (bulkMode || closeBatch) {
            //Bulk
            if (bulkRequest == null) {
                if (!bulkMode) {
                    bulkMode=true;
                    if (config.getFlushTimeOut()>0)
                        startBulkFlushTimeOutChecker();
                }
                bulkRequest = new BulkRequest();
                bulkRequestCreationTime = System.currentTimeMillis();
            }

            bulkRequest.add(indexRequest);

            if (closeBatch)
                sendBulkRequest();
        } else
            //Single
            client.index(indexRequest, RequestOptions.DEFAULT);
    }


    private synchronized void sendBulkRequest() throws IOException {
        try {
            client.bulk(bulkRequest, RequestOptions.DEFAULT);
        } finally {
            bulkRequest = null;
        }
    }


    //Bulk buffer flush timeout management
    private void startBulkFlushTimeOutChecker() {
        bulkFlushTimeOutCheckerExecutor = Executors.newSingleThreadScheduledExecutor();
        bulkFlushTimeOutCheckerExecutor.scheduleAtFixedRate(
            () -> {
                //Timeout check based on bulk request creation time. Check is scheduled every 5 seconds
                if (bulkRequest!=null && config.getFlushTimeOut()<(System.currentTimeMillis()-bulkRequestCreationTime)/1000)
                    try {
                        sendBulkRequest();
                    } catch (IOException e) {
                        LOGGER.error("Error logging into Elasticsearch during a bulk request execution",e);
                        if (!config.isIgnoreExceptions())
                            throw new AppenderLoggingException(e);
                    }
            },
            5,
            5,
            TimeUnit.SECONDS);
    }
    private void stopBulkFlushTimeOutChecker() {
        if (bulkFlushTimeOutCheckerExecutor!=null && !bulkFlushTimeOutCheckerExecutor.isShutdown())
            bulkFlushTimeOutCheckerExecutor.shutdown();
    }
}
