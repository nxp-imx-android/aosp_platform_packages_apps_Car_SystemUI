/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.car.statusbar;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.UserHandle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;

import com.android.systemui.R;
import com.android.systemui.car.privacy.MicPrivacyChip;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.privacy.OngoingPrivacyChip;
import com.android.systemui.privacy.PrivacyItem;
import com.android.systemui.privacy.PrivacyItemController;
import com.android.systemui.privacy.PrivacyType;

import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

/**
 * Controls a Privacy Chip view in system icons.
 */
@SysUISingleton
public class PrivacyChipViewController implements View.OnClickListener {
    private static final String TAG = "PrivacyChipViewController";

    private final PrivacyItemController mPrivacyItemController;
    private final Context mContext;
    private final Handler mMainHandler;
    private MicPrivacyChip mPrivacyChip;
    private boolean mAllIndicatorsEnabled;
    private boolean mMicCameraIndicatorsEnabled;
    private boolean mIsMicPrivacyChipVisible;

    private final PrivacyItemController.Callback mPicCallback =
            new PrivacyItemController.Callback() {
                @Override
                public void onPrivacyItemsChanged(@NonNull List<PrivacyItem> privacyItems) {
                    if (mPrivacyChip == null) {
                        return;
                    }

                    boolean shouldShowMicPrivacyChip = isMicPartOfPrivacyItems(privacyItems);
                    if (mIsMicPrivacyChipVisible == shouldShowMicPrivacyChip) {
                        return;
                    }

                    mIsMicPrivacyChipVisible = shouldShowMicPrivacyChip;
                    setChipVisibility(shouldShowMicPrivacyChip);
                }

                @Override
                public void onFlagAllChanged(boolean enabled) {
                    onAllIndicatorsToggled(enabled);
                }

                @Override
                public void onFlagMicCameraChanged(boolean enabled) {
                    onMicCameraToggled(enabled);
                }

                private void onMicCameraToggled(boolean enabled) {
                    if (mMicCameraIndicatorsEnabled != enabled) {
                        mMicCameraIndicatorsEnabled = enabled;
                    }
                }

                private void onAllIndicatorsToggled(boolean enabled) {
                    if (mAllIndicatorsEnabled != enabled) {
                        mAllIndicatorsEnabled = enabled;
                    }
                }
            };

    @Inject
    public PrivacyChipViewController(Context context, @Main Handler mainHandler,
            PrivacyItemController privacyItemController) {
        mContext = context;
        mMainHandler = mainHandler;
        mPrivacyItemController = privacyItemController;
        mIsMicPrivacyChipVisible = false;
    }

    @Override
    public void onClick(View view) {
        mMainHandler.post(() -> mContext.startActivityAsUser(
                new Intent(Intent.ACTION_REVIEW_ONGOING_PERMISSION_USAGE)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .addCategory(Intent.CATEGORY_DEFAULT),
                UserHandle.CURRENT));
    }

    private boolean isMicPartOfPrivacyItems(@NonNull List<PrivacyItem> privacyItems) {
        Optional<PrivacyItem> optionalMicPrivacyItem = privacyItems.stream()
                .filter(privacyItem -> privacyItem.getPrivacyType()
                        .equals(PrivacyType.TYPE_MICROPHONE))
                .findAny();
        return optionalMicPrivacyItem.isPresent();
    }

    /**
     * Finds the {@link OngoingPrivacyChip} and sets {@link PrivacyItemController}'s callback.
     */
    public void addPrivacyChipView(View view) {
        if (mPrivacyChip == null) {
            mPrivacyChip = view.findViewById(R.id.privacy_chip);
            if (mPrivacyChip == null) return;
        }

        mPrivacyChip.setOnClickListener(this);
        mAllIndicatorsEnabled = mPrivacyItemController.getAllIndicatorsAvailable();
        mMicCameraIndicatorsEnabled = mPrivacyItemController.getMicCameraAvailable();
        mPrivacyItemController.addCallback(mPicCallback);
    }

    /**
     * Cleans up the controller and removes callback.
     */
    public void removeAll() {
        if (mPrivacyChip != null) {
            mPrivacyChip.setOnClickListener(null);
        }

        mPrivacyItemController.removeCallback(mPicCallback);
    }

    private void setChipVisibility(boolean chipVisible) {
        if (mPrivacyChip == null) {
            return;
        }

        // Since this is launched using a callback thread, its UI based elements need
        // to execute on main executor.
        mContext.getMainExecutor().execute(() -> {
            if (chipVisible && getChipEnabled()) {
                mPrivacyChip.animateIn();
            } else {
                mPrivacyChip.animateOut();
            }
        });
    }

    private boolean getChipEnabled() {
        return mMicCameraIndicatorsEnabled || mAllIndicatorsEnabled;
    }
}
