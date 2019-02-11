package tech.elasticlog.appender.dto;

import tech.elasticlog.appender.Logged;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Data @AllArgsConstructor
@Logged("data-example")
public class DataExample {

    private String title;
    private String address;
    private Date creationTime;
    private List<String> phone;

}
