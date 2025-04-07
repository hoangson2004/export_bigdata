package org.aps.export_data_v2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;


@SpringBootApplication
public class ExportDataV2Application {

    public static void main(String[] args) {
        SpringApplication.run(ExportDataV2Application.class, args);
    }

}
