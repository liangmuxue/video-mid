#include <dlfcn.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

typedef struct {
    char szIPAddr[260];
    int32_t dwPort;
    char szUserName[132];
    char szPassword[128];
    int32_t dwLoginProto;
    int32_t dwDeviceType;
    unsigned char byRes[256];
} NETDEV_DEVICE_LOGIN_INFO_S;

typedef struct {
    int32_t dwSELogCount;
    int32_t dwSELogTime;
    unsigned char byRes[64];
} NETDEV_SELOG_INFO_S;

typedef struct {
    char szFileName[64];
    int32_t dwChannelID;
    int32_t dwStreamType;
    int32_t dwFileType;
    int64_t tBeginTime;
    int64_t tEndTime;
    int32_t dwRecordLocation;
    uint32_t udwServerID;
    unsigned char byRes[28];
} NETDEV_FILECOND_S;

typedef struct {
    char szFileName[64];
    int32_t dwChannelID;
    int32_t dwStreamType;
    int32_t dwFileType;
    int64_t tBeginTime;
    int64_t tEndTime;
    int32_t dwRecordLocation;
    uint32_t udwServerID;
    uint32_t udwRelationOfType;
    unsigned char byRes[24];
} NETDEV_FILECOND_V40_S;

typedef struct {
    char szFileName[256];
    int64_t tBeginTime;
    int64_t tEndTime;
    unsigned char byFileType;
    uint32_t udwServerID;
    uint32_t udwFileSize;
    uint32_t dwFileType;
    uint32_t udwRecordTypes;
    unsigned char byRes[155];
} NETDEV_FINDDATA_S;

typedef int (*InitFn)(void);
typedef int (*CleanupFn)(void);
typedef void *(*LoginFn)(NETDEV_DEVICE_LOGIN_INFO_S *, NETDEV_SELOG_INFO_S *);
typedef int (*LogoutFn)(void *);
typedef int (*LastErrorFn)(void);
typedef int (*SetConnectTimeFn)(int32_t, int32_t);
typedef void *(*FindFileFn)(void *, NETDEV_FILECOND_S *);
typedef void *(*FindFileV40Fn)(void *, NETDEV_FILECOND_V40_S *, void *, void *);
typedef int (*GetVideoDayNumsFn)(void *, int32_t, uint32_t *);
typedef int (*FindNextFn)(void *, NETDEV_FINDDATA_S *);
typedef int (*FindCloseFn)(void *);

static LogoutFn g_logout;

static void print_time(const char *label, int64_t ts) {
    time_t t = (time_t)ts;
    struct tm tm_buf;
    char text[32];
    if (localtime_r(&t, &tm_buf) == NULL) {
        printf("%s=%lld", label, (long long)ts);
        fflush(stdout);
        return;
    }
    strftime(text, sizeof(text), "%Y-%m-%d %H:%M:%S", &tm_buf);
    printf("%s=%s", label, text);
    fflush(stdout);
}

static void *try_login(LoginFn login, LastErrorFn last_error, const char *ip, int port,
                       const char *user, const char *password, int proto) {
    NETDEV_DEVICE_LOGIN_INFO_S info;
    NETDEV_SELOG_INFO_S se;
    memset(&info, 0, sizeof(info));
    memset(&se, 0, sizeof(se));
    snprintf(info.szIPAddr, sizeof(info.szIPAddr), "%s", ip);
    snprintf(info.szUserName, sizeof(info.szUserName), "%s", user);
    snprintf(info.szPassword, sizeof(info.szPassword), "%s", password);
    info.dwPort = port;
    info.dwLoginProto = proto;
    info.dwDeviceType = 0;

    printf("login try proto=%d\n", proto);
    fflush(stdout);
    void *handle = login(&info, &se);
    if (handle != NULL) {
        printf("login ok proto=%d handle=%p\n", proto, handle);
        fflush(stdout);
        return handle;
    }
    printf("login fail proto=%d lastError=%d\n", proto, last_error());
    fflush(stdout);
    return NULL;
}

static int drain(const char *api, FindNextFn find_next, FindCloseFn find_close,
                 void *find, int channel, int stream_type) {
    int count = 0;
    NETDEV_FINDDATA_S data;
    while (count < 5) {
        memset(&data, 0, sizeof(data));
        if (!find_next(find, &data)) {
            break;
        }
        count++;
        printf("%s record channel=%d stream=%d file=%s ", api, channel, stream_type, data.szFileName);
        print_time("begin", data.tBeginTime);
        printf(" ");
        print_time("end", data.tEndTime);
        printf(" size=%u\n", data.udwFileSize);
        fflush(stdout);
    }
    if (find_close) {
        find_close(find);
    }
    printf("%s channel %d stream %d recordCount=%d\n", api, channel, stream_type, count);
    fflush(stdout);
    return count;
}

static int query_findfile(const char *api, FindFileFn find_file, FindNextFn find_next, FindCloseFn find_close,
                          LastErrorFn last_error, void *user, int channel, int64_t begin, int64_t end) {
    NETDEV_FILECOND_S cond;
    memset(&cond, 0, sizeof(cond));
    cond.dwChannelID = channel;
    cond.dwFileType = 0;
    cond.tBeginTime = begin;
    cond.tEndTime = end;

    printf("%s query channel=%d fileType=0\n", api, channel);
    fflush(stdout);
    void *find = find_file(user, &cond);
    if (find == NULL) {
        printf("%s channel %d find none lastError=%d\n", api, channel, last_error());
        fflush(stdout);
        return 0;
    }
    return drain(api, find_next, find_close, find, channel, 0);
}

static void query_day_nums(GetVideoDayNumsFn get_days, LastErrorFn last_error, void *user, int channel) {
    uint32_t days = 0;
    if (!get_days) {
        printf("GetVideoDayNums missing\n");
        fflush(stdout);
        return;
    }
    if (get_days(user, channel, &days)) {
        printf("GetVideoDayNums channel=%d days=%u\n", channel, days);
    } else {
        printf("GetVideoDayNums channel=%d fail lastError=%d\n", channel, last_error());
    }
    fflush(stdout);
}

static int query_v30(FindFileFn find_file, FindNextFn find_next, FindCloseFn find_close,
                     LastErrorFn last_error, void *user, int channel, int64_t begin, int64_t end) {
    NETDEV_FILECOND_S cond;
    memset(&cond, 0, sizeof(cond));
    cond.dwChannelID = channel;
    cond.dwStreamType = 0;
    cond.dwFileType = 0;
    cond.tBeginTime = begin;
    cond.tEndTime = end;
    cond.dwRecordLocation = 0;

    printf("FindFile_V30 query channel=%d\n", channel);
    fflush(stdout);
    void *find = find_file(user, &cond);
    if (find == NULL) {
        printf("FindFile_V30 channel %d find none lastError=%d\n", channel, last_error());
        fflush(stdout);
        return 0;
    }
    return drain("FindFile_V30", find_next, find_close, find, channel, 0);
}

static int query_v40(FindFileV40Fn find_file, FindNextFn find_next, FindCloseFn find_close,
                     LastErrorFn last_error, void *user, int channel, int64_t begin, int64_t end) {
    NETDEV_FILECOND_V40_S cond;
    memset(&cond, 0, sizeof(cond));
    cond.dwChannelID = channel;
    cond.dwStreamType = 0;
    cond.dwFileType = 0;
    cond.tBeginTime = begin;
    cond.tEndTime = end;
    cond.dwRecordLocation = 0;
    cond.udwServerID = 0xFFFFFFF;
    cond.udwRelationOfType = 0xFFFFFFF;

    printf("FindFile_V40 query channel=%d\n", channel);
    fflush(stdout);
    void *find = find_file(user, &cond, NULL, NULL);
    if (find == NULL) {
        printf("FindFile_V40 channel %d find none lastError=%d\n", channel, last_error());
        fflush(stdout);
        return 0;
    }
    return drain("FindFile_V40", find_next, find_close, find, channel, 0);
}

int main(int argc, char **argv) {
    const char *ip = argc > 1 ? argv[1] : "39.185.236.176";
    int port = argc > 2 ? atoi(argv[2]) : 10135;
    const char *user_name = argc > 3 ? argv[3] : "guest";
    const char *password = argc > 4 ? argv[4] : "*Guest321";

    printf("probe start %s:%d\n", ip, port);
    fflush(stdout);

    void *lib = dlopen("libNetDEVSDK.so", RTLD_NOW);
    if (!lib) {
        fprintf(stderr, "dlopen failed: %s\n", dlerror());
        return 1;
    }

    InitFn init = (InitFn)dlsym(lib, "NETDEV_Init");
    CleanupFn cleanup = (CleanupFn)dlsym(lib, "NETDEV_Cleanup");
    LoginFn login = (LoginFn)dlsym(lib, "NETDEV_Login_V30");
    g_logout = (LogoutFn)dlsym(lib, "NETDEV_Logout");
    LastErrorFn last_error = (LastErrorFn)dlsym(lib, "NETDEV_GetLastError");
    SetConnectTimeFn set_connect = (SetConnectTimeFn)dlsym(lib, "NETDEV_SetConnectTime");
    FindFileFn find_file = (FindFileFn)dlsym(lib, "NETDEV_FindFile");
    FindFileFn find_v30 = (FindFileFn)dlsym(lib, "NETDEV_FindFile_V30");
    FindFileV40Fn find_v40 = (FindFileV40Fn)dlsym(lib, "NETDEV_FindFile_V40");
    GetVideoDayNumsFn get_days = (GetVideoDayNumsFn)dlsym(lib, "NETDEV_GetVideoDayNums");
    FindNextFn find_next = (FindNextFn)dlsym(lib, "NETDEV_FindNextFile");
    FindCloseFn find_close = (FindCloseFn)dlsym(lib, "NETDEV_FindClose");
    if (!init || !login || !last_error || !find_next || !find_file || !find_v30 || !find_v40) {
        fprintf(stderr, "dlsym failed: %s\n", dlerror());
        return 1;
    }

    if (set_connect) {
        set_connect(3, 1);
    }
    if (!init()) {
        fprintf(stderr, "NETDEV_Init failed lastError=%d\n", last_error());
        return 1;
    }

    time_t now = time(NULL);
    struct tm tm_buf;
    localtime_r(&now, &tm_buf);
    tm_buf.tm_hour = 0;
    tm_buf.tm_min = 0;
    tm_buf.tm_sec = 0;
    int64_t begin = (int64_t)mktime(&tm_buf);
    int64_t end = (int64_t)now;
    printf("query window ");
    print_time("begin", begin);
    printf(" ");
    print_time("end", end);
    printf("\n");
    fflush(stdout);

    void *user = try_login(login, last_error, ip, port, user_name, password, 0);
    if (user != NULL) {
        query_day_nums(get_days, last_error, user, 1);
        query_findfile("FindFile", find_file, find_next, find_close, last_error, user, 1, begin, end);
        query_v30(find_v30, find_next, find_close, last_error, user, 1, begin, end);
        if (g_logout) {
            g_logout(user);
        }
    }

    user = try_login(login, last_error, ip, port, user_name, password, 1);
    if (user != NULL) {
        query_day_nums(get_days, last_error, user, 1);
        query_findfile("FindFile", find_file, find_next, find_close, last_error, user, 1, begin, end);
        query_v40(find_v40, find_next, find_close, last_error, user, 1, begin, end);
        if (g_logout) {
            g_logout(user);
        }
    }

    printf("probe done\n");
    fflush(stdout);
    if (cleanup) {
        cleanup();
    }
    dlclose(lib);
    return 0;
}
