package com.github.ivanocortesini.log4j.elastic.appender.dto;

import com.github.ivanocortesini.log4j.elastic.appender.Logged;
import lombok.AllArgsConstructor;
import lombok.Data;

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
