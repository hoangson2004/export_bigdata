package org.aps.export_data_v2.repository;

import org.aps.export_data_v2.constant.BatchStatus;
import org.aps.export_data_v2.entity.ExportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExportBatchRepository extends JpaRepository<ExportBatch, Long> {
    List<ExportBatch> findByExportJobIdAndStatus(Long jobId, BatchStatus status);


    @Query("SELECT b FROM ExportBatch b WHERE b.exportJob.id = :jobId AND b.status IN :status AND b.retryCount < :maxRetries")
    List<ExportBatch> findBatchesForRetry(
            @Param("jobId") Long jobId,
            @Param("status") List<BatchStatus> status,
            @Param("maxRetries") Integer maxRetries
    );

    @Query("SELECT COUNT(b) FROM ExportBatch b WHERE b.exportJob.id = :jobId AND b.status = :status")
    Integer countBatchesByJobIdAndStatus(@Param("jobId") Long jobId, @Param("status") BatchStatus status);
}