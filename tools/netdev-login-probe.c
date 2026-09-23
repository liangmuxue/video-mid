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
typedef void *(*FindFileFn)(void *, NETDEV_FILECOND_S *);
typedef int (*FindNextFn)(void *, NETDEV_FINDDATA_S *);
typedef int (*FindCloseFn)(void *);
typedef int (*ReplayUrlFn)(void *, int32_t, int32_t, char *);

static LogoutFn g_logout;

static void print_time(const char *label, int64_t ts) {
    time_t t = (time_t)ts;
    struct tm tm_buf;
    char text[32];
    if (localtime_r(&t, &tm_buf) == NULL) {
        printf("%s=%lld", label, (long long)ts);
        return;
    }
    strftime(text, sizeof(text), "%Y-%m-%d %H:%M:%S", &tm_buf);
    printf("%s=%s", label, text);
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
    info.dwDeviceType = 1;

    void *handle = login(&info, &se);
    if (handle != NULL) {
        printf("login ok proto=%d handle=%p\n", proto, handle);
        return handle;
    }
    printf("login fail proto=%d lastError=%d\n", proto, last_error());
    return NULL;
}

static int query_channel(FindFileFn find_file, FindNextFn find_next, FindCloseFn find_close,
                         LastErrorFn last_error, void *user, int channel,
                         int64_t begin, int64_t end) {
    NETDEV_FILECOND_S cond;
    memset(&cond, 0, sizeof(cond));
    cond.dwChannelID = channel;
    cond.dwStreamType = 1;
    cond.dwFileType = 0;
    cond.tBeginTime = begin;
    cond.tEndTime = end;
    cond.dwRecordLocation = 0;

    void *find = find_file(user, &cond);
    if (find == NULL) {
        printf("channel %d find none lastError=%d\n", channel, last_error());
        return 0;
    }

    int count = 0;
    NETDEV_FINDDATA_S data;
    while (count < 20) {
        memset(&data, 0, sizeof(data));
        if (!find_next(find, &data)) {
            break;
        }
        count++;
        printf("record channel=%d file=%s ", channel, data.szFileName);
        print_time("begin", data.tBeginTime);
        printf(" ");
        print_time("end", data.tEndTime);
        printf(" size=%u\n", data.udwFileSize);
    }
    if (find_close) {
        find_close(find);
    }
    printf("channel %d recordCount=%d\n", channel, count);
    return count;
}

int main(int argc, char **argv) {
    const char *ip = argc > 1 ? argv[1] : "39.185.236.176";
    int port = argc > 2 ? atoi(argv[2]) : 10135;
    const char *user_name = argc > 3 ? argv[3] : "guest";
    const char *password = argc > 4 ? argv[4] : "*Guest321";

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
    FindFileFn find_file = (FindFileFn)dlsym(lib, "NETDEV_FindFile");
    FindNextFn find_next = (FindNextFn)dlsym(lib, "NETDEV_FindNextFile");
    FindCloseFn find_close = (FindCloseFn)dlsym(lib, "NETDEV_FindClose");
    ReplayUrlFn replay_url = (ReplayUrlFn)dlsym(lib, "NETDEV_GetReplayUrl");
    if (!init || !login || !last_error || !find_file || !find_next) {
        fprintf(stderr, "dlsym failed: %s\n", dlerror());
        return 1;
    }

    if (!init()) {
        fprintf(stderr, "NETDEV_Init failed lastError=%d\n", last_error());
        return 1;
    }

    void *user = try_login(login, last_error, ip, port, user_name, password, 0);
    if (user == NULL) {
        user = try_login(login, last_error, ip, port, user_name, password, 1);
    }
    if (user == NULL) {
        if (cleanup) {
            cleanup();
        }
        dlclose(lib);
        return 2;
    }

    int64_t end = (int64_t)time(NULL);
    int64_t begin = end - 24 * 60 * 60;
    printf("query window ");
    print_time("begin", begin);
    printf(" ");
    print_time("end", end);
    printf("\n");

    int total = 0;
    int channels[2] = {1, 0};
    int i;
    for (i = 0; i < 2; i++) {
        total += query_channel(find_file, find_next, find_close, last_error, user,
                               channels[i], begin, end);
    }
    if (total == 0) {
        begin = end - 7 * 24 * 60 * 60;
        printf("no record in 1 day, retry ");
        print_time("begin", begin);
        printf(" ");
        print_time("end", end);
        printf("\n");
        for (i = 0; i < 2; i++) {
            total += query_channel(find_file, find_next, find_close, last_error, user,
                                   channels[i], begin, end);
        }
    }

    if (replay_url) {
        char url[260];
        memset(url, 0, sizeof(url));
        if (replay_url(user, 1, 1, url)) {
            printf("replayUrl channel=1 %s\n", url);
        } else {
            printf("replayUrl fail lastError=%d\n", last_error());
        }
    }

    if (g_logout) {
        g_logout(user);
    }
    if (cleanup) {
        cleanup();
    }
    dlclose(lib);
    return 0;
}
