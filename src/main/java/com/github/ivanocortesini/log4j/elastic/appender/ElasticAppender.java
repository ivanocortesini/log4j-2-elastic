package com.github.ivanocortesini.log4j.elastic.appender;

import com.github.ivanocortesini.log4j.elastic.client.ElasticClient;
import com.github.ivanocortesini.log4j.elastic.config.ElasticConfig;
import com.github.ivanocortesini.log4j.elastic.utils.DocUtils;
import com.github.ivanocortesini.log4j.elastic.config.ElasticHost;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.appender.AppenderLoggingException;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.status.StatusLogger;

import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Plugin(name = "Elastic", category = "Core", elementType = "appender", printObject = true)
public final class ElasticAppender extends AbstractAppender {
    private static final Logger LOGGER = StatusLogger.getLogger();

    private ElasticClient elasticClient;
    private boolean ignoreExceptions;


    public ElasticAppender(String name, ElasticConfig elasticConfig, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions) throws IOException {
        super(name, filter, layout, ignoreExceptions);
        elasticClient = ElasticClient.getInstance(elasticConfig);
        this.ignoreExceptions = ignoreExceptions;
    }


    @PluginFactory
    public static ElasticAppender createAppender(@PluginAttribute("name") String name,
                                                 @PluginAttribute(value = "cluster", defaultString = "http://localhost:9200") String cluster,
                                                 @PluginAttribute(value = "index") String indexName,
                                                 @PluginAttribute(value = "flushTimeOut") int flushTimeOut,
                                                 @PluginAttribute(value = "ignoreExceptions", defaultBoolean = true) boolean ignoreExceptions,
                                                 @PluginAttribute(value = "username") String username,
                                                 @PluginAttribute(value = "password") String password,
                                                 @PluginElement("Layout") Layout layout,
                                                 @PluginElement("Filters") Filter filter
                                                 ) {
        if (name == null) {
            LOGGER.error("No name provided for Elastic appender");
            return null;
        }

        if (layout == null)
            layout = PatternLayout.createDefaultLayout();

        try {
            return new ElasticAppender(
                    name,
                    new ElasticConfig(
                            name,
                            indexName==null ? name : indexName,
                            ignoreExceptions,
                            flushTimeOut,
                            username,
                            password,
                            Stream.of(cluster.split("[\\s,;]+")).map(host -> {
                                try {
                                    return new ElasticHost(host);
                                } catch (MalformedURLException e) {
                                    return null;
                                }
                            }).filter(Objects::nonNull).collect(Collectors.toList())
                    ),
                    filter,
                    layout,
                    ignoreExceptions
            );
        } catch (IOException e) {
            LOGGER.error("Error initializing Elasticsearch appender named '"+name+"'",e);
            return null;
        }
    }

    @Override
    public void append(LogEvent logEvent) {
        try {
            elasticClient.storeXContentDocument(
                    DocUtils.docBuilder(logEvent, ThreadContext.getContext(), logEvent.isIncludeLocation(), ignoreExceptions),
                    logEvent.isEndOfBatch());
            ThreadContext.clearAll();
        } catch (Throwable e) {
            LOGGER.error("Error logging into Elasticsearch for logger '"+logEvent.getLoggerName()+"'",e);
            if (!ignoreExceptions)
                throw new AppenderLoggingException(e);
        }
    }
}