package org.aps.export_data_v2.constant;

public enum ExportStatus {
    PENDING("Đang chờ xử lý"),
    IN_PROGRESS("Đang xử lý"),
    COMPLETED("Hoàn thành"),
    FAILED("Thất bại"),
    PARTIALLY_COMPLETED("Hoàn thành một phần");

    private final String description;

    ExportStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
