package com.github.ivanocortesini.log4j.elastic.appender;

import com.github.ivanocortesini.log4j.elastic.appender.dto.DataExample;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.api.Assertions;
import com.github.ivanocortesini.log4j.elastic.utils.ElasticUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ElasticAppenderTest {
    static ElasticUtils elasticUtils;

    Logger asynchronousLogger;
    Logger synchronousLogger;

    @BeforeAll
    static void setUp() throws IOException {
        elasticUtils = new ElasticUtils();
        elasticUtils.openTestConnection();
    }

    @AfterAll
    static void tearDown() throws IOException {
        elasticUtils.closeConnection();
    }

    @BeforeEach
    public void initTest() {
        asynchronousLogger = LogManager.getLogger("example-logger");
        synchronousLogger = LogManager.getLogger("example-logger-sync");
    }

    @AfterEach
    public void shutdownTest() throws IOException {
        elasticUtils.deleteIndex("example-log-index");
    }


    @Test
    void append() throws IOException, InterruptedException {

        for (int i=0; i<10000; i++) {
            DataExample data = new DataExample("title "+i, "address "+i, new Date(), Arrays.asList("0039061111111", "0039062222222"));
            asynchronousLogger.info("Message n. {}", i+1, data);
        }

        Thread.sleep(20000);
        Assertions.assertThat(elasticUtils.indexCount("example-log-index")).isEqualTo(10000);

        List<Map<String, Object>> storedData = elasticUtils.loadIndexData("example-log-index");
        assertThat(((String)storedData.get(0).get("message")).startsWith("Message n. ")).isEqualTo(true);
        assertThat(storedData.get(0).get("data-example")).isNotNull();
    }

    @Test
    void appendSingle() throws IOException, InterruptedException {
        synchronousLogger.info("Single message");

        Thread.sleep(1000);
        Assertions.assertThat(elasticUtils.indexCount("example-log-index")).isEqualTo(1);

        List<Map<String, Object>> storedData = elasticUtils.loadIndexData("example-log-index");
        assertThat(storedData.get(0).get("message")).isEqualTo("Single message");
    }

    @Test
    void appendSingleMDC() throws IOException, InterruptedException {
        ThreadContext.put("proeprty_1", "value_1");
        ThreadContext.put("proeprty_2", "value_2");
        assertThat(ThreadContext.getContext()).hasSize(2);
        synchronousLogger.info("Single message");

        Thread.sleep(1000);
        Assertions.assertThat(elasticUtils.indexCount("example-log-index")).isEqualTo(1);

        List<Map<String, Object>> storedData = elasticUtils.loadIndexData("example-log-index");
        assertThat(storedData.get(0).get("message")).isEqualTo("Single message");
        assertThat(storedData.get(0).get("proeprty_1")).isEqualTo("value_1");
        assertThat(storedData.get(0).get("proeprty_2")).isEqualTo("value_2");
        assertThat(ThreadContext.getContext()).hasSize(0);
    }

    @Test
    void appendSingleWithLambda() throws IOException, InterruptedException {
        synchronousLogger.info("Single message: {} {}",
                ()->"param value",
                ()->2,
                ()->new DataExample("title", "address", new Date(), Arrays.asList("0039061111111", "0039062222222"))
        );

        Thread.sleep(1000);
        Assertions.assertThat(elasticUtils.indexCount("example-log-index")).isEqualTo(1);

        List<Map<String, Object>> storedData = elasticUtils.loadIndexData("example-log-index");
        assertThat(storedData.get(0).get("message")).isEqualTo("Single message: param value 2");
        assertThat(storedData.get(0).get("data-example")).isNotNull();
        assertThat(((Map<String,Object>)storedData.get(0).get("data-example")).get("phone")).isEqualTo(Arrays.asList("0039061111111", "0039062222222"));
    }


    @Test
    void appendSingleException() throws IOException, InterruptedException {
        synchronousLogger.error("Single error", new IllegalArgumentException("Wrong"));

        Thread.sleep(1000);
        Assertions.assertThat(elasticUtils.indexCount("example-log-index")).isEqualTo(1);

        List<Map<String, Object>> storedData = elasticUtils.loadIndexData("example-log-index");
        assertThat(storedData.get(0).get("message")).isEqualTo("Single error");
        assertThat(storedData.get(0).get("errorMessage")).isEqualTo("Wrong");
        assertThat(storedData.get(0).get("errorType")).isEqualTo(IllegalArgumentException.class.getName());
    }
}