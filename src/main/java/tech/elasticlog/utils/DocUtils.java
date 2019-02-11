package tech.elasticlog.utils;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import tech.elasticlog.appender.Logged;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AppenderLoggingException;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.status.StatusLogger;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


public class DocUtils {
	private static final Logger LOGGER = StatusLogger.getLogger();

	private static ObjectMapper mapper = new ObjectMapper()
			.setSerializationInclusion(JsonInclude.Include.NON_NULL)
			.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZ"));

	//Cached mapping between parameters object class and field name declared by @Logged annotation
	private static final ConcurrentHashMap<Class, String> fieldByEntity = new ConcurrentHashMap<>();


	public static XContentBuilder docBuilder(LogEvent logEvent, boolean includeLocation, boolean ignoreExceptions) throws IOException {
		XContentBuilder builder = XContentFactory.jsonBuilder().startObject();

		Message message = logEvent.getMessage();
		builder.field("message", message.getFormattedMessage());
		builder.field("level", logEvent.getLevel().name());
		builder.field("logger", logEvent.getLoggerName());
		builder.timeField("timestamp", new Date());
		builder.field("thread", logEvent.getThreadName());

		if (includeLocation) {
			StackTraceElement stackTraceElement = logEvent.getSource();
			builder.field("class", stackTraceElement.getClassName());
			builder.field("method", stackTraceElement.getMethodName());
			builder.field("line", stackTraceElement.getLineNumber());
		}

		Throwable error = message.getThrowable();
		if (error!=null) {
			builder.field("errorMessage", error.getLocalizedMessage());
			builder.field("errorType", error.getClass().getName());
			if (includeLocation) {
				builder.field("stack", Arrays.stream(error.getStackTrace())
								.map(ste -> Arrays.stream(new Object[][]{
												{"class", ste.getClassName()},
												{"method", ste.getMethodName()},
												{"line", ste.getLineNumber()},
												{"string", ste.toString()}
										}).collect(Collectors.toMap(f->(String)f[0], f->f[1]))
								).collect(Collectors.toList()) );
			}
		}

		Object[] parameters = message.getParameters();
		if (parameters!=null)
			Arrays.stream(parameters)
					.filter(Objects::nonNull)
					.map(p -> new AbstractMap.SimpleEntry<>(getFieldName(p), p))
					.filter(e -> e.getKey()!=null)
					.forEach(e -> {
						try {
							builder.rawField(e.getKey(), new ByteArrayInputStream(mapper.writeValueAsString(e.getValue()).getBytes()), XContentType.JSON);
						} catch (Exception ex) {
							LOGGER.error("Error logging into Elasticsearch for logger '"+logEvent.getLoggerName()+"' converting parameter '"+e.getKey()+"'",ex);
							if (!ignoreExceptions)
								throw new AppenderLoggingException(ex);
						}
					});

		return builder.endObject();
	}

	private static String getFieldName(Object entity) {
		String fieldName = fieldByEntity.get(entity.getClass());
		if (fieldName==null) {
			Logged loggedAnnotation = entity.getClass().getAnnotation(Logged.class);
			fieldByEntity.put(entity.getClass(), fieldName = loggedAnnotation!=null ? loggedAnnotation.value() : "");
		}
		return fieldName.length()>0 ? fieldName : null;
	}

	public static String toJson(Object obj) throws JsonProcessingException {
		return obj!=null ? mapper.writeValueAsString(obj) : null;
	}

}
