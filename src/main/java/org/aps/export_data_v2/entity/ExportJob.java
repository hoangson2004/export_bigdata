package org.aps.export_data_v2.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aps.export_data_v2.constant.ExportStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "export_jobs")
public class ExportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_unique_id", unique = true, nullable = false)
    private String jobUniqueId;

    @Column(name = "requested_by")
    private String requestedBy;

    @Column(name = "export_type")
    private String exportType;

    @Column(name = "parameters", columnDefinition = "TEXT")
    private String parameters;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ExportStatus status;

    @Column(name = "result_file_url")
    private String resultFileUrl;

    @Column(name = "total_batches")
    private Integer totalBatches;

    @Column(name = "processed_batches")
    private Integer processedBatches;

    @Column(name = "total_records")
    private Integer totalRecords;

    @OneToMany(mappedBy = "exportJob", cascade = CascadeType.ALL)
    private List<ExportBatch> batches = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.status = ExportStatus.PENDING;
        this.processedBatches = 0;
    }
}