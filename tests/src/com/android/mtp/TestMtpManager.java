/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mtp;

import android.content.Context;
import android.mtp.MtpObjectInfo;
import android.os.ParcelFileDescriptor;
import android.util.SparseArray;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class TestMtpManager extends MtpManager {
    public static final int CREATED_DOCUMENT_HANDLE = 1000;

    protected static String pack(int... args) {
        return Arrays.toString(args);
    }

    private final SparseArray<MtpDeviceRecord> mDevices = new SparseArray<>();
    private final Map<String, MtpObjectInfo> mObjectInfos = new HashMap<>();
    private final Map<String, int[]> mObjectHandles = new HashMap<>();
    private final Map<String, byte[]> mThumbnailBytes = new HashMap<>();
    private final Map<String, byte[]> mImportFileBytes = new HashMap<>();

    TestMtpManager(Context context) {
        super(context);
    }

    void addValidDevice(MtpDeviceRecord device) {
        mDevices.put(device.deviceId, device);
    }

    void setObjectHandles(int deviceId, int storageId, int parentHandle, int[] objectHandles) {
        mObjectHandles.put(pack(deviceId, storageId, parentHandle), objectHandles);
    }

    void setObjectInfo(int deviceId, MtpObjectInfo objectInfo) {
        mObjectInfos.put(pack(deviceId, objectInfo.getObjectHandle()), objectInfo);
    }

    void setImportFileBytes(int deviceId, int objectHandle, byte[] bytes) {
        mImportFileBytes.put(pack(deviceId, objectHandle), bytes);
    }

    byte[] getImportFileBytes(int deviceId, int objectHandle) {
        return mImportFileBytes.get(pack(deviceId, objectHandle));
    }

    void setThumbnail(int deviceId, int objectHandle, byte[] bytes) {
        mThumbnailBytes.put(pack(deviceId, objectHandle), bytes);
    }

    @Override
    MtpDeviceRecord[] getDevices() {
        final MtpDeviceRecord[] result = new MtpDeviceRecord[mDevices.size()];
        for (int i = 0; i < mDevices.size(); i++) {
            final MtpDeviceRecord device = mDevices.valueAt(i);
            if (device.opened) {
                result[i] = device;
            } else {
                result[i] = new MtpDeviceRecord(
                        device.deviceId, device.name, device.opened, new MtpRoot[0], new int[0]);
            }
        }
        return result;
    }

    @Override
    void openDevice(int deviceId) throws IOException {
        final MtpDeviceRecord device = mDevices.get(deviceId);
        if (device == null || device.opened) {
            throw new IOException();
        }
        mDevices.put(
                deviceId,
                new MtpDeviceRecord(device.deviceId, device.name, true, device.roots, new int[0]));
    }

    @Override
    void closeDevice(int deviceId) throws IOException {
        final MtpDeviceRecord device = mDevices.get(deviceId);
        if (device == null || !device.opened) {
            throw new IOException();
        }
        mDevices.put(
                deviceId,
                new MtpDeviceRecord(device.deviceId, device.name, false, device.roots, new int[0]));
    }

    @Override
    MtpObjectInfo getObjectInfo(int deviceId, int objectHandle) throws IOException {
        final String key = pack(deviceId, objectHandle);
        if (mObjectInfos.containsKey(key)) {
            return mObjectInfos.get(key);
        } else {
            throw new IOException("getObjectInfo error: " + key);
        }
    }

    @Override
    int[] getObjectHandles(int deviceId, int storageId, int parentObjectHandle) throws IOException {
        final String key = pack(deviceId, storageId, parentObjectHandle);
        if (mObjectHandles.containsKey(key)) {
            return mObjectHandles.get(key);
        } else {
            throw new IOException("getObjectHandles error: " + key);
        }
    }

    @Override
    void importFile(int deviceId, int objectHandle, ParcelFileDescriptor target)
            throws IOException {
        final String key = pack(deviceId, objectHandle);
        if (mImportFileBytes.containsKey(key)) {
            try (final ParcelFileDescriptor.AutoCloseOutputStream outputStream =
                    new ParcelFileDescriptor.AutoCloseOutputStream(target)) {
                outputStream.write(mImportFileBytes.get(key));
            }
        } else {
            throw new IOException("importFile error: " + key);
        }
    }

    @Override
    int createDocument(int deviceId, MtpObjectInfo objectInfo, ParcelFileDescriptor source)
            throws IOException {
        final String key = pack(deviceId, CREATED_DOCUMENT_HANDLE);
        if (mObjectInfos.containsKey(key)) {
            throw new IOException();
        }
        mObjectInfos.put(key, objectInfo);
        if (objectInfo.getFormat() != 0x3001) {
            try (final ParcelFileDescriptor.AutoCloseInputStream inputStream =
                    new ParcelFileDescriptor.AutoCloseInputStream(source)) {
                final byte[] buffer = new byte[objectInfo.getCompressedSize()];
                if (inputStream.read(buffer, 0, objectInfo.getCompressedSize()) !=
                        objectInfo.getCompressedSize()) {
                    throw new IOException();
                }

                mImportFileBytes.put(pack(deviceId, CREATED_DOCUMENT_HANDLE), buffer);
            }
        }
        return CREATED_DOCUMENT_HANDLE;
    }

    @Override
    byte[] getThumbnail(int deviceId, int objectHandle) throws IOException {
        final String key = pack(deviceId, objectHandle);
        if (mThumbnailBytes.containsKey(key)) {
            return mThumbnailBytes.get(key);
        } else {
            throw new IOException("getThumbnail error: " + key);
        }
    }

    @Override
    void deleteDocument(int deviceId, int objectHandle) throws IOException {
        final String key = pack(deviceId, objectHandle);
        if (mObjectInfos.containsKey(key)) {
            mObjectInfos.remove(key);
        } else {
            throw new IOException();
        }
    }

    @Override
    int getParent(int deviceId, int objectHandle) throws IOException {
        final String key = pack(deviceId, objectHandle);
        if (mObjectInfos.containsKey(key)) {
            return mObjectInfos.get(key).getParent();
        } else {
            throw new IOException();
        }
    }

    @Override
    int[] getOpenedDeviceIds() {
        final int[] result = new int[mDevices.size()];
        int count = 0;
        for (int i = 0; i < mDevices.size(); i++) {
            final MtpDeviceRecord device = mDevices.valueAt(i);
            if (device.opened) {
                result[count++] = device.deviceId;
            }
        }
        return Arrays.copyOf(result, count);
    }

    @Override
    byte[] getObject(int deviceId, int objectHandle, int expectedSize) throws IOException {
        return mImportFileBytes.get(pack(deviceId, objectHandle));
    }
}
