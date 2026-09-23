package com.jizhi.videomid.biz.dto;

public class BizDeviceListRequest {
    private Long folderId;
    private Boolean includeChildren;

    public Long getFolderId() { return folderId; }
    public void setFolderId(Long folderId) { this.folderId = folderId; }
    public Boolean getIncludeChildren() { return includeChildren; }
    public void setIncludeChildren(Boolean includeChildren) { this.includeChildren = includeChildren; }
}
