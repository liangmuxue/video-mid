package com.jizhi.videomid.device.dto;

public class DeviceFolderRequest {
    private Long parentId;
    private String name;
    private Integer sortNo;

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
}
