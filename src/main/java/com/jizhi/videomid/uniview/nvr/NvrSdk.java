package com.jizhi.videomid.uniview.nvr;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 宇视 NETDEV SDK 的录像查询。NVR 只使用 ONVIF 登录。
 * 结构体与 Java Demo 的 NETDEV_FILECOND_S、NETDEV_MONTH_INFO_S 一致。
 */
final class NvrSdk {
    private static final Logger log = LoggerFactory.getLogger(NvrSdk.class);
    /** glibc: RTLD_NOW | RTLD_GLOBAL，让后加载的主库能用到已打开依赖的 SONAME。 */
    private static final int RTLD_NOW_GLOBAL = 0x102;
    private static final String[] LINUX_DEPENDENCIES = {
            "libcrypto.so", "libssl.so", "libmxml.so", "libevent.so", "libpolarssl.so", "libcurl.so",
            "libCloudSDK.so", "libNDPlayer.so", "libNDRM.so", "libNetDiscovery.so", "libtunnel.so"
    };

    static final int NO_RECORDING = 10806;

    private static volatile LibraryApi API;

    private NvrSdk() {}

    static LibraryApi api(String libraryPath) {
        LibraryApi loaded = API;
        if (loaded != null) {
            return loaded;
        }
        synchronized (NvrSdk.class) {
            if (API == null) {
                String resolved = resolveLibrary(libraryPath);
                preloadDependencies(resolved);
                API = Native.load(withOriginRpath(resolved), LibraryApi.class);
                if (!API.NETDEV_Init()) {
                    int err = API.NETDEV_GetLastError();
                    API = null;
                    throw new IllegalStateException("NETDEV_Init 失败，错误码 " + err);
                }
            }
            return API;
        }
    }

    private static String resolveLibrary(String configured) {
        if (configured != null && !configured.isBlank()) {
            return libraryFile(configured.trim());
        }
        File win = new File("D:/工作/视频项目/宇视/NETDEVSDK_Win64_V2.8.0.0/dll/NetDEVSDK.dll");
        if (win.isFile()) {
            return win.getAbsolutePath();
        }
        File linux = new File("/videomid/lib/libNetDEVSDK.so");
        if (linux.isFile()) {
            return linux.getAbsolutePath();
        }
        throw new IllegalStateException("未找到 NetDEVSDK 动态库，请配置 uniview.nvr.library-path");
    }

    private static String libraryFile(String dirOrFile) {
        File file = new File(dirOrFile);
        if (file.isFile()) {
            return file.getAbsolutePath();
        }
        File dll = new File(file, "NetDEVSDK.dll");
        if (dll.isFile()) {
            return dll.getAbsolutePath();
        }
        File so = new File(file, "libNetDEVSDK.so");
        if (so.isFile()) {
            return so.getAbsolutePath();
        }
        throw new IllegalStateException("目录中没有 NetDEVSDK 动态库: " + file.getAbsolutePath());
    }

    /**
     * Linux 按 SONAME 找依赖（例如 libmxml.so.1），SDK 目录里实际文件是 libmxml.so。
     * 用绝对路径先 dlopen，进程里登记 SONAME 后再加载 libNetDEVSDK.so。
     */
    private static void preloadDependencies(String libraryPath) {
        if (!libraryPath.endsWith(".so")) {
            return;
        }
        File dir = new File(libraryPath).getAbsoluteFile().getParentFile();
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        Set<String> names = new LinkedHashSet<>();
        names.addAll(Arrays.asList(LINUX_DEPENDENCIES));
        File[] extras = dir.listFiles((folder, name) -> name.startsWith("lib") && name.endsWith(".so")
                && !"libNetDEVSDK.so".equals(name));
        if (extras != null) {
            for (File extra : extras) {
                names.add(extra.getName());
            }
        }
        publishToLinkerPath(dir);
        Dl libc = Native.load("c", Dl.class);
        for (String name : names) {
            File dep = new File(dir, name);
            if (!dep.isFile()) {
                continue;
            }
            libc.dlerror();
            Pointer handle = libc.dlopen(dep.getAbsolutePath(), RTLD_NOW_GLOBAL);
            if (handle == null) {
                log.warn("预加载 {} 失败: {}", dep.getAbsolutePath(), libc.dlerror());
            }
        }
    }

    /** NEEDED 名和磁盘文件名不一致时，补一个同名链接。已存在的系统库不覆盖。 */
    private static final String[][] SONAME_ALIASES = {
            {"libmxml.so.1", "libmxml.so"},
            {"libcrypto.so.3", "libcrypto.so"},
            {"libssl.so.3", "libssl.so"},
            {"libcurl.so.4", "libcurl.so"},
            {"libevent-2.1.so.7", "libevent.so"}
    };

    private static void publishToLinkerPath(File sdkDir) {
        for (String[] alias : SONAME_ALIASES) {
            linkIfAbsent(new File(sdkDir, alias[0]), new File(sdkDir, alias[1]));
        }
        File destDir = firstWritableDir(new File("/usr/lib64"), new File("/lib64"), new File("/usr/lib"), new File("/lib"));
        if (destDir == null) {
            log.warn("无法写入系统库目录。请用 LD_LIBRARY_PATH={} 启动进程", sdkDir.getAbsolutePath());
            return;
        }
        File[] libs = sdkDir.listFiles((folder, name) -> name.startsWith("lib") && name.endsWith(".so"));
        if (libs != null) {
            for (File lib : libs) {
                linkIfAbsent(new File(destDir, lib.getName()), lib);
            }
        }
        for (String[] alias : SONAME_ALIASES) {
            linkIfAbsent(new File(destDir, alias[0]), new File(sdkDir, alias[1]));
        }
    }

    private static File firstWritableDir(File... dirs) {
        for (File dir : dirs) {
            if (dir.isDirectory() && dir.canWrite()) {
                return dir;
            }
        }
        return null;
    }

    /**
     * libNetDiscovery.so 没有 SONAME，按绝对路径预加载登记不上这个名字。
     * 给主库补上 DT_RPATH=$ORIGIN，让它在自己所在目录里找依赖。不改原始 SDK 文件。
     */
    private static String withOriginRpath(String libraryPath) {
        if (!libraryPath.endsWith(".so")) {
            return libraryPath;
        }
        File source = new File(libraryPath);
        File patched = new File(source.getParentFile(), "libNetDEVSDK.linked.so");
        try {
            Files.copy(source.toPath(), patched.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            writeOriginRpath(patched);
        } catch (IOException ex) {
            throw new IllegalStateException("无法为 NetDEVSDK 写入库搜索路径: " + ex.getMessage(), ex);
        }
        return patched.getAbsolutePath();
    }

    private static void writeOriginRpath(File elf) throws IOException {
        byte[] origin = "$ORIGIN\0".getBytes(StandardCharsets.US_ASCII);
        try (RandomAccessFile file = new RandomAccessFile(elf, "rw")) {
            if (file.readByte() != 0x7f) {
                throw new IOException("不是 ELF 文件");
            }
            file.seek(32);
            long phoff = readU64(file);
            file.seek(54);
            int phentsize = readU16(file);
            int phnum = readU16(file);
            long dynOff = -1;
            long dynSize = 0;
            for (int i = 0; i < phnum; i++) {
                file.seek(phoff + (long) i * phentsize);
                int type = readU32(file);
                if (type != 2) {
                    continue;
                }
                file.seek(phoff + (long) i * phentsize + 8);
                dynOff = readU64(file);
                file.seek(phoff + (long) i * phentsize + 32);
                dynSize = readU64(file);
            }
            if (dynOff < 0) {
                throw new IOException("没有动态段");
            }
            long strtab = -1;
            int nullIndex = -1;
            int nullCount = 0;
            int count = (int) (dynSize / 16);
            for (int i = 0; i < count; i++) {
                file.seek(dynOff + (long) i * 16);
                long tag = readU64(file);
                long val = readU64(file);
                if (tag == 5) {
                    strtab = val;
                }
                if (tag == 0) {
                    if (nullIndex < 0) {
                        nullIndex = i;
                    }
                    nullCount++;
                }
            }
            if (strtab < 0 || nullIndex < 0 || nullCount < 3) {
                throw new IOException("动态段没有空位写入 RPATH");
            }
            long stringOff = dynOff + (long) (nullIndex + 2) * 16;
            if (origin.length > 16) {
                throw new IOException("RPATH 字符串过长");
            }
            file.seek(stringOff);
            file.write(origin);
            long stringVa = virtualAddress(file, phoff, phentsize, phnum, stringOff);
            long strtabVa = strtab;
            file.seek(dynOff + (long) nullIndex * 16);
            writeU64(file, 15);
            writeU64(file, stringVa - strtabVa);
        }
    }

    private static long virtualAddress(RandomAccessFile file, long phoff, int phentsize, int phnum, long fileOff) throws IOException {
        for (int i = 0; i < phnum; i++) {
            file.seek(phoff + (long) i * phentsize);
            if (readU32(file) != 1) {
                continue;
            }
            file.seek(phoff + (long) i * phentsize + 8);
            long off = readU64(file);
            long va = readU64(file);
            file.seek(phoff + (long) i * phentsize + 32);
            long filesz = readU64(file);
            if (fileOff >= off && fileOff < off + filesz) {
                return va + (fileOff - off);
            }
        }
        throw new IOException("地址不在可加载段内: " + fileOff);
    }

    private static int readU16(RandomAccessFile file) throws IOException {
        int b0 = file.read();
        int b1 = file.read();
        return b0 | (b1 << 8);
    }

    private static int readU32(RandomAccessFile file) throws IOException {
        long v = Integer.toUnsignedLong(readU16(file));
        return (int) (v | (Integer.toUnsignedLong(readU16(file)) << 16));
    }

    private static long readU64(RandomAccessFile file) throws IOException {
        long lo = Integer.toUnsignedLong(readU32(file));
        long hi = Integer.toUnsignedLong(readU32(file));
        return lo | (hi << 32);
    }

    private static void writeU64(RandomAccessFile file, long value) throws IOException {
        for (int i = 0; i < 8; i++) {
            file.write((int) (value & 0xff));
            value >>>= 8;
        }
    }

    private static void linkIfAbsent(File link, File target) {
        if (link.exists() || !target.isFile()) {
            return;
        }
        try {
            Files.createSymbolicLink(link.toPath(), target.toPath());
        } catch (IOException ex) {
            log.warn("建立库链接失败 {} -> {}: {}", link.getAbsolutePath(), target.getAbsolutePath(), ex.getMessage());
        }
    }

    interface Dl extends Library {
        Pointer dlopen(String filename, int flags);
        String dlerror();
    }

    interface LibraryApi extends Library {
        boolean NETDEV_Init();
        Pointer NETDEV_Login_V30(LoginInfo info, SeLogInfo se);
        boolean NETDEV_Logout(Pointer userId);
        int NETDEV_GetLastError();
        Pointer NETDEV_FindFile(Pointer userId, FileCond cond);
        boolean NETDEV_FindNextFile(Pointer findHandle, FindData data);
        boolean NETDEV_FindClose(Pointer findHandle);
        boolean NETDEV_QuickSearch(Pointer userId, int channelId, MonthInfo month, MonthStatus status);
        boolean NETDEV_QueryVideoChlDetailListEx(Pointer userId, com.sun.jna.ptr.IntByReference count, ChannelInfo[] list);
        boolean NETDEV_GetReplayUrl_V30(Pointer userId, PlaybackCond cond, byte[] url);
        boolean NETDEV_GetPlaybackUrl(Pointer userId, RecordFindCond cond, byte[] url);
        Pointer NETDEV_GetFileByTime(Pointer userId, PlaybackCond cond, byte[] savePath, int format);
        boolean NETDEV_StopGetFile(Pointer playHandle);
        boolean NETDEV_PlayBackControl(Pointer playHandle, int controlCode, Pointer buffer);
    }

    /** 与 Linux SDK 2.8.1 的 NETDEV_PLAYBACKCOND_S 一致。Demo 回放用这组字段取 RTSP。 */
    public static class PlaybackCond extends Structure {
        public int dwChannelID;
        public long tBeginTime;
        public long tEndTime;
        public int dwLinkMode;
        public Pointer hPlayWnd;
        public int dwFileType;
        public int dwDownloadSpeed;
        public int dwStreamMode;
        public int dwStreamIndex;
        public int dwRecordLocation;
        public int dwTransType;
        public int bCloudStorage;
        public int bOneFrameEnable;
        public int dwPlaySpeed;
        public Pointer cbPlayDecodeVideoCALLBACK;
        public long tPlayTime;
        public int udwServerID;
        public int udwStreamID;
        public int bStreamIDEnable;
        public int udwRelationOfTypes;
        public int udwPosition;
        public int udwSessionID;
        public byte[] byRes = new byte[192];

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList(
                    "dwChannelID", "tBeginTime", "tEndTime", "dwLinkMode", "hPlayWnd",
                    "dwFileType", "dwDownloadSpeed", "dwStreamMode", "dwStreamIndex", "dwRecordLocation",
                    "dwTransType", "bCloudStorage", "bOneFrameEnable", "dwPlaySpeed", "cbPlayDecodeVideoCALLBACK",
                    "tPlayTime", "udwServerID", "udwStreamID", "bStreamIDEnable", "udwRelationOfTypes",
                    "udwPosition", "udwSessionID", "byRes");
        }
    }

    /** NETDEV_RECORD_FIND_COND_S，GetPlaybackUrl 的时间范围条件。时间单位秒。 */
    public static class RecordFindCond extends Structure {
        public int udwChannelID;
        public int udwBegin;
        public int udwEnd;
        public int udwTypes;
        public int udwRelationOfTypes;
        public int udwPosition;
        public int udwSessionID;
        public int udwTransType;
        public int udwStreamID;
        public int bStreamIDEnable;
        public byte[] byRes = new byte[248];

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList(
                    "udwChannelID", "udwBegin", "udwEnd", "udwTypes", "udwRelationOfTypes",
                    "udwPosition", "udwSessionID", "udwTransType", "udwStreamID", "bStreamIDEnable", "byRes");
        }
    }

    public static class LoginInfo extends Structure {
        public byte[] szIPAddr = new byte[260];
        public int dwPort;
        public byte[] szUserName = new byte[132];
        public byte[] szPassword = new byte[128];
        public int dwLoginProto;
        public int dwDeviceType;
        public byte[] byRes = new byte[256];

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("szIPAddr", "dwPort", "szUserName", "szPassword",
                    "dwLoginProto", "dwDeviceType", "byRes");
        }
    }

    public static class SeLogInfo extends Structure {
        public int dwSELogCount;
        public int dwSELogTime;
        public byte[] byRes = new byte[64];

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("dwSELogCount", "dwSELogTime", "byRes");
        }
    }

    public static class FileCond extends Structure {
        public byte[] szFileName = new byte[64];
        public int dwChannelID;
        public int dwStreamType;
        public int dwFileType;
        public long tBeginTime;
        public long tEndTime;
        public int dwRecordLocation;
        public int udwServerID;
        public byte[] byRes = new byte[28];

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("szFileName", "dwChannelID", "dwStreamType", "dwFileType",
                    "tBeginTime", "tEndTime", "dwRecordLocation", "udwServerID", "byRes");
        }
    }

    public static class FindData extends Structure {
        public byte[] szFileName = new byte[256];
        public long tBeginTime;
        public long tEndTime;
        public byte byFileType;
        public int udwServerID;
        public int udwFileSize;
        public int dwFileType;
        public byte[] byRes = new byte[159];

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("szFileName", "tBeginTime", "tEndTime", "byFileType",
                    "udwServerID", "udwFileSize", "dwFileType", "byRes");
        }
    }

    public static class MonthInfo extends Structure {
        public int udwYear;
        public int udwMonth;
        public int udwPosition;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("udwYear", "udwMonth", "udwPosition");
        }
    }

    public static class MonthStatus extends Structure {
        public int udwDayNumInMonth;
        public int[] szVideoStatus = new int[31];

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("udwDayNumInMonth", "szVideoStatus");
        }
    }

    public static class ChannelInfo extends Structure {
        public int dwChannelID;
        public int bPtzSupported;
        public int enStatus;
        public int dwStreamNum;
        public int enChannelType;
        public int enVideoFormat;
        public int enAddressType;
        public byte[] szIPAddr = new byte[64];
        public int dwPort;
        public byte[] szChnName = new byte[64];
        public int allowDistribution;
        public int dwDeviceType;
        public byte[] szManufacturer = new byte[32];
        public byte[] szDeviceModel = new byte[32];
        public int udwAccessProtocol;
        public Pointer pstExtendedInformation;
        public byte[] byRes = new byte[16];

        public ChannelInfo() {}

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("dwChannelID", "bPtzSupported", "enStatus", "dwStreamNum",
                    "enChannelType", "enVideoFormat", "enAddressType", "szIPAddr", "dwPort",
                    "szChnName", "allowDistribution", "dwDeviceType", "szManufacturer",
                    "szDeviceModel", "udwAccessProtocol", "pstExtendedInformation", "byRes");
        }
    }
}
