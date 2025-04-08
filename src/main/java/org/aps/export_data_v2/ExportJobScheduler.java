package org.aps.export_data_v2;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aps.export_data_v2.constant.ExportStatus;
import org.aps.export_data_v2.entity.ExportJob;
import org.aps.export_data_v2.repository.ExportJobRepository;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@EnableScheduling
public class ExportJobScheduler {

    private final ExportJobRepository exportJobRepository;
    private final ExportExcelService exportExcelService;

    @Scheduled(fixedRate = 100000)
    public void recoverStuckJobs() {
        log.info("Bắt đầu quá trình khôi phục các job bị treo");

        List<ExportStatus> listStatus = new ArrayList<>();
        listStatus.add(ExportStatus.FAILED);
        listStatus.add(ExportStatus.IN_PROGRESS);
        listStatus.add(ExportStatus.PENDING);
        ;
        List<ExportJob> stuckJobs = exportJobRepository.findStuckJobs(listStatus);

        for (ExportJob job : stuckJobs) {
            log.info("Đang thử khôi phục job bị treo: {}", job.getJobUniqueId());
            exportExcelService.retryFailedBatches(job.getJobUniqueId());
        }
    }



}