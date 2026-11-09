# 时刻记 · 事件记录与定时提醒

一个只有你自己能用的安卓小工具：随手记下一件事和它的时间，到点准时响提醒。没有账号、没有联网、数据只存在手机本地。

## 它能做什么

- **随手记**：记下事件内容、可选备注，设定日期和时间
- **到点提醒**：系统通知栏弹出提醒，带「稍后10分钟」和「已完成」两个按钮
- **重复提醒**：不重复 / 每天 / 每周 / 每月
- **状态管理**：待提醒会显示「还有 2 小时」这类倒计时；过期、已完成的会置灰并划线
- **关机重启、改系统时间后自动重挂闹钟**，不会因为重启丢提醒
- **本地存储**：一个 JSON 文件放在应用私有目录，不联网、不上传

## 装上它

APK 不在仓库里（仓库只存源码），有两种拿法：

- 到仓库的 **Actions → Build APK → 最新一次运行**，在页面底部下载 `shikeji-debug-apk` 压缩包，解压得到 `app-debug.apk`
- 自己本地编译：`gradle assembleDebug`，产物在 `app/build/outputs/apk/debug/app-debug.apk`

1. 把 APK 传到手机（微信文件传输、数据线、网盘都行）
2. 手机上点开安装，第一次会提示「未知来源」，允许即可
3. 打开 App，按提示允许「通知」权限

### 想准点响，必须开这两项

| 系统设置 | 位置 | 为什么 |
| --- | --- | --- |
| 通知权限 | 首次打开时弹窗，选「允许」 | 没通知权限就弹不出提醒 |
| 闹钟和提醒（精确闹钟） | 设置 → 应用 → 时刻记 → 闹钟和提醒 | 没这个权限，提醒可能晚几分钟 |

小米 / 红米、华为 / 荣耀、OPPO / vivo 还需要在「电池优化」里把时刻记设为**不受限制**，否则后台可能被清理掉，提醒会延迟。App 主页顶部如果出现提示条，点一下就能直接跳到对应设置。

## 自己编译

需要 JDK 17 和 Android SDK（platform 34 / build-tools 34.0.0）。

```bash
# 用 Android Studio 打开本目录，直接 Run 也行
gradle assembleDebug        # 产物在 app/build/outputs/apk/debug/
```

仓库里的 `.github/workflows/build.yml` 会自动在 GitHub 上编译并上传 APK 到 Actions 产物里。

## 代码结构

```
app/src/main/java/com/shikeji/reminder/
├── MainActivity.kt              主页：列表、汇总、权限引导
├── ui/EventEditActivity.kt      新建 / 编辑事件
├── ui/EventAdapter.kt           列表项渲染（状态、倒计时、划线）
├── data/EventStore.kt           事件模型 + JSON 本地存储
├── alarm/AlarmScheduler.kt      闹钟挂载、取消、重复下一次时间计算
├── alarm/ReminderReceiver.kt    闹钟触发 → 通知；稍后提醒 / 已完成
├── alarm/BootReceiver.kt        开机、改时间后重新挂载
└── util/TimeFormat.kt           今天/明天、几点几分、还有多久
```

技术选择上尽量少依赖：Kotlin + 原生 XML 视图，不引 Compose、不引 Room、不引 Retrofit，构建快、包体小、行为可预期。

## 版本

- v1.0.0 — 事件记录、精确定时提醒、重复提醒、通知操作、开机重挂
