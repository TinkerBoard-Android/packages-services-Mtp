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

import android.database.Cursor;
import android.mtp.MtpConstants;
import android.mtp.MtpObjectInfo;
import android.net.Uri;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsContract;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.io.FileNotFoundException;
import java.io.IOException;

@SmallTest
public class MtpDocumentsProviderTest extends AndroidTestCase {
    private final static Uri ROOTS_URI =
            DocumentsContract.buildRootsUri(MtpDocumentsProvider.AUTHORITY);
    private TestContentResolver mResolver;
    private MtpDocumentsProvider mProvider;
    private TestMtpManager mMtpManager;
    private final TestResources mResources = new TestResources();
    private MtpDatabase mDatabase;

    @Override
    public void setUp() throws IOException {
        mResolver = new TestContentResolver();
        mMtpManager = new TestMtpManager(getContext());
        mProvider = new MtpDocumentsProvider();
        mDatabase = new MtpDatabase(getContext(), MtpDatabaseConstants.FLAG_DATABASE_IN_MEMORY);
        mProvider.onCreateForTesting(mResources, mMtpManager, mResolver, mDatabase);
    }

    @Override
    public void tearDown() {
        mProvider.shutdown();
    }

    public void testOpenAndCloseDevice() throws Exception {
        mMtpManager.addValidDevice(0);
        mMtpManager.setRoots(0, new MtpRoot[] {
                new MtpRoot(
                        0 /* deviceId */,
                        1 /* storageId */,
                        "Device A" /* device model name */,
                        "Storage A" /* volume description */,
                        1024 /* free space */,
                        2048 /* total space */,
                        "" /* no volume identifier */)
        });

        mProvider.openDevice(0);
        mResolver.waitForNotification(ROOTS_URI, 1);

        mProvider.closeDevice(0);
        mResolver.waitForNotification(ROOTS_URI, 2);
    }

    public void testOpenAndCloseErrorDevice() throws Exception {
        try {
            mProvider.openDevice(1);
            fail();
        } catch (Throwable error) {
            assertTrue(error instanceof IOException);
        }

        try {
            mProvider.closeDevice(1);
            fail();
        } catch (Throwable error) {
            assertTrue(error instanceof IOException);
        }

        // Check if the following notification is the first one or not.
        mMtpManager.addValidDevice(0);
        mMtpManager.setRoots(0, new MtpRoot[] {
                new MtpRoot(
                        0 /* deviceId */,
                        1 /* storageId */,
                        "Device A" /* device model name */,
                        "Storage A" /* volume description */,
                        1024 /* free space */,
                        2048 /* total space */,
                        "" /* no volume identifier */)
        });
        mProvider.openDevice(0);
        mResolver.waitForNotification(ROOTS_URI, 1);
    }

    public void testQueryRoots() throws Exception {
        mMtpManager.addValidDevice(0);
        mMtpManager.addValidDevice(1);
        mMtpManager.setRoots(0, new MtpRoot[] {
                new MtpRoot(
                        0 /* deviceId */,
                        1 /* storageId */,
                        "Device A" /* device model name */,
                        "Storage A" /* volume description */,
                        1024 /* free space */,
                        2048 /* total space */,
                        "" /* no volume identifier */)
        });
        mMtpManager.setRoots(1, new MtpRoot[] {
                new MtpRoot(
                        1 /* deviceId */,
                        1 /* storageId */,
                        "Device B" /* device model name */,
                        "Storage B" /* volume description */,
                        2048 /* free space */,
                        4096 /* total space */,
                        "Identifier B" /* no volume identifier */)
        });

        {
            mProvider.openDevice(0);
            mResolver.waitForNotification(ROOTS_URI, 1);
            final Cursor cursor = mProvider.queryRoots(null);
            assertEquals(1, cursor.getCount());
            cursor.moveToNext();
            assertEquals("1", cursor.getString(0));
            assertEquals(Root.FLAG_SUPPORTS_IS_CHILD | Root.FLAG_SUPPORTS_CREATE, cursor.getInt(1));
            // TODO: Add storage icon for MTP devices.
            assertTrue(cursor.isNull(2) /* icon */);
            assertEquals("Device A Storage A", cursor.getString(3));
            assertEquals("0_1_0", cursor.getString(4));
            assertEquals(1024, cursor.getInt(5));
        }

        {
            mProvider.openDevice(1);
            mResolver.waitForNotification(ROOTS_URI, 2);
            final Cursor cursor = mProvider.queryRoots(null);
            assertEquals(2, cursor.getCount());
            cursor.moveToNext();
            cursor.moveToNext();
            assertEquals("2", cursor.getString(0));
            assertEquals(Root.FLAG_SUPPORTS_IS_CHILD | Root.FLAG_SUPPORTS_CREATE, cursor.getInt(1));
            // TODO: Add storage icon for MTP devices.
            assertTrue(cursor.isNull(2) /* icon */);
            assertEquals("Device B Storage B", cursor.getString(3));
            assertEquals("1_1_0", cursor.getString(4));
            assertEquals(2048, cursor.getInt(5));
        }
    }

    public void testQueryRoots_error() throws Exception {
        mMtpManager.addValidDevice(0);
        mMtpManager.addValidDevice(1);
        // Not set roots for device 0 so that MtpManagerMock#getRoots throws IOException.
        mMtpManager.setRoots(1, new MtpRoot[] {
                new MtpRoot(
                        1 /* deviceId */,
                        1 /* storageId */,
                        "Device B" /* device model name */,
                        "Storage B" /* volume description */,
                        2048 /* free space */,
                        4096 /* total space */,
                        "Identifier B" /* no volume identifier */)
        });
        {
            mProvider.openDevice(0);
            mProvider.openDevice(1);
            mResolver.waitForNotification(ROOTS_URI, 1);

            final Cursor cursor = mProvider.queryRoots(null);
            assertEquals(1, cursor.getCount());
            cursor.moveToNext();
            assertEquals("1", cursor.getString(0));
            assertEquals(Root.FLAG_SUPPORTS_IS_CHILD | Root.FLAG_SUPPORTS_CREATE, cursor.getInt(1));
            // TODO: Add storage icon for MTP devices.
            assertTrue(cursor.isNull(2) /* icon */);
            assertEquals("Device B Storage B", cursor.getString(3));
            assertEquals("1_1_0", cursor.getString(4));
            assertEquals(2048, cursor.getInt(5));
        }
    }

    public void testQueryDocument() throws IOException {
        mMtpManager.addValidDevice(0);
        mProvider.openDevice(0);
        mMtpManager.setObjectInfo(0, new MtpObjectInfo.Builder()
                .setObjectHandle(2)
                .setStorageId(1)
                .setFormat(MtpConstants.FORMAT_EXIF_JPEG)
                .setName("image.jpg")
                .setDateModified(1422716400000L)
                .setCompressedSize(1024 * 1024 * 5)
                .setThumbCompressedSize(1024 * 50)
                .build());
        final Cursor cursor = mProvider.queryDocument("0_1_2", null);
        assertEquals(1, cursor.getCount());

        cursor.moveToNext();

        assertEquals("0_1_2", cursor.getString(0));
        assertEquals("image/jpeg", cursor.getString(1));
        assertEquals("image.jpg", cursor.getString(2));
        assertEquals(1422716400000L, cursor.getLong(3));
        assertEquals(
                DocumentsContract.Document.FLAG_SUPPORTS_DELETE |
                DocumentsContract.Document.FLAG_SUPPORTS_WRITE |
                DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL,
                cursor.getInt(4));
        assertEquals(1024 * 1024 * 5, cursor.getInt(5));
    }

    public void testQueryDocument_directory() throws IOException {
        mMtpManager.addValidDevice(0);
        mProvider.openDevice(0);
        mMtpManager.setObjectInfo(0, new MtpObjectInfo.Builder()
                .setObjectHandle(2)
                .setStorageId(1)
                .setFormat(MtpConstants.FORMAT_ASSOCIATION)
                .setName("directory")
                .setDateModified(1422716400000L)
                .build());
        final Cursor cursor = mProvider.queryDocument("0_1_2", null);
        assertEquals(1, cursor.getCount());

        cursor.moveToNext();
        assertEquals("0_1_2", cursor.getString(0));
        assertEquals(DocumentsContract.Document.MIME_TYPE_DIR, cursor.getString(1));
        assertEquals("directory", cursor.getString(2));
        assertEquals(1422716400000L, cursor.getLong(3));
        assertEquals(
                DocumentsContract.Document.FLAG_SUPPORTS_DELETE |
                DocumentsContract.Document.FLAG_SUPPORTS_WRITE |
                DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE,
                cursor.getInt(4));
        assertEquals(0, cursor.getInt(5));
    }

    public void testQueryDocument_forRoot() throws IOException {
        mMtpManager.addValidDevice(0);
        mProvider.openDevice(0);
        mMtpManager.setRoots(0, new MtpRoot[] {
                new MtpRoot(
                        0 /* deviceId */,
                        1 /* storageId */,
                        "Device A" /* device model name */,
                        "Storage A" /* volume description */,
                        1024 /* free space */,
                        4096 /* total space */,
                        "" /* no volume identifier */)
        });
        final Cursor cursor = mProvider.queryDocument("0_1_0", null);
        assertEquals(1, cursor.getCount());

        cursor.moveToNext();
        assertEquals("0_1_0", cursor.getString(0));
        assertEquals(DocumentsContract.Document.MIME_TYPE_DIR, cursor.getString(1));
        assertEquals("Device A Storage A", cursor.getString(2));
        assertTrue(cursor.isNull(3));
        assertEquals(0, cursor.getInt(4));
        assertEquals(3072, cursor.getInt(5));
    }

    public void testQueryChildDocuments() throws Exception {
        mMtpManager.addValidDevice(0);
        mProvider.openDevice(0);
        mMtpManager.setObjectHandles(0, 0, -1, new int[] { 1 });

        mDatabase.startAddingRootDocuments(0);
        mDatabase.putRootDocuments(0, mResources, new MtpRoot[] {
                new MtpRoot(0, 0, "Device", "Storage", 1000, 1000, "")
        });
        mDatabase.stopAddingRootDocuments(0);

        mMtpManager.setObjectInfo(0, new MtpObjectInfo.Builder()
                .setObjectHandle(1)
                .setFormat(MtpConstants.FORMAT_EXIF_JPEG)
                .setName("image.jpg")
                .setCompressedSize(1024 * 1024 * 5)
                .setThumbCompressedSize(5 * 1024)
                .setProtectionStatus(MtpConstants.PROTECTION_STATUS_READ_ONLY)
                .build());

        final Cursor cursor = mProvider.queryChildDocuments("0_0_0", null, null);
        assertEquals(1, cursor.getCount());

        assertTrue(cursor.moveToNext());
        assertEquals("0_0_1", cursor.getString(0));
        assertEquals("image/jpeg", cursor.getString(1));
        assertEquals("image.jpg", cursor.getString(2));
        assertEquals(0, cursor.getLong(3));
        assertEquals(DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL, cursor.getInt(4));
        assertEquals(1024 * 1024 * 5, cursor.getInt(5));

        assertFalse(cursor.moveToNext());
    }

    public void testQueryChildDocuments_cursorError() throws Exception {
        mMtpManager.addValidDevice(0);
        mProvider.openDevice(0);
        try {
            mProvider.queryChildDocuments("0_0_0", null, null);
            fail();
        } catch (Throwable error) {
            assertTrue(error instanceof FileNotFoundException);
        }
    }

    public void testQueryChildDocuments_documentError() throws Exception {
        mMtpManager.addValidDevice(0);
        mProvider.openDevice(0);
        mMtpManager.setObjectHandles(0, 0, -1, new int[] { 1 });
        try {
            mProvider.queryChildDocuments("0_0_0", null, null);
            fail();
        } catch (Throwable error) {
            assertTrue(error instanceof FileNotFoundException);
        }
    }

    public void testDeleteDocument() throws IOException {
        mMtpManager.addValidDevice(0);
        mProvider.openDevice(0);
        mMtpManager.setObjectInfo(0, new MtpObjectInfo.Builder()
                .setObjectHandle(1)
                .setParent(2)
                .build());
        mProvider.deleteDocument("0_0_1");
        assertEquals(1, mResolver.getChangeCount(
                DocumentsContract.buildChildDocumentsUri(
                        MtpDocumentsProvider.AUTHORITY, "0_0_2")));
    }

    public void testDeleteDocument_error() throws IOException {
        mMtpManager.addValidDevice(0);
        mProvider.openDevice(0);
        mMtpManager.setObjectInfo(0, new MtpObjectInfo.Builder()
                .setObjectHandle(2)
                .build());
        try {
            mProvider.deleteDocument("0_0_1");
            fail();
        } catch (Throwable e) {
            assertTrue(e instanceof IOException);
        }
        assertEquals(0, mResolver.getChangeCount(
                DocumentsContract.buildChildDocumentsUri(
                        MtpDocumentsProvider.AUTHORITY, "0_0_2")));
    }
}
