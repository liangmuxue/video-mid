# ZLM 演示推流（Windows / mock 页面用）
# 用法:
#   .\scripts\zlm-demo-push.ps1
#   .\scripts\zlm-demo-push.ps1 -Stop
#   .\scripts\zlm-demo-push.ps1 -ZlmHost 8.130.74.232

param(
    [switch]$Stop,
    [switch]$Status,
    [string]$ZlmHost = "127.0.0.1",
    [int]$ZlmRtmpPort = 1935,
    [string]$Ffmpeg = "ffmpeg",
    [string]$App = "live"
)

$PidFile = Join-Path $env:TEMP "video-mid-zlm-demo.pids"

$Streams = @(
    @{ Name = "tic7632_01_visible_main"; Label = "TIC7632-01 可见光主"; Kind = "visible" },
    @{ Name = "tic7632_01_visible_sub";  Label = "TIC7632-01 可见光子"; Kind = "visible" },
    @{ Name = "tic7632_01_thermal_main"; Label = "TIC7632-01 热成像";   Kind = "thermal" },
    @{ Name = "tic7632_02_visible_main"; Label = "TIC7632-02 可见光主"; Kind = "visible" },
    @{ Name = "tic7632_02_thermal_main"; Label = "TIC7632-02 热成像";   Kind = "thermal" },
    @{ Name = "cam01_main"; Label = "东门主码流"; Kind = "visible" },
    @{ Name = "cam01_sub";  Label = "东门子码流"; Kind = "visible" },
    @{ Name = "cam02_main"; Label = "岗卡主码流"; Kind = "visible" },
    @{ Name = "cam02_sub";  Label = "岗卡子码流"; Kind = "visible" },
    @{ Name = "cam03_sub"; Label = "停车场";     Kind = "visible" }
)

function Stop-DemoStreams {
    if (Test-Path $PidFile) {
        Get-Content $PidFile | ForEach-Object {
            if ($_ -match '^\d+$') {
                Stop-Process -Id ([int]$_) -Force -ErrorAction SilentlyContinue
            }
        }
        Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
        Write-Host "已停止演示推流"
    } else {
        Write-Host "无运行中的演示推流"
    }
}

function Start-DemoStream {
    param($Name, $Label, $Kind)
    $rtmp = "rtmp://${ZlmHost}:${ZlmRtmpPort}/${App}/${Name}"
    if ($Kind -eq "thermal") {
        $vf = "format=gray,drawtext=text='${Label}':fontsize=28:fontcolor=white:x=20:y=20,drawtext=text='MOCK THERMAL':fontsize=18:fontcolor=orange:x=20:y=56"
    } else {
        $vf = "drawtext=text='${Label}':fontsize=28:fontcolor=white:x=20:y=20,drawtext=text='MOCK VISIBLE':fontsize=18:fontcolor=cyan:x=20:y=56"
    }
    $args = @(
        "-hide_banner", "-loglevel", "warning", "-re",
        "-f", "lavfi", "-i", "testsrc=size=1280x720:rate=25",
        "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100",
        "-vf", $vf,
        "-c:v", "libx264", "-preset", "ultrafast", "-tune", "zerolatency", "-pix_fmt", "yuv420p", "-g", "50",
        "-c:a", "aac", "-b:a", "48k",
        "-f", "flv", $rtmp
    )
    $p = Start-Process -FilePath $Ffmpeg -ArgumentList $args -PassThru -WindowStyle Hidden
    Add-Content -Path $PidFile -Value $p.Id
    Write-Host "  -> $Name  ($rtmp)"
}

if ($Stop) { Stop-DemoStreams; exit 0 }
if ($Status) {
    if (Test-Path $PidFile) { Get-Content $PidFile } else { Write-Host "未运行" }
    exit 0
}

if (-not (Get-Command $Ffmpeg -ErrorAction SilentlyContinue)) {
    Write-Error "未找到 ffmpeg，请先安装并加入 PATH"
    exit 1
}

Stop-DemoStreams
New-Item -Path $PidFile -ItemType File -Force | Out-Null

Write-Host "ZLM RTMP: rtmp://${ZlmHost}:${ZlmRtmpPort}/${App}/<stream>"
Write-Host "HTTP-FLV: http://${ZlmHost}:8080/${App}/<stream>.live.flv"
Write-Host "启动演示推流..."

foreach ($s in $Streams) {
    Start-DemoStream -Name $s.Name -Label $s.Label -Kind $s.Kind
}

Write-Host ""
Write-Host "已启动 $($Streams.Count) 路。停止: .\scripts\zlm-demo-push.ps1 -Stop"
