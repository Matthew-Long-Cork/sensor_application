/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.google.android.apps.forscience.whistlepunk.devicemanager;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;

import com.bignerdranch.expandablerecyclerview.Model.ParentListItem;
import com.google.android.apps.forscience.whistlepunk.SensorAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * ExpandableRecyclerView Model object that holds the specification of a device and the keys of all
 * of the sensors on that device.
 *
 * As currently used, a device item is created, and sensors are added to it.  If we start to
 * detect and indicate that sensors have been removed from a device, that will require some
 * additional code.
 */
public class DeviceParentListItem implements ParentListItem {
    private DeviceWithSensors mDevice;
    private final SensorAppearanceProvider mAppearanceProvider;
    private boolean mIsNowExpanded = true;

    public DeviceParentListItem(InputDeviceSpec spec, SensorAppearanceProvider appearanceProvider,
            boolean startsExpanded) {
        mDevice = new DeviceWithSensors(Preconditions.checkNotNull(spec));
        mAppearanceProvider = appearanceProvider;
        mIsNowExpanded = startsExpanded;
    }

    @Override
    public List<?> getChildItemList() {
        return mDevice.getSensorKeys();
    }

    @Override
    public boolean isInitiallyExpanded() {
        return mIsNowExpanded;
    }

    public boolean isCurrentlyExpanded() {
        return mIsNowExpanded;
    }

    public String getDeviceName() {
        return mDevice.getName();
    }


    public boolean isDevice(InputDeviceSpec device) {
        return mDevice.getSpec().isSameSensor(device);
    }

    public void addSensor(String key) {
        mDevice.addSensorKey(key);
    }

    public String getSensorKey(int i) {
        return mDevice.getSensorKeys().get(i);
    }

    public int sensorIndexOf(String sensorKey) {
        return mDevice.getSensorKeys().indexOf(sensorKey);
    }

    boolean isPhoneSensorParent(DeviceRegistry deviceRegistry) {
        return isDevice(deviceRegistry.getBuiltInDevice());
    }

    public Drawable getDeviceIcon(Context context, Map<String, ConnectableSensor> sensorMap) {
        return mDevice.getIconDrawable(context, mAppearanceProvider, sensorMap);
    }

    public InputDeviceSpec getSpec() {
        return mDevice.getSpec();
    }

    public void setIsCurrentlyExpanded(boolean isNowExpanded) {
        mIsNowExpanded = isNowExpanded;
    }

    public boolean canForget(DeviceRegistry deviceRegistry) {
        return !isPhoneSensorParent(deviceRegistry);
    }

    public List<String> getSensorKeys() {
        return mDevice.getSensorKeys();
    }
}
