package com.github.ivanocortesini.log4j.elastic.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.LinkedList;
import java.util.List;


@Data @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class ElasticConfig {

    private String appenderName;
    private String indexName;
    private boolean ignoreExceptions;
    private int flushTimeOut;
    private String userName;
    private String password;
    private List<ElasticHost> cluster = new LinkedList<>();

}
