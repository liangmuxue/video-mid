package com.jizhi.videomid.uniview.nvr;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "uniview.nvr")
public class NvrProperties {
    /** NetDEVSDK 动态库所在目录。空则自动找本机 Windows SDK 或服务器 /videomid/lib。 */
    private String libraryPath = "";

    public String getLibraryPath() { return libraryPath; }
    public void setLibraryPath(String libraryPath) { this.libraryPath = libraryPath; }
}
