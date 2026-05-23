package com.ihh.wpBot.dto;

import java.util.List;

public class ExportOptionsRequest {

    private String status;
    private Integer days;
    private List<String> failureCodes;
    private String templateName;
    private String phoneSearch;
    private String contactNameSearch;
    private List<String> columns;
    private String sortBy;

    public ExportOptionsRequest() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getDays() {
        return days;
    }

    public void setDays(Integer days) {
        this.days = days;
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

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }
}