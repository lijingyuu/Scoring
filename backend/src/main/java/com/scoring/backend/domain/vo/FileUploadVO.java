package com.scoring.backend.domain.vo;

public class FileUploadVO {

    private String url;

    public FileUploadVO() {
    }

    public FileUploadVO(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
