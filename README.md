### 一. 发热案例分析

Android 框架层通过一个名为 batterystats 的系统服务，**电池的信息**，**电压**，**温度**，**充电状态**等等，都是由**BatteryService**来提供的。<br/><br/>电池的这些信息是**BatteryService**通过广播主动把数据传送给所关心的应用程序。实现了电量统计的功能，**batterystats**实现原理可以查阅电量统计服务 Android 提供的 dumpsys 命令用于查看系统服务的信息(实现原理可以查阅 dumpsys 介绍)<br/><br/>将**batterystats**作为参数，就能输出完整的电量统计信息。小编曾经在日本最大的游戏直播平台之一工作过一段时间发现直播页面发热问题一直饱受日本用户诟病,因此我准备出一篇技术文章详细介绍整个优化流程,经过功能测试发现: 如果在游戏直播中播放视频，手机很快就会发烫。针对这种现象，我马上拉取数据进行了分析，测试数据表明游戏直播耗电量竟然高达 7%,经过调研,发现 Battery Historian 这个框架还挺合适线下优化的

### 二. 发热测试工具

#### 2.1 开发环境

首先确保你的电脑已经安装,并配置好以下相关环境变量:

1. Python 2.7 环境
2. Docker 环境
3. go 1.8.1 环境
4. adb 环境

然后你还得准备一台 Android 5.0 以上手机,因为 Battery Historian 是在 Android 5.0 以上运行环境上跑的,最后 找一台适合高富帅的 Mac OS X 系统,实在没有就拿乞丐版 window 操作~

#### 2.2 Battery Historian 使用指南

接下来我们来看一下 Battery Historian 具体使用:

##### 2.2.1 使用 Docker 监听 battery-historian 9999 端口

![](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/aaabc1207881409584de15748a6d8d4f~tplv-k3u1fbpfcp-watermark.image)

##### 2.2.2 配置 go 的环境

![](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/a9429fa0860747279be1cd1af8e62bbf~tplv-k3u1fbpfcp-watermark.image)

##### 2.2.3 通过 go 下载 Battery Historian 源码

![](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/284d4d2ff3ea4ad99bb2eed88ec349c1~tplv-k3u1fbpfcp-watermark.image)

##### 2.2.4 运行 Battery Historian

![](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/ae6be6cd88794fbeabf24ae6f0d1fb9e~tplv-k3u1fbpfcp-watermark.image)

##### 2.2.5 手机连上我们的 USB,先唤醒 Battery Historian 然后再清空电池历史状态

![](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/58022e5637a64e9a9438d7e0d72f296b~tplv-k3u1fbpfcp-watermark.image)

##### 2.2.6 断开 USB ,打开测试应用,疯狂测试,20 分钟后将 bugreport_xxx 版本.zip 文件导出,通过命令将该文件上传到http://localhost:9999 即可

![](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/7c9ea5ea407148af99cf0e4352a3d610~tplv-k3u1fbpfcp-watermark.image)

##### 2.2.7 查看当前进程的关键信息

![](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/7e0e3d36266b43649e7d80b694b20e8e~tplv-k3u1fbpfcp-watermark.image)
![](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/83014a2744b2461bae42eac961a7310c~tplv-k3u1fbpfcp-watermark.image)
![image](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/cd4cd0085cb14581a306fbe24322f76b~tplv-k3u1fbpfcp-zoom-1.image)
![image](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/b6c2614019494c22bf8767820235e538~tplv-k3u1fbpfcp-zoom-1.image)
![image](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/aa0433d355ac4b32ad63d051ae0c09e7~tplv-k3u1fbpfcp-zoom-1.image)
![image](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/f19c4a47f2804f28a80e244c347a9be9~tplv-k3u1fbpfcp-zoom-1.image)

图片可能不是很清楚,我这边再给大家总结一下核心参数信息

| **参数**     | **参数说明**                                                                                                                                                                                                                                                                                            |
| ------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Sync         | 是否跟后台同步，可以把鼠标停在某一项上面。可以看到何时 sync 同步 启动的，持续时间 Duration 多久。电池容量不会显示单一行为消耗的具体电量，这里只能显示使用电池的频率和时长，你可以看分时段的剩余电量来了解具体消耗了多少电量。                                                                           |
| wake_lock_in | wake_lock 有不同的组件，这个地方记录在某一个时刻，有哪些部件开始工作，以及工作的时间。                                                                                                                                                                                                                  |
| wake_lock    | wake_lock 该属性是记录 wake_lock 模块的工作时间。是否有停止的时候等。Android 的休眠唤醒主要基于 wake_lock 机制，只要系统中存在任一有效的 wake_lock，系统就不能进入深度休眠，但可以进行设备的浅度休眠操作。wake_lock 一般在关闭 lcd、tp 但系统仍然需要正常运行的情况下使用，比如听歌、传输很大的文件等。 |
| running      | 界面的状态，主要判断是否处于 idle 的状态。用来判断无操作状态下电量的消耗。                                                                                                                                                                                                                              |
| plugged      | 充电状态，这一栏显示是否进行了充电，以及充电的时间范围。例如上图反映了我们在第 22s 插入了数据线，然后一直持续了数据采集结束                                                                                                                                                                             |
| screen       | 屏幕是否点亮，这一点可以考虑到睡眠状态和点亮状态下电量的使用信息。                                                                                                                                                                                                                                      |
| top          | 该栏显示当前时刻哪个 app 处于最上层，就是当前手机运行的 app，用来判断某个 app 对手机电量的影响，这样也能判断出该 app 的耗电量信息。该栏记录了应用在某一个时刻启动，以及运行的时间，这对我们比对不同应用对性能的影响有很大的帮助。                                                                       |

### 三. 发热测试过程

我们首先找出一款被骂的最狠的一款测试手机 xx 三星 xx 版本,电池容量：3000mAh,游戏直播和秀场直播以及直播回放 WiFi 环境下，打开 App，播放同一个测试直播源资源分别测试 20 分钟。

测试场景是这样的

- 通过 **小木箱** 的账号进入游戏开播竖屏页面，开启游戏直播
- 通过 **小木箱** 的账号进入直播回放竖屏页面, 进行直播回放
- 通过 **小木箱** 的账号进入秀场直播竖屏页面, 开启秀场直播

为了保证测试数据的准确性,要保证两点,第一点是，手机不要灭屏,因为屏幕唤醒本身就会有耗电开销,第二点是不要使用蜂窝网络,这样测试的数据不具备公正性,最后把没有用到的传感器关掉,最后在测试过程不要充电，保持测试环境的一致性。

测试完毕后,在导航栏选中你的进程 ID

![](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/f58a819b9d3348b99c8d4c9ae2302549~tplv-k3u1fbpfcp-watermark.image)

关注 App Status 两个核心指标

![](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/3752289a52374fd7b0c264d6570d4566~tplv-k3u1fbpfcp-watermark.image)

- CPU User Time
- Device estimated power use
- 测试时长

关注这三个指标,从测试结果可以看到，你的对照组和样本组的测试结果了,由于测试数据比较核心,就不方便对外公布了

直播应用耗电量的问题: 无非就建立 socket 连接过程中.推送心跳包,会定时唤醒 CPU 这样可能会有耗电风险,然后如果自定义 View 设计不合理,进行高频刷新 UI,也会造成耗电,而且本公司产品 UI 布局设计很不合理,布局嵌套很严重,存在重复渲染问题。当然如果你们应用在播放礼物,什么送跑车,送游艇,脸萌效果这样都可能会耗电,耗电最根本的操作就是要解决底层的 welock 及时释放,因为 welock 可以保证 CPU 进行休眠

### 三. 耗电优化建议

- 省电这一块 主要是需要控制 wakelock 的使用。控制无谓的 CPU 运行和计算
- 频繁定位类的 App 确实是耗电大户，可以在非必须的情况下，采用缓存数据，或者通过简化业务流程的情况下来进行优化
- 避免在后台进行日志上报以及前台进行拉活工作
- 如果面有一些任务的队列里面积累了大量的任务,每次都循环的执行任务太久,耗电会明显,要及时的进行清理工作
- 对于网络请求或者 websocket 通信要对数据进行 gzip 压缩处理
- 数据解析不要使用原生的 JSONObject ,应该使用 Gson jackson ptoBuffer 或者其他数据解析工具

### 四. APM 耗电监控建设


眼光放长远点我们可以通过Hook wake_lock 持有长和 alarm 阀值 来预判 alarm 是否在做定时的重复任务,我们也可以通过Hook代理LOCATION_SERVICE实现GPS监控 ; <br/><br/>最后可以通过Hook传感器的SENSOR_SERVICE中的“mSensorListeners”，拿到部分信息,然后利用埋点方案，在申请资源的时候将堆栈信息保存起来。当我们触发某个规则上报问题的时候，可以将收集到的堆栈信息、电池是否充电、CPU 信息、应用前后台时间等辅助信息也一起带上实现 <br/><br/>基于这几点我们可以大胆预设做一套符合企业规则的耗电 APM 体系,固然工作很曲折,如: 在 Android P 之后，很多的 Hook 点都不支持了。 所以测试范围有限~ 但是我们耗电这块可以针对固定低端机测试也不是非得全量测试,总比手动排雷来得方便~

### 五. 总结

本文主要是通过我业余时间的技术调研,利用线下工具 Battery Historian 分析企业 app 电池发热问题,**电池的信息**，**电压**，**温度**，**充电状态**,Device estimated power use 这几个指标是耗电测试的关键指标<br/><br/>对于这些指标我们可以埋点采集,指标采集核心思想还是Hook方案,对于APM耗电建设是一个费力不讨好的工程,耗电监控相关代码我也从张绍文的Matrix仓库抽出来并进行源码分析注释,感兴趣可以[点赞关注](https://github.com/MicroKibaco/BatteryCanary)一下~
