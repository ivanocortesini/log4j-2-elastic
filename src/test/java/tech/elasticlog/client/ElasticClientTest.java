package tech.elasticlog.client;

import tech.elasticlog.config.ElasticConfig;
import tech.elasticlog.config.ElasticHost;
import org.assertj.core.api.Assertions;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.junit.jupiter.api.*;
import tech.elasticlog.utils.ElasticUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.Properties;

import static org.assertj.core.api.Assertions.*;

class ElasticClientTest {
    static ElasticUtils elasticUtils;
    static Properties alsticConfiguration = new Properties();

    @BeforeAll
    static void setUp() throws IOException {
        elasticUtils = new ElasticUtils();
        elasticUtils.openTestConnection();

        alsticConfiguration.load(ElasticClientTest.class.getResourceAsStream("/elasticsearch.properties"));
    }

    @AfterAll
    static void tearDown() throws IOException {
        try {
            elasticUtils.deleteIndex("test-elastic");
            elasticUtils.deleteIndex("test-elastic2");
            elasticUtils.deleteIndex("test-elastic3");
            elasticUtils.deleteIndex("test-elastic4");
        } finally {
            elasticUtils.closeConnection();
        }
    }

    @Test
    void getInstance() throws IOException {
        ElasticConfig config = new ElasticConfig(
                "test-elastic",
                "audit-test",
                false,
                60,
                null,null,
                Arrays.asList(
                        new ElasticHost(
                                alsticConfiguration.getProperty("test.connection.protocol", "http"),
                                alsticConfiguration.getProperty("test.connection.host", "localhost"),
                                Integer.parseInt(alsticConfiguration.getProperty("test.connection.port", "9200"))
                        )
                )
        );

        ElasticClient client = ElasticClient.getInstance(config);
        assertThat(client).isNotNull();

        client.shutdown();
    }

    @Test
    void sendImmedateMessagesWithError() throws IOException, InterruptedException {
        ElasticConfig config = new ElasticConfig(
                "test-elastic2",
                "test-elastic2",
                false,
                30,
                null,null,
                Arrays.asList(
                        new ElasticHost(
                                alsticConfiguration.getProperty("test.connection.protocol", "http"),
                                alsticConfiguration.getProperty("test.connection.host", "localhost"),
                                Integer.parseInt(alsticConfiguration.getProperty("test.connection.port", "9200"))
                        )
                )
        );

        ElasticClient client = ElasticClient.getInstance(config);
        assertThat(client).isNotNull();


        for (int i=0; i<50; i++) {
            XContentBuilder document = XContentFactory.jsonBuilder().startObject();
            document.field("event", "test event");
            document.field("target", "test target");
            document.timeField("timestamp", new Date());
            document.field("position", i);
            document.endObject();
            client.storeXContentDocument( document, false);
        }

        Thread.sleep(1000);
        Assertions.assertThat(elasticUtils.indexCount("test-elastic2")).isEqualTo(50l);

        client.shutdown();
    }

    @Test
    void sendBatchMessagesWithError() throws IOException, InterruptedException {
        ElasticConfig config = new ElasticConfig(
                "test-elastic3",
                "test-elastic3",
                false,
                10,
                null,null,
                Arrays.asList(
                        new ElasticHost(
                                alsticConfiguration.getProperty("test.connection.protocol", "http"),
                                alsticConfiguration.getProperty("test.connection.host", "localhost"),
                                Integer.parseInt(alsticConfiguration.getProperty("test.connection.port", "9200"))
                        )
                )
        );

        ElasticClient client = ElasticClient.getInstance(config);
        assertThat(client).isNotNull();


        for (int i=0; i<50; i++) {
            XContentBuilder document = XContentFactory.jsonBuilder().startObject();
            document.field("event", "test event");
            document.field("target", "test target");
            document.timeField("timestamp", new Date());
            document.field("position", i);
            document.endObject();
            client.storeXContentDocument( document, i % 30 == 0 );
        }

        Thread.sleep(1000);
        Assertions.assertThat(elasticUtils.indexCount("test-elastic3")).isBetween(29l,31l);
        Thread.sleep(16000);
        Assertions.assertThat(elasticUtils.indexCount("test-elastic3")).isEqualTo(50l);

        client.shutdown();
    }

    @Test
    void sendBatchMessagesWithoutError() throws IOException, InterruptedException {
        ElasticConfig config = new ElasticConfig(
                "test-elastic4",
                "test-elastic4",
                true,
                10,
                null,null,
                Arrays.asList(
                        new ElasticHost(
                                alsticConfiguration.getProperty("test.connection.protocol", "http"),
                                alsticConfiguration.getProperty("test.connection.host", "localhost"),
                                Integer.parseInt(alsticConfiguration.getProperty("test.connection.port", "9200"))
                        )
                )
        );

        ElasticClient client = ElasticClient.getInstance(config);
        assertThat(client).isNotNull();


        for (int i=0; i<50; i++) {
            XContentBuilder document = XContentFactory.jsonBuilder().startObject();
            document.field("event", "test event");
            document.field("target", "test target");
            document.timeField("timestamp", new Date());
            document.field("position", i);
            document.endObject();
            client.storeXContentDocument( document, i % 30 == 0 );
        }

        Thread.sleep(7000);
        Assertions.assertThat(elasticUtils.indexCount("test-elastic4")).isBetween(29l,31l);
        Thread.sleep(10000);
        Assertions.assertThat(elasticUtils.indexCount("test-elastic4")).isEqualTo(50l);

        client.shutdown();
    }


}