# log4j-2-elastic
Build status: [![build_status](https://travis-ci.org/ivanocortesini/log4j-2-elastic.svg?branch=master)](https://travis-ci.org/ivanocortesini/log4j-2-elastic)

I've created this project to share and enhance a [Log4j2](https://logging.apache.org/log4j/2.x/) appender that logs messages directly into an [Elasticsearch](https://www.elastic.co/products/elasticsearch) cluster.
This approach to log aggregation into Elasticsearch can be a good alternative to Elastic Beats in some specific scenario.

## Features
This product includes some standard and some particular features:
* Take advantage of Log4j2 asynchronous logging with bulk store requests. Batch mode is recognized automatically. A timeout based buffer flush can be configured.
* Store @Logged annotated log message parameter objects as embedded JSON fields into the log document stored in Elasticsearch.
* Connection to multiple cluster nodes.
* Basic authentication.
* Log4j "ignoreExceptions" option support.
* Log4j "includeLocation" option support.
* Log4j layout and filters support.

## Getting Started

If you want to create your own local development environment you only need to import this code in your preferred IDE as a standard Maven project.

### Prerequisites

* git
* Java 8+ JDK
* Maven 3+
* Elasticsearch 6.6+
* Your preferred IDE 

## Running the tests

For this project I've included a small set of JUnit 5 unit tests to verify the working status of main use cases.
To run those tests you need to update "log4j2.xml" and "elasticsearch.properties" configuration files to connect to an up and running Elasticsearch node or cluster.

## Deployment

The simplest way to use this appender in a Java application is:
* include this Maven dependency in your pom.xml file:
```
<dependency>
    <groupId>com.github.ivanocortesini</groupId>
    <artifactId>log4j-2-elastic</artifactId>
    <version>1.0.0</version>
</dependency>
```
* include an "Elastic" type appender into your log4j2 configuration like in the following example:
```
<Elastic
        name="example-elastic-appender"
        cluster="http://host-1:9200 http://host-2:9200"
        index="example-log-index"
        flushTimeOut="60"
        ignoreExceptions="true"
        username="usr",
        password="psw"
        >
    <PatternLayout pattern="%m%n"/>
</Elastic>
```

### Configuration
In the table below are summarized available attribute parameters (all optional except "name"):

Parameter | Type | Default | Description
---|---|---|---
name | String | | Appender name
cluster | String | http://localhost:9200 | Elasticsearch cluster nodes URL list (blank or comma separated) 
index | String | Appender name | Elasticsearch destination index 
flushTimeOut | Integer | 0 | Timeout (in seconds) to flush the current log messages bulk transmission. Timeout refers to bulk messages creation time. The timer is active if value is greater than 0.  
ignoreExceptions | Boolean | true | If value is "false" the appender throw internal errors to the caller method. If value is "true" the appender try to use default logger to log internal error message.
username | String | | Username for basic authentication (if required)
password | String | | Password for basic authentication (if required)

For performance purposes I really recommend to use this appender with asynchronous loggers like in the example below:
```
<?xml version="1.0" encoding="UTF-8"?>

<Configuration status="WARN" name="example-configuration" packages="">
    <Appenders>

        <Elastic
                name="example-elastic-appender"
                cluster="http://localhost:9200"
                index="example-log-index"
                flushTimeOut="60"
                ignoreExceptions="true"
                username="usr"
                password="psw" >
            <PatternLayout pattern="%m%n"/>
        </Elastic>

        <Console name="STDOUT" target="SYSTEM_OUT">
            <PatternLayout pattern="%m%n"/>
        </Console>

    </Appenders>

    <Loggers>
        <AsyncLogger name="example-logger" level="info" includeLocation="true" additivity="false">
            <AppenderRef ref="example-elastic-appender"/>
        </AsyncLogger>
        <Root level="info" includeLocation="true">
            <AppenderRef ref="STDOUT"/>
        </Root>
    </Loggers>
</Configuration>
```
### @Logged
@Logged annotation gives the possibility to add multiple objects data into a log message in the form of embedded fields inside the document stored in Elasticsearch. Below there's an example POJO (I've used [Lombok](https://github.com/rzwitserloot/lombok) here) annotated with @Logged.

```
@Data @AllArgsConstructor
@Logged("data-example")
public class DataExample {

    private String title;
    private String address;
    private Date creationTime;
    private List<String> phone;

}
```
This annotation requires to specify the name of the destination field for wich you should **avoid to use reserved field names**:
message, level, logger, timestamp, thread, class, method, line, errorMessage, errorType, stack.

Actually dates are serialized in standard Elasticsearch [date_time](https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping-date-format.html) format but you must include a document schema to save and manage them as real dates into Elasticsearch storage. 
 
### Usage details and considerations
When you produce a log message a correspondent JSON document will be generated and stored into Elasticsearch. So the code below (Log4j2 support [lambda expressions](https://logging.apache.org/log4j/2.x/manual/api.html)):

```
logger.info("Single message: {} {}", 
        ()->"param value",
        ()->2,
        ()->new DataExample("title", "address", LocalDateTime.now(), Arrays.asList("0039061111111", "0039062222222"))
);
```

will produce a document like:

```
{
    "_index":"example-log-index",
    "_type":"doc",
    "_id":"nSQS3GgBLia8gni1U7cV",
    "_score":1.0,
    "_source":{
       "message":"Single message: param value 2",
       "level":"INFO",
       "logger":"example-logger",
       "timestamp":"2019-02-11T10:20:17.793Z",
       "thread":"main",
       "class":"tech.elasticlog.appender.ElasticAppenderTest",
       "method":"appendSingleWithLambda",
       "line":75,
       "data-example":{
          "title":"title",
          "address":"address",
          "creationTime":"2019-02-11T11:20:17.577+0100",
          "phone":[
             "0039061111111",
             "0039062222222"
          ]
       }
    }
}
```
If you log an exception, other fields will be added:

```
...
"errorMessage" : "<exception localized error message>",
"errorType" : "<exception class full name>",
"stack" : [{
               "class":"<stacktrace element class name>",
               "method":"<stacktrace element method name>",
               "line": <stacktrace element line number>
            }],
...
```
Stack, class, method and line fields will be present only when logger "includeLocation" attribute is set to true. Take into account that location inclusion consumes time and resources.

Depending on Log4j configuration, generated documents will be stored immediately (with synchronous loggers) or in bulk (with asynchronous loggers). This appender autodetect if the logger require the usage of single message or bulk transmission. It's important to **avoid to use the same "Elastic" appender instance for mixed asynchronous and synchronous loggers**.

If you want to be 100% sure to avoid log messages loss you can use synchronous logger and set "ignoreExceptions" to false.
However this approach can bring performance issues. Even using asynchronous loggers, if an Elasticsearch cluster of 3+ nodes is available you should have enough guarantees and in any case you can add some other appender to provide an information backup.

The Elasticsearch destination **index will be created automatically** (if not present) during appender startup phase. Any change you wanto to make in this sense requires to update Log4j configuration and restart your application.

The bulk documents buffer flush timeout is checked every 5 seconds. So if you set a timeout of 30 seconds you can expect that the document buffer will be flushed in less than 35 seconds by the logger (that can close the log batch) or by the timeout manager (after 30 seconds from batch creation).
Log4j2 has an algorithm to dynamically define the batch size based on log activities frequency and usually it close the batch in few seconds. However you can specify buffer size by setting speficic [system parameters](). "flushTimeOut" parameter have no effect if the appender is included in a synchronous logger.    

## Built With

[Maven](https://maven.apache.org/) - Dependency Management

## Contributing

This is a tiny product with a simple and clean design and minimum external dependencies. The main components are:
* A standard Log4j2 appender implementation.
* An Elasticsearch client component based on Elasticsearch high level Java client.
* A log message content translator enhanced with parameters JSON encoding with Jackson. 
  
Any kind of contribution will be appreciated. You can contact me by email or open issues or pull requests for bugs, enhancements and suggestions about any aspect of this product (e.g. architectural considerations, code design, performance, etc...).  

I've included a standard [CONTRIBUTING](CONTRIBUTING.md) file for details on code of conduct, and process for submitting pull requests.

## Versioning

I use [SemVer](http://semver.org/) for versioning. For the versions available, see the [tags on this repository](https://github.com/ivanocortesini/log4j-2-elastic/tags). 

## Authors

* **IvanoCortesini** ([Github](https://github.com/ivanocortesini) - [LinkedIn](https://www.linkedin.com/in/ivanocortesini/))

See also the list of [contributors](https://github.com/ivanocortesini/log4j-2-elastic/contributors) who participated in this project.

## License

This project is licensed under the Apache License 2.0 License - see the [LICENSE](LICENSE) file for details

