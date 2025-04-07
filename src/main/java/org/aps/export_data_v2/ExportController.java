    package org.aps.export_data_v2;

    import lombok.RequiredArgsConstructor;
    import org.aps.export_data_v2.entity.ExportJob;
    import org.springframework.core.io.FileSystemResource;
    import org.springframework.core.io.Resource;
    import org.springframework.http.HttpHeaders;
    import org.springframework.http.MediaType;
    import org.springframework.http.ResponseEntity;
    import org.springframework.web.bind.annotation.*;

    import java.nio.file.Path;
    import java.nio.file.Paths;
    import java.security.Principal;
    import java.util.HashMap;
    import java.util.Map;

    @RestController
    @RequestMapping("/api/exports")
    @RequiredArgsConstructor
    public class ExportController {

        private final ExportExcelService exportExcelService;

        @PostMapping("/salaries")
        public ResponseEntity<Map<String, Object>> exportSalaries() {

            ExportJob job = exportExcelService.createSalaryExportJob();
            exportExcelService.processExportJob(job.getJobUniqueId());

            Map<String, Object> response = new HashMap<>();
            response.put("jobId", job.getJobUniqueId());
            response.put("status", job.getStatus().toString());
            response.put("totalBatches", job.getTotalBatches());
            response.put("totalRecords", job.getTotalRecords());

            return ResponseEntity.ok(response);
        }

        @GetMapping("/{jobUniqueId}")
        public ResponseEntity<Map<String, Object>> getExportStatus(@PathVariable String jobUniqueId) {
            ExportJob job = exportExcelService.getJobStatus(jobUniqueId);

            Map<String, Object> response = new HashMap<>();
            response.put("jobId", job.getJobUniqueId());
            response.put("status", job.getStatus().toString());
            response.put("totalBatches", job.getTotalBatches());
            response.put("processedBatches", job.getProcessedBatches());
            response.put("totalRecords", job.getTotalRecords());
            response.put("completed", job.getCompletedAt() != null);
            if (job.getResultFileUrl() != null) {
                response.put("downloadUrl", "/api/exports/" + job.getJobUniqueId() + "/download");
            }

            return ResponseEntity.ok(response);
        }

        @PostMapping("/{jobUniqueId}/retry")
        public ResponseEntity<Map<String, String>> retryFailedBatches(@PathVariable String jobUniqueId) {
            exportExcelService.retryFailedBatches(jobUniqueId);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Retry requested for failed batches");

            return ResponseEntity.accepted().body(response);
        }

        @GetMapping("/{jobUniqueId}/download")
        public ResponseEntity<Resource> downloadExportFile(@PathVariable String jobUniqueId) {
            ExportJob job = exportExcelService.getJobStatus(jobUniqueId);

            if (job.getResultFileUrl() == null) {
                return ResponseEntity.notFound().build();
            }

            Path path = Paths.get(job.getResultFileUrl());
            Resource resource = new FileSystemResource(path.toFile());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"export_" + jobUniqueId + ".xlsx\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        }
    }