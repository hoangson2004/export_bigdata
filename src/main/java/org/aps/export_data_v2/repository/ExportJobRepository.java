package org.aps.export_data_v2.repository;

import org.aps.export_data_v2.constant.ExportStatus;
import org.aps.export_data_v2.entity.ExportBatch;
import org.aps.export_data_v2.entity.ExportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExportJobRepository extends JpaRepository<ExportJob, Long> {
    Optional<ExportJob> findByJobUniqueId(String jobUniqueId);

    List<ExportJob> findByRequestedByAndStatus(String requestedBy, ExportStatus status);

    @Query("SELECT e FROM ExportJob e WHERE e.status = :status AND e.createdAt < :timeout")
    List<ExportJob> findStuckJobs(@Param("status") ExportStatus status, @Param("timeout") LocalDateTime timeout);
}
