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

import android.mtp.MtpObjectInfo;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;

import java.util.Date;

class MtpDocument {
    static final int DUMMY_HANDLE_FOR_ROOT = 0;

    private final int mObjectHandle;
    private final int mFormat;
    private final String mName;
    private final Date mDateModified;
    private final int mSize;
    private final int mThumbSize;

    /**
     * Constructor for root document.
     */
    MtpDocument(MtpRoot root) {
        this(DUMMY_HANDLE_FOR_ROOT,
             0x3001,  // Directory.
             root.mDescription,
             null,  // Unknown,
             (int) Math.min(root.mMaxCapacity - root.mFreeSpace, Integer.MAX_VALUE),
             0);
    }

    MtpDocument(MtpObjectInfo objectInfo) {
        this(objectInfo.getObjectHandle(),
             objectInfo.getFormat(),
             objectInfo.getName(),
             objectInfo.getDateModified() != 0 ? new Date(objectInfo.getDateModified()) : null,
             objectInfo.getCompressedSize(),
             objectInfo.getThumbCompressedSize());
    }

    MtpDocument(int objectHandle,
                int format,
                String name,
                Date dateModified,
                int size,
                int thumbSize) {
        this.mObjectHandle = objectHandle;
        this.mFormat = format;
        this.mName = name;
        this.mDateModified = dateModified;
        this.mSize = size;
        this.mThumbSize = thumbSize;
    }

    int getSize() {
        return mSize;
    }

    String getMimeType() {
        // TODO: Add complete list of mime types.
        switch (mFormat) {
            case 0x3001:
                return DocumentsContract.Document.MIME_TYPE_DIR;
            case 0x3009:
                return "audio/mp3";
            case 0x3801:
                return "image/jpeg";
            default:
                return "";
        }
    }

    Object[] getRow(Identifier rootIdentifier, String[] columnNames) {
        final Object[] rows = new Object[columnNames.length];
        for (int i = 0; i < columnNames.length; i++) {
            if (Document.COLUMN_DOCUMENT_ID.equals(columnNames[i])) {
                final Identifier identifier = new Identifier(
                        rootIdentifier.mDeviceId, rootIdentifier.mStorageId, mObjectHandle);
                rows[i] = identifier.toDocumentId();
            } else if (Document.COLUMN_DISPLAY_NAME.equals(columnNames[i])) {
                rows[i] = mName;
            } else if (Document.COLUMN_MIME_TYPE.equals(columnNames[i])) {
                rows[i] = getMimeType();
            } else if (Document.COLUMN_LAST_MODIFIED.equals(columnNames[i])) {
                rows[i] = mDateModified != null ? mDateModified.getTime() : null;
            } else if (Document.COLUMN_FLAGS.equals(columnNames[i])) {
                int flag = 0;
                if (mObjectHandle != DUMMY_HANDLE_FOR_ROOT) {
                    flag |= DocumentsContract.Document.FLAG_SUPPORTS_DELETE;
                    if (mThumbSize > 0) {
                        flag |= DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL;
                    }
                }
                rows[i] = flag;
            } else if (Document.COLUMN_SIZE.equals(columnNames[i])) {
                rows[i] = mSize;
            } else {
                rows[i] = null;
            }
        }
        return rows;
    }
}
