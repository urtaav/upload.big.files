package com.videoflow.upload;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the upload orchestration API.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class UploadApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(UploadApiApplication.class, args);
    }
}