/*
 * Tencent is pleased to support the open source community by making wechat-matrix available.
 * Copyright (C) 2018 THL A29 Limited, a Tencent company. All rights reserved.
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.microkibaco.canary_plugin.batterycanary.config;


import com.github.microkibaco.mrs.plugin.IDynamicConfig;

/**
 * @author liyongjie Created by liyongjie on 2017/8/14.
 */

public class BatteryConfig {
    //
    private static final String TAG = "Matrix.BatteryConfig";
    // 检测唤醒锁
    private static final boolean DETECT_WAKE_LOCK = true;
    // 记录唤醒锁
    private static final boolean RECORD_WAKE_LOCK = false;
    // 检测报警
    private static final boolean DETECT_ALARM = true;
    // 记录警告
    private static final boolean RECORD_ALARM = false;
//    private static final boolean DETECT_NONE      = false;

    /**
     * if a single wake lock is held longer than this threshold, a issue is published
     */

    // 如果单个唤醒锁的保持时间超过此阈值，则会发布问题

    private static final int DEFAULT_WAKE_LOCK_HOLD_TIME_THRESHOLD = 2 * 60 * 1000;

    /**
     * 具有相同标签的唤醒锁可能会获取并释放多次。 有一个聚合。如果获取时间过多或保存时间过长，则会发布问题
     */
    private static final int DEFAULT_WAKE_LOCK_ACQUIRE_CNT_1H_THRESHOLD = 20;
    // 默认唤醒锁定时间 1hour 阈值
    private static final int DEFAULT_WAKE_LOCK_HOLD_TIME_1H_THRESHOLD = 10 * 60 * 1000;

    // 默认警报已触发NUM 1小时阈值
    private static final int DEFAULT_ALARM_TRIGGERED_NUM_1H_THRESHOLD = 20;
    private static final int DEFAULT_WAKEUP_ALARM_TRIGGERED_NUM_1H_THRESHOLD = 12;

    /**
     * 默认的宽松策略将启用所有可用的检测器
     */
//    public static final BatteryConfig DEFAULT = new BatteryConfig.Builder().build();

    private final IDynamicConfig mDynamicConfig;

    /**
     * 初始化电池监测配置
     */
    private BatteryConfig(IDynamicConfig dynamicConfig) {
        this.mDynamicConfig = dynamicConfig;
    }

    /**
     * 是否为唤醒锁
     */
    public boolean isDetectWakeLock() {
        return mDynamicConfig.get(IDynamicConfig.ExptEnum.clicfg_matrix_battery_detect_wake_lock_enable.name(), DETECT_WAKE_LOCK);
    }

    /**
     * 是否检测警报
     */
    public boolean isDetectAlarm() {
        return mDynamicConfig.get(IDynamicConfig.ExptEnum.clicfg_matrix_battery_detect_alarm_enable.name(), DETECT_ALARM);
    }

    /**
     * only make sense when {@link #isDetectWakeLock()} is true
     *
     * @return 是否记录唤醒锁
     */
    public boolean isRecordWakeLock() {
        return mDynamicConfig.get(IDynamicConfig.ExptEnum.clicfg_matrix_battery_record_wake_lock_enable.name(), RECORD_WAKE_LOCK);
    }

    /**
     * only make sense when {@link #isDetectAlarm()} is true
     *
     * @return 是否记录警报
     */
    public boolean isRecordAlarm() {
        return mDynamicConfig.get(IDynamicConfig.ExptEnum.clicfg_matrix_battery_record_alarm_enable.name(), RECORD_ALARM);
    }

    /**
     * 获取唤醒锁定保持时间阈值
     */

    public int getWakeLockHoldTimeThreshold() {
        return mDynamicConfig.get(IDynamicConfig.ExptEnum.clicfg_matrix_battery_wake_lock_hold_time_threshold.name(), DEFAULT_WAKE_LOCK_HOLD_TIME_THRESHOLD);
    }

    /**
     * 获取唤醒锁定保持时间1H阈值
     */
    public int getWakeLockHoldTime1HThreshold() {
        return mDynamicConfig.get(IDynamicConfig.ExptEnum.clicfg_matrix_battery_wake_lock_1h_hold_time_threshold.name(), DEFAULT_WAKE_LOCK_HOLD_TIME_1H_THRESHOLD);
    }

    /**
     * 获取唤醒锁获取Cnt 1H阈值
     */
    public int getWakeLockAcquireCnt1HThreshold() {
        return mDynamicConfig.get(IDynamicConfig.ExptEnum.clicfg_matrix_battery_wake_lock_1h_acquire_cnt_threshold.name(), DEFAULT_WAKE_LOCK_ACQUIRE_CNT_1H_THRESHOLD);
    }

    // 获取警报触发数字阈值
    public int getAlarmTriggerNum1HThreshold() {
        return mDynamicConfig.get(IDynamicConfig.ExptEnum.clicfg_matrix_battery_alarm_1h_trigger_cnt_threshold.name(), DEFAULT_ALARM_TRIGGERED_NUM_1H_THRESHOLD);
    }

    // 获取唤醒警报触发数字阈值
    public int getWakeUpAlarmTriggerNum1HThreshold() {
        return mDynamicConfig.get(IDynamicConfig.ExptEnum.clicfg_matrix_battery_wake_up_alarm_1h_trigger_cnt_threshold.name(), DEFAULT_WAKEUP_ALARM_TRIGGERED_NUM_1H_THRESHOLD);
    }


    @Override
    public String toString() {
        return String.format("[BatteryCanary.BatteryConfig], isDetectWakeLock:%b, isDetectAlarm:%b, isRecordWakeLock:%b, isRecordAlarm:%b",
                isDetectWakeLock(), isDetectWakeLock(), isRecordWakeLock(), isRecordAlarm());
    }

    /**
     * 构建者设计模式
     */
    public static final class Builder {
        private IDynamicConfig dynamicConfig;

        public Builder() {
        }

        public Builder dynamicConfig(IDynamicConfig dynamicConfig) {
            this.dynamicConfig = dynamicConfig;
            return this;
        }


        public BatteryConfig build() {
            return new BatteryConfig(dynamicConfig);
        }
    }
}
