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

package com.github.microkibaco.canary_plugin.batterycanary.core;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.Parcel;
import android.os.SystemClock;

import com.github.microkibaco.canary_plugin.batterycanary.config.BatteryConfig;
import com.github.microkibaco.canary_plugin.batterycanary.config.SharePluginInfo;
import com.github.microkibaco.canary_plugin.batterycanary.util.BatteryCanaryUtil;
import com.github.microkibaco.canary_plugin.report.Issue;
import com.github.microkibaco.canary_plugin.report.IssuePublisher;
import com.github.microkibaco.canary_plugin.util.MatrixLog;
import com.github.microkibaco.canary_plugin.util.MatrixUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 1. 在统计期间（1小时）内将有警报触发的估计计数 如果计数超过可自定义的阈值，则会发布问题 {@link BatteryConfig.Builder#alarmTriggerNum1HThreshold(int)}
 * 和 {@link BatteryConfig.Builder#wakeUpAlarmTriggerNum1HThreshold(int)}.
 *
 * 2. 如果启用了记录功能，将记录警报的历史记录 {@link BatteryConfig.Builder#enableRecordAlarm()}. 然后看 {@link
 * AlarmInfoRecorder}
 *
 * @author liyongjie Created by liyongjie on 2017/11/2.
 */

public class AlarmDetector extends IssuePublisher {
    private static final String TAG = "MicroMsg.AlarmDetector";

    // 新警报的最小限度
    public static final long MIN_FUTURITY = 5 * 1000;
    // 最小警报重复间隔
    public static final long MIN_INTERVAL = 60 * 1000;

    //1小时 转 毫秒
    private static final int COUNT_PERIOD = 3600 * 1000;

    //2天转毫秒
    private static final int COUNT_PERIOD_EXPIRED_THRESHOLD = 2 * 24 * 3600 * 1000;

    private final int mAlarmTriggerNum1HThreshold;
    private final int mWakeUpAlarmTriggerNum1HThreshold;

    private final PersistenceHelper mPersistenceHelper;
    private final AlarmInfoRecorder mAlarmInfoRecorder;
    private List<AlarmInfo> mCurrentRunningAlarms;
    private long mCurrentCountPeriodFrom;

    public AlarmDetector(OnIssueDetectListener issueDetectListener, BatteryConfig config) {
        super(issueDetectListener);
        mAlarmTriggerNum1HThreshold = config.getAlarmTriggerNum1HThreshold();
        mWakeUpAlarmTriggerNum1HThreshold = config.getWakeUpAlarmTriggerNum1HThreshold();
        mPersistenceHelper = new PersistenceHelper();
        mPersistenceHelper.load();

        if (config.isRecordAlarm()) {
            mAlarmInfoRecorder = new AlarmInfoRecorder();
        } else {
            mAlarmInfoRecorder = null;
        }
    }

    /**
     * 初始化检测机制
     */
    public void initDetect() {
        countAndDetect();
    }

    /**
     * 警报器处理逻辑 Run in {@link BatteryCanaryDetectScheduler} single thread
     *
     * @see com.tencent.matrix.batterycanary.core.BatteryCanaryCore
     */
    public void onAlarmSet(int type, long triggerAtMillis, long windowMillis, long intervalMillis,
                           int flags, PendingIntent operation, AlarmManager.OnAlarmListener onAlarmListener,
                           String stackTrace) {

        if (mAlarmInfoRecorder != null) {
            mAlarmInfoRecorder.onAlarmSet(type, triggerAtMillis, windowMillis, intervalMillis, flags, operation, onAlarmListener, stackTrace);
        }
// 警报信息
        AlarmInfo alarmInfo = new AlarmInfo(type, triggerAtMillis, intervalMillis, operation, onAlarmListener, stackTrace);

        doMarkRemoveLogic(alarmInfo.onAlarmListener, alarmInfo.operationInfo);
// 当前任务队列添加警报信息
        mCurrentRunningAlarms.add(alarmInfo);

        countAndDetect();
    }

    /**
     * 运行在 {@link BatteryCanaryDetectScheduler} 的单一线程
     *
     * @see com.tencent.matrix.batterycanary.core.BatteryCanaryCore
     */
    public void onAlarmRemove(PendingIntent operation, AlarmManager.OnAlarmListener onAlarmListener, String stackTrace) {
        if (mAlarmInfoRecorder != null) {
            mAlarmInfoRecorder.onAlarmRemove(operation, onAlarmListener, stackTrace);
        }

        doMarkRemoveLogic(onAlarmListener, new OperationInfo(operation));

        countAndDetect();
    }

    /**
     * 监测和统计
     */
    private void countAndDetect() {
        // 记录当系统时间
        final long now = System.currentTimeMillis();

        MatrixLog.i(TAG, "countAndDetect now:%d mCurrentCountPeriodFrom:%d", now, mCurrentCountPeriodFrom);

        if (mCurrentCountPeriodFrom <= 0) {
            mCurrentCountPeriodFrom = now;
        }

        if (now - mCurrentCountPeriodFrom >= 2 * COUNT_PERIOD_EXPIRED_THRESHOLD) {
            mCurrentCountPeriodFrom = now - COUNT_PERIOD_EXPIRED_THRESHOLD;
        } else if (now - mCurrentCountPeriodFrom >= COUNT_PERIOD_EXPIRED_THRESHOLD) {
            mCurrentCountPeriodFrom = mCurrentCountPeriodFrom + COUNT_PERIOD_EXPIRED_THRESHOLD;
        }

        for (; mCurrentCountPeriodFrom + COUNT_PERIOD <= now; mCurrentCountPeriodFrom += COUNT_PERIOD) {
            doCountAndDetect();
        }

        mPersistenceHelper.save();
    }

    /**
     * 处理监测和统计逻辑
     */
    private void doCountAndDetect() {
        if (mCurrentRunningAlarms == null || mCurrentRunningAlarms.isEmpty()) {
            MatrixLog.i(TAG, "doCountAndDetect no alarms");
            return;
        }

        StringBuilder relatedAlarmSetStacks = new StringBuilder();
        StringBuilder relatedWakeUpAlarmSetStacks = new StringBuilder();

        /**
         * It's just an estimate.
         * TODO
         */
        int currentCountPeriodAlarmTriggeredCount = 0;
        int currentCountPeriodWakeUpAlarmTriggeredCount = 0;
        long triggerAtMillisUTC, repeatCount;
        AlarmInfo alarmInfo;
        long currentCountPeriodEnd = mCurrentCountPeriodFrom + COUNT_PERIOD;
        Iterator<AlarmInfo> it = mCurrentRunningAlarms.iterator();
        while (it.hasNext()) {
            alarmInfo = it.next();

            triggerAtMillisUTC = alarmInfo.getUTCTriggerAtMillis();

            //MatrixLog.d(TAG, "doCountAndDetect triggerAtMillisUTC:%d type:%d intervalMillis:%d removeTimeAtMillis:%d",
            //      triggerAtMillisUTC, alarmInfo.type, alarmInfo.intervalMillis, alarmInfo.removeTimeAtMillis);

            if (triggerAtMillisUTC < mCurrentCountPeriodFrom && alarmInfo.intervalMillis <= 0) {
                //MatrixLog.v(TAG, "doCountAndDetect no-repeat alarm triggerAtMillisUTC less than mCurrentCountPeriodFrom, removed");
                it.remove();
                continue;
            }

            if (alarmInfo.removeTimeAtMillis < mCurrentCountPeriodFrom || alarmInfo.removeTimeAtMillis <= triggerAtMillisUTC) {
                //MatrixLog.v(TAG, "doCountAndDetect already mark remove and not need to be counted, removed");
                it.remove();
                continue;
            }

            if (triggerAtMillisUTC >= currentCountPeriodEnd) {
                //MatrixLog.v(TAG, "doCountAndDetect will not be triggered in this count period");
                continue;
            }

            if (alarmInfo.intervalMillis > 0) {
                long countEnd = alarmInfo.removeTimeAtMillis < currentCountPeriodEnd ? alarmInfo.removeTimeAtMillis : currentCountPeriodEnd;
                long countFrom;
                if (mCurrentCountPeriodFrom <= triggerAtMillisUTC) {
                    countFrom = triggerAtMillisUTC;
                    repeatCount = (countEnd - countFrom - 1) / alarmInfo.intervalMillis + 1;
                } else {
                    countFrom = mCurrentCountPeriodFrom - ((mCurrentCountPeriodFrom - triggerAtMillisUTC) % alarmInfo.intervalMillis);
                    repeatCount = (countEnd - countFrom - 1) / alarmInfo.intervalMillis;
                }

                //MatrixLog.d(TAG, "doCountAndDetect count repeatCount:%d", repeatCount);
            } else {
                repeatCount = 1;
            }

            currentCountPeriodAlarmTriggeredCount += repeatCount;
            relatedAlarmSetStacks.append(alarmInfo.stackTrace).append("\t\t");

            if (alarmInfo.isWakeUpAlarm()) {
                currentCountPeriodWakeUpAlarmTriggeredCount += repeatCount;
                relatedWakeUpAlarmSetStacks.append(alarmInfo.stackTrace).append("\t\t");
            }
        }

//        MatrixLog.i(TAG, "doCountAndDetect currentRunningAlarms size:%d, currentCountPeriodAlarmTriggeredCount:%d, currentCountPeriodWakeUpAlarmTriggeredCount:%d",
//                mCurrentRunningAlarms.size(), currentCountPeriodAlarmTriggeredCount, currentCountPeriodWakeUpAlarmTriggeredCount);

        //detect
        int issueType = -1;
        String alarmSetStacks = null;
        int alarmTriggerCount = 0;
        if (currentCountPeriodAlarmTriggeredCount >= mAlarmTriggerNum1HThreshold) {
            issueType = SharePluginInfo.IssueType.ISSUE_ALARM_TOO_OFTEN;
            alarmSetStacks = relatedAlarmSetStacks.toString();
            alarmTriggerCount = currentCountPeriodAlarmTriggeredCount;
        } else if (currentCountPeriodWakeUpAlarmTriggeredCount >= mWakeUpAlarmTriggerNum1HThreshold) {
            issueType = SharePluginInfo.IssueType.ISSUE_ALARM_WAKE_UP_TOO_OFTEN;
            alarmSetStacks = relatedWakeUpAlarmSetStacks.toString();
            alarmTriggerCount = currentCountPeriodWakeUpAlarmTriggeredCount;
        }
        if (issueType > 0) {
            String issueKey = MatrixUtil.getMD5String(String.format("%d%s", issueType, alarmSetStacks));
            if (isPublished(issueKey)) {
                MatrixLog.v(TAG, "doCountAndDetect issue already published");
            } else {
                JSONObject content = new JSONObject();
                try {
                    content.put(SharePluginInfo.ISSUE_ALARMS_SET_STACKS, alarmSetStacks);
                    content.put(SharePluginInfo.ISSUE_ALARM_TRIGGERED_NUM_1H, alarmTriggerCount);
                    content.put(SharePluginInfo.ISSUE_BATTERY_SUB_TAG, SharePluginInfo.SUB_TAG_ALARM);
                } catch (JSONException e) {
                    MatrixLog.e(TAG, "doCountAndDetect json content error: %s", e);
                }

                Issue batteryIssue = new Issue(issueType);
                batteryIssue.setKey(issueKey);
                batteryIssue.setContent(content);
                publishIssue(batteryIssue);
                markPublished(issueKey);
            }
        }
    }

    /**
     * 正在运行的警报可能会被删除或取消 1. 设置相同的操作{@link PendingIntent#equals(Object)} 或者 onAlarmListener 监听器 2.
     * 删除目标警报{@link AlarmManager#cancel(PendingIntent)} 或onAlarmListener 3.
     * 设置由PendingIntent创建的Alarm{@link PendingIntent#getBroadcast(Context, int, Intent, int)}
     * 而且这个flags是{@link PendingIntent#FLAG_CANCEL_CURRENT}.
     */
    private void doMarkRemoveLogic(AlarmManager.OnAlarmListener targetAlarmListener, OperationInfo targetOperationInfo) {
        AlarmInfo alarmInfo;
        for (int i = 0; i < mCurrentRunningAlarms.size(); i++) {
            alarmInfo = mCurrentRunningAlarms.get(i);

            if (alarmInfo.onAlarmListener != null && alarmInfo.onAlarmListener.equals(targetAlarmListener)) {
                alarmInfo.markRemove();
                continue;
            }

            if (alarmInfo.operationInfo != null && alarmInfo.operationInfo.isTheTargetOperation(targetOperationInfo)) {
                alarmInfo.markRemove();
                continue;
            }

            if (alarmInfo.operationInfo == null && alarmInfo.onAlarmListener == null) {
                alarmInfo.markRemove();
                continue;
            }
        }
    }

    /**
     * 警报信息
     */
    private final static class AlarmInfo {
        private final static long REMOVE_TIME_UNSET_VAL = Long.MAX_VALUE;
        /**
         * 警报类型
         */
        final int type;
        /**
         * 触发时间
         */
        final long triggerAtMillis;
        /**
         * 间隔时间
         */
        final long intervalMillis;

        final OperationInfo operationInfo;
        final AlarmManager.OnAlarmListener onAlarmListener;

        final String stackTrace;
        /**
         * 移除时间
         */
        long removeTimeAtMillis;

        AlarmInfo(int type, long triggerAtMillis, long intervalMillis, PendingIntent operation,
                  AlarmManager.OnAlarmListener onAlarmListener, String stackTrace) {
            this.type = type;
            this.triggerAtMillis = adjustTriggerAtMillis(type, triggerAtMillis);
            this.intervalMillis = adjustIntervalMillis(intervalMillis);
            this.operationInfo = new OperationInfo(operation);
            this.onAlarmListener = onAlarmListener;
            this.stackTrace = stackTrace;
            this.removeTimeAtMillis = REMOVE_TIME_UNSET_VAL;
        }

        /**
         * from file
         *
         * @see InfoWrapper#parseAlarmInfoList()
         */
        AlarmInfo(int type, long triggerAtMillis, long intervalMillis, OperationInfo operationInfo, String stackTrace, long removeTimeAtMillis) {
            this.type = type;
            this.triggerAtMillis = triggerAtMillis;
            this.intervalMillis = intervalMillis;
            this.operationInfo = operationInfo;
            this.onAlarmListener = null;
            this.stackTrace = stackTrace;
            this.removeTimeAtMillis = removeTimeAtMillis;
        }

        public long getUTCTriggerAtMillis() {
            if (type == AlarmManager.RTC || type == AlarmManager.RTC_WAKEUP) {
                return triggerAtMillis;
            }

            return triggerAtMillis + System.currentTimeMillis() - SystemClock.elapsedRealtime();
        }

        public boolean isWakeUpAlarm() {
            return type == AlarmManager.RTC_WAKEUP || type == AlarmManager.ELAPSED_REALTIME_WAKEUP;
        }

        public void markRemove() {
            if (removeTimeAtMillis != REMOVE_TIME_UNSET_VAL) {
                //MatrixLog.i(TAG, "markRemove already remove, removeTimeAtMillis:%d", removeTimeAtMillis);
                return;
            }

            removeTimeAtMillis = System.currentTimeMillis();
            //MatrixLog.i(TAG, "markRemove, removeTimeAtMillis:%d", removeTimeAtMillis);
        }

        private static long adjustTriggerAtMillis(int type, long triggerAtMillis) {
            final long now;
            if (type == AlarmManager.ELAPSED_REALTIME_WAKEUP || type == AlarmManager.ELAPSED_REALTIME) {
                now = SystemClock.elapsedRealtime();
            } else {
                now = System.currentTimeMillis();
            }

            final long minTriggerAtMillis = now + MIN_FUTURITY;
            return triggerAtMillis < minTriggerAtMillis ? minTriggerAtMillis : triggerAtMillis;
        }

        private static long adjustIntervalMillis(long intervalMillis) {
            if (intervalMillis <= 0) {
                return intervalMillis;
            }

            return intervalMillis < MIN_INTERVAL ? MIN_INTERVAL : intervalMillis;
        }
    }

    private final static class OperationInfo {
        /**
         * 获取方法意图
         */
        private static Method sMethodGetIntent;
        /**
         * 获取方法意图类型标记
         */
        private static Method sMethodGetIntentTag;

        //in memory
        final PendingIntent operation;

        // 操作哈希值
        final int operationHashCode;
        // 操作意图
        final Intent operationIntent;
        // 操作意图标记
        final String operationIntentTag;

        OperationInfo(PendingIntent operation) {
            this.operation = operation;
            if (this.operation != null) {
                this.operationHashCode = operation.hashCode();
                this.operationIntent = getOperationIntent(operation);
                this.operationIntentTag = getOperationIntentTag(operation);
            } else {
                this.operationHashCode = -1;
                this.operationIntent = null;
                this.operationIntentTag = null;
            }
        }

        /**
         * from file {@link InfoWrapper#parseAlarmInfoList()}
         */
        OperationInfo(int operationHashCode, Intent operationIntent, String operationIntentTag) {
            this.operation = null;
            this.operationHashCode = operationHashCode;
            this.operationIntent = operationIntent;
            this.operationIntentTag = operationIntentTag;
        }

        /**
         * a acceptable bug : ignore the requestCode when compared by operationIntent or
         * operationIntentTag, even though the requestCode do concern with the comparision of
         * PendingIntent
         *
         * @see PendingIntent#getBroadcast(Context, int, Intent, int)
         * @see #doMarkRemoveLogic(AlarmManager.OnAlarmListener, OperationInfo)
         */
        @SuppressWarnings("PMD")
        public boolean isTheTargetOperation(OperationInfo targetOperationInfo) {
            /**
             * 1. operationInfo only save in memory, will be null if reboot app
             * 2. why not return false, when operation equals false.
             * eg.
             * void setAlarm() {
             *    PendingIntent pi = PendingIntent.getBroadcast(this, 1, intent, PendingIntent.FLAG_CANCEL_CURRENT);
             *    mAlarmManager.setRepeating(AlarmManager.RTC, now+6*60*1000, 60000, pi);
             * }
             * if setAlarm is called multiple times, pi is different since the PendingIntent.FLAG_CANCEL_CURRENT and there will
             * be many alarms. But only the last alarm will act the operation.
             */
            if (operation != null && operation.equals(targetOperationInfo.operation)) {
                //MatrixLog.i(TAG, "isTheTargetOperation operation equals true");
                return true;
            }

            //MatrixLog.d(TAG, "isTheTargetOperation hashCode: %d  %d", operationHashCode, targetOperationInfo.operationHashCode);
            if (operationHashCode == targetOperationInfo.operationHashCode) {
                //MatrixLog.i(TAG, "isTheTargetOperation operationHashCode equals true");
                return true;
            }

            if (operationIntent != null && operationIntent.filterEquals(targetOperationInfo.operationIntent)) {
                //MatrixLog.i(TAG, "isTheTargetOperation operationIntent equals true");
                return true;
            }

            if (operationIntentTag != null && operationIntentTag.equals(targetOperationInfo.operationIntentTag)) {
                //MatrixLog.i(TAG, "isTheTargetOperation operationIntentTag equals true");
                return true;
            }

            return false;
        }

        /**
         * 1. no such method : below than {@link android.os.Build.VERSION_CODES#KITKAT}), this case
         * I am the Ostrich. 2. throws SecurityException: above than {@link
         * android.os.Build.VERSION_CODES#N}, this case will use {@link
         * #getOperationIntentTag(PendingIntent)} as instead
         */
        private static Intent getOperationIntent(PendingIntent operation) {
            if (operation == null) {
                return null;
            }

            if (sMethodGetIntent == null) {
                try {
                    sMethodGetIntent = PendingIntent.class.getDeclaredMethod("getIntent", new Class[0]);
                    sMethodGetIntent.setAccessible(true);
                } catch (NoSuchMethodException e) {
                    MatrixLog.w(TAG, "getOperationIntent e:%s", e);
                    return null;
                }
            }

            try {
                Object obj = sMethodGetIntent.invoke(operation);
                if (!(obj instanceof Intent)) {
                    return null;
                }

                return (Intent) obj;
            } catch (IllegalAccessException e) {
                MatrixLog.w(TAG, "getOperationIntent e:%s", e);
            } catch (InvocationTargetException e) {
//                MatrixLog.w(TAG, "getOperationIntent e:%s cause:%s", e, e.getCause()); // many log, ignore fist @felixzhou
            } catch (SecurityException e) {
                MatrixLog.w(TAG, "getOperationIntent e:%s", e);
            }

            return null;
        }

        private static String getOperationIntentTag(PendingIntent operation) {
            if (operation == null) {
                return null;
            }


            if (sMethodGetIntentTag == null) {
                try {
                    sMethodGetIntentTag = PendingIntent.class.getDeclaredMethod("getTag", new Class[]{String.class});
                    sMethodGetIntentTag.setAccessible(true);
                } catch (NoSuchMethodException e) {
                    MatrixLog.w(TAG, "getOperationIntentTag e:%s", e);
                    return null;
                }
            }

            try {
                Object obj = sMethodGetIntentTag.invoke(operation, "");
                if (!(obj instanceof String)) {
                    return null;
                }

                return (String) obj;
            } catch (IllegalAccessException e) {
                MatrixLog.w(TAG, "getOperationIntentTag e:%s", e);
            } catch (InvocationTargetException e) {
                MatrixLog.w(TAG, "getOperationIntentTag e:%s", e);
            }

            return null;
        }
    }

    private final static class AlarmInfoSerializable implements Serializable {
        final int type;
        final long triggerAtMillis;
        final long intervalMillis;

        final int operationHashCode;
        final byte[] operationIntentBytes;
        final String operationIntentTag;

        final String stackTrace;
        long removeTimeAtMillis;

        AlarmInfoSerializable(AlarmInfo alarmInfo) {
            assert alarmInfo != null;
            this.type = alarmInfo.type;
            this.triggerAtMillis = alarmInfo.triggerAtMillis;
            this.intervalMillis = alarmInfo.intervalMillis;

            this.operationHashCode = alarmInfo.operationInfo.operationHashCode;
            this.operationIntentBytes = intentToBytes(alarmInfo.operationInfo.operationIntent);
            this.operationIntentTag = alarmInfo.operationInfo.operationIntentTag;

            this.stackTrace = alarmInfo.stackTrace;
            this.removeTimeAtMillis = alarmInfo.removeTimeAtMillis;
        }
    }

    // intent 转字节
    private static byte[] intentToBytes(Intent intent) {
        if (intent == null) {
            return null;
        }

        Parcel p = Parcel.obtain();
        p.setDataPosition(0);
        intent.writeToParcel(p, 0);
        byte[] intentBytes = p.marshall();
        p.recycle();
        return intentBytes;
    }

    // 字节转Intent
    private static Intent bytesToIntent(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            Parcel p = Parcel.obtain();
            p.unmarshall(bytes, 0, bytes.length);
            p.setDataPosition(0);
            Intent intent = Intent.CREATOR.createFromParcel(p);
            p.recycle();
            return intent;
        } catch (IllegalStateException e) {
            MatrixLog.e(TAG, "[bytesToIntent] %s", e);
        }
        return null;
    }

    private static final class InfoWrapper implements Serializable {
        private final List<AlarmInfoSerializable> mCurrentRunningAlarms;
        private final long mCurrentCountPeriodFrom;

        InfoWrapper(List<AlarmInfo> currentRunningAlarms, long currentCountPeriodFrom) {
            mCurrentRunningAlarms = new ArrayList<>();
            if (currentRunningAlarms != null) {
                for (int i = 0; i < currentRunningAlarms.size(); i++) {
                    mCurrentRunningAlarms.add(new AlarmInfoSerializable(currentRunningAlarms.get(i)));
                }
            }
            mCurrentCountPeriodFrom = currentCountPeriodFrom;
        }

        public List<AlarmInfo> parseAlarmInfoList() {
            List<AlarmInfo> currentRunningAlarms = new ArrayList<>();
            AlarmInfoSerializable alarmInfoSer;
            OperationInfo operationInfo;
            for (int i = 0; i < mCurrentRunningAlarms.size(); i++) {
                alarmInfoSer = mCurrentRunningAlarms.get(i);
                Intent intent = bytesToIntent(alarmInfoSer.operationIntentBytes);
                if (null == intent) {
                    MatrixLog.e(TAG, "bytesToIntent is null,  alarmInfoSet maybe invalid object");
                    continue;
                }

                operationInfo = new OperationInfo(alarmInfoSer.operationHashCode, intent, alarmInfoSer.operationIntentTag);

                currentRunningAlarms.add(new AlarmInfo(alarmInfoSer.type, alarmInfoSer.triggerAtMillis, alarmInfoSer.intervalMillis,
                        operationInfo, alarmInfoSer.stackTrace, alarmInfoSer.removeTimeAtMillis));
            }

            return currentRunningAlarms;
        }
    }

    private final class PersistenceHelper {

        private final String mSaveFileName;

        PersistenceHelper() {
            mSaveFileName = String.format("%s/com.tencent.matrix/alarm-detector-record/%s/current-alarm-info-%s", Environment.getExternalStorageDirectory().getAbsolutePath(),
                    BatteryCanaryUtil.getPackageName(), BatteryCanaryUtil.getProcessName());
            MatrixLog.i(TAG, "PersistenceHelper mSaveFileName :%s", mSaveFileName);
        }

        void load() {
            File f = new File((mSaveFileName));
            if (!f.exists()) {
                mCurrentRunningAlarms = new ArrayList<>();
                mCurrentCountPeriodFrom = -1;
                return;
            }

            ObjectInputStream ois = null;
            try {
                ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(f)));
                InfoWrapper infoWrapper = (InfoWrapper) ois.readObject();
                if (infoWrapper != null) {
                    mCurrentRunningAlarms = infoWrapper.parseAlarmInfoList();
                    mCurrentCountPeriodFrom = infoWrapper.mCurrentCountPeriodFrom;
                }
            } catch (IOException e) {
                MatrixLog.w(TAG, "load : exp:%s", e);
            } catch (ClassNotFoundException e) {
                MatrixLog.w(TAG, "load : exp:%s", e);
            } catch (Exception e) {
                MatrixLog.w(TAG, "load: exp:%s", e);
            } finally {
                try {
                    if (ois != null) {
                        ois.close();
                    }
                } catch (IOException e) {
                    MatrixLog.w(TAG, "save : exp:%s", e);
                }
            }

            if (mCurrentRunningAlarms == null) {
                mCurrentRunningAlarms = new ArrayList<>();
                mCurrentCountPeriodFrom = -1;
            }

            MatrixLog.i(TAG, "load mCurrentCountPeriodFrom:%d, mCurrentRunningAlarms size:%d", mCurrentCountPeriodFrom, mCurrentRunningAlarms.size());
        }

        void save() {
            File f = new File((mSaveFileName));
            if (!f.getParentFile().exists()) {
                f.getParentFile().mkdirs();
            }

            ObjectOutputStream oos = null;
            try {
                oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(f)));
                InfoWrapper infoWrapper = new InfoWrapper(mCurrentRunningAlarms, mCurrentCountPeriodFrom);
                oos.writeObject(infoWrapper);
                oos.flush();
                MatrixLog.i(TAG, "save mCurrentCountPeriodFrom:%d, mCurrentRunningAlarms size:%d", mCurrentCountPeriodFrom, mCurrentRunningAlarms.size());
            } catch (IOException e) {
                MatrixLog.w(TAG, "save : exp:%s", e);
            } finally {
                try {
                    if (oos != null) {
                        oos.close();
                    }
                } catch (IOException e) {
                    MatrixLog.i(TAG, "save close: exp:%s", e);
                }
            }
        }
    }

    private static final class AlarmInfoRecorder {
        private final String mRecordFilePath;

        AlarmInfoRecorder() {
            String date = MatrixUtil.formatTime("yyyy-MM-dd", System.currentTimeMillis());
            mRecordFilePath = String.format("%s/com.tencent.matrix/alarm-detector-record/%s/alarm-info-record-%s",
                    Environment.getExternalStorageDirectory().getAbsolutePath(), BatteryCanaryUtil.getPackageName(), date);

            MatrixLog.i(TAG, "AlarmInfoRecorder path:%s", mRecordFilePath);
        }

        public void onAlarmSet(int type, long triggerAtMillis, long windowMillis, long intervalMillis,
                               int flags, PendingIntent operation, AlarmManager.OnAlarmListener onAlarmListener,
                               String stackTrace) {
            String time = MatrixUtil.formatTime("yyyy-MM-dd HH:mm", System.currentTimeMillis());
            String record = "";
            try {
                record = String.format("%s onAlarmSet type:%d triggerAtMillis:%d windowMillis:%d intervalMillis:%d flags:%d operationInfo:%s operationHashCode:%d onAlarmListener:%s onAlarmListenerHashCode:%d\n%s\n\n",
                        time, type, triggerAtMillis, windowMillis, intervalMillis, flags, operation, operation == null ? -1 : operation.hashCode(), onAlarmListener, onAlarmListener == null ? -1 : onAlarmListener.hashCode(), stackTrace);
            } catch (ClassCastException ex) {
                MatrixLog.e(TAG, ex.toString());
                return;
            }

            doRecord(record);
        }

        public void onAlarmRemove(PendingIntent operation, AlarmManager.OnAlarmListener onAlarmListener, String stackTrace) {
            String time = MatrixUtil.formatTime("yyyy-MM-dd HH:mm", System.currentTimeMillis());
            String record = "";
            try {
                record = String.format("%s onAlarmRemove operationInfo:%s operationHashCode:%d onAlarmListener:%s onAlarmListenerHashCode:%d\n%s\n\n",
                        time, operation, operation == null ? -1 : operation.hashCode(), onAlarmListener, onAlarmListener == null ? -1 : onAlarmListener.hashCode(), stackTrace);
            } catch (ClassCastException ex) {
                MatrixLog.e(TAG, ex.toString());
                return;
            }

            doRecord(record);
        }

        /**
         * 做记录
         */
        private void doRecord(String record) {
            BufferedWriter bw = null;
            try {
                File f = new File((mRecordFilePath));
                if (!f.getParentFile().mkdirs() && !f.getParentFile().exists()) {
                    MatrixLog.e(TAG, "doRecord mkdirs failed");
                    return;
                }

                bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, true)));
                bw.write(record, 0, record.length());
                bw.flush();
            } catch (FileNotFoundException e) {
                MatrixLog.w(TAG, "doRecord exp:%s", e.getLocalizedMessage());
            } catch (IOException e) {
                MatrixLog.w(TAG, "doRecord exp:%s", e.getLocalizedMessage());
            } finally {
                try {
                    if (bw != null) {
                        bw.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }
    }
}
