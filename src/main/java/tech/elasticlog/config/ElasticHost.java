package tech.elasticlog.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.net.MalformedURLException;
import java.net.URL;

@Data @AllArgsConstructor @NoArgsConstructor @EqualsAndHashCode
public class ElasticHost {

    private String protocol;
    private String host;
    private Integer port;

    public ElasticHost(String hostString) throws MalformedURLException {
        URL hostUrl = new URL(hostString);
        protocol = hostUrl.getProtocol();
        host = hostUrl.getHost();
        port = hostUrl.getPort();
    }

    public String toString() {
        return protocol+"://"+host+':'+port;
    }
}
