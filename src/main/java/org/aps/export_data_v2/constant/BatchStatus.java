package org.aps.export_data_v2.constant;

public enum BatchStatus {
    PENDING("Đang chờ xử lý"),
    IN_PROGRESS("Đang xử lý"),
    COMPLETED("Hoàn thành"),
    FAILED("Thất bại");

    private final String description;

    BatchStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
