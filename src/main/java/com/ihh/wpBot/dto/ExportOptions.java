package com.ihh.wpBot.dto;

import com.ihh.wpBot.model.DeliveryStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public class ExportOptions {

    private DeliveryStatus status;
    private LocalDateTime sinceDate;
    private List<String> failureCodes;
    private String templateName;
    private String phoneSearch;
    private String contactNameSearch;
    private Set<ExportColumn> columns;
    private SortBy sortBy = SortBy.SENT_AT_DESC;

    public ExportOptions() {
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public void setStatus(DeliveryStatus status) {
        this.status = status;
    }

    public LocalDateTime getSinceDate() {
        return sinceDate;
    }

    public void setSinceDate(LocalDateTime sinceDate) {
        this.sinceDate = sinceDate;
    }

    public List<String> getFailureCodes() {
        return failureCodes;
    }

    public void setFailureCodes(List<String> failureCodes) {
        this.failureCodes = failureCodes;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getPhoneSearch() {
        return phoneSearch;
    }

    public void setPhoneSearch(String phoneSearch) {
        this.phoneSearch = phoneSearch;
    }

    public String getContactNameSearch() {
        return contactNameSearch;
    }

    public void setContactNameSearch(String contactNameSearch) {
        this.contactNameSearch = contactNameSearch;
    }

    public Set<ExportColumn> getColumns() {
        return columns;
    }

    public void setColumns(Set<ExportColumn> columns) {
        this.columns = columns;
    }

    public SortBy getSortBy() {
        return sortBy;
    }

    public void setSortBy(SortBy sortBy) {
        this.sortBy = sortBy;
    }
}