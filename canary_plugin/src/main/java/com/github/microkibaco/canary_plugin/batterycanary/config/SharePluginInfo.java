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

/**
 * @author liyongjie
 *         Created by liyongjie on 2017/8/14.
 */

public class SharePluginInfo {
    // 标签插件
    public static final String TAG_PLUGIN = "battery";
    // 子标签唤醒锁
    public static final String SUB_TAG_WAKE_LOCK = "wakeLock";
    // 子标签警报
    public static final String SUB_TAG_ALARM = "alarm";

    // Issue 类型
    public static final class IssueType {
        public static final int ISSUE_UNKNOWN                  = 0x0;
        public static final int ISSUE_WAKE_LOCK_ONCE_TOO_LONG  = 0x1;
        public static final int ISSUE_WAKE_LOCK_TOO_OFTEN      = 0x2;
        public static final int ISSUE_WAKE_LOCK_TOTAL_TOO_LONG = 0x3;

        public static final int ISSUE_ALARM_TOO_OFTEN          = 0x4;
        public static final int ISSUE_ALARM_WAKE_UP_TOO_OFTEN  = 0x5;
    }

    // 电池子标签问题
    public static final String ISSUE_BATTERY_SUB_TAG                                   = "subTag";
    // 问题唤醒锁标签
    public static final String ISSUE_WAKE_LOCK_TAG                                     = "wakeLockTag";
    // 唤醒锁定历史问题
    public static final String ISSUE_WAKE_LOCK_STACK_HISTORY                           = "stackHistory";
    // 唤醒标志问题
    public static final String ISSUE_WAKE_FLAGS                                        = "flags";
  // 唤醒时间问题
    public static final String ISSUE_WAKE_HOLD_TIME                                    = "holdTime";

    //发出锁定统计时间范围唤醒
    public static final String ISSUE_WAKE_LOCK_STATISTICAL_TIME_FRAME                  = "timeFrame";
    // 唤醒锁统计获取次数问题
    public static final String ISSUE_WAKE_LOCK_STATISTICAL_ACQUIRE_CNT                 = "acquireCnt";
    // 当屏幕关闭时，发出唤醒锁统计信息获取次数问题
    public static final String ISSUE_WAKE_LOCK_STATISTICAL_ACQUIRE_CNT_WHEN_SCREEN_OFF = "acquireCntWhenScreenOff";
    // 唤醒锁定统计保持时间问题
    public static final String ISSUE_WAKE_LOCK_STATISTICAL_HOLD_TIME                   = "statisticalHoldTime";
 // 已触发NUM 1H的问题警报问题
    public static final String ISSUE_ALARM_TRIGGERED_NUM_1H                            = "alarmTriggeredNum1H";
    // 警报设置堆栈问题
    public static final String ISSUE_ALARMS_SET_STACKS                                 = "alarmSetStacks";
}
