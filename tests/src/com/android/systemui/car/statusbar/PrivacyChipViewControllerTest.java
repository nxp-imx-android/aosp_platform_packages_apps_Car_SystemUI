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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.content.Intent;
import android.os.Handler;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.filters.SmallTest;

import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.privacy.CarOngoingPrivacyChip;
import com.android.systemui.privacy.PrivacyItemController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class PrivacyChipViewControllerTest extends SysuiTestCase {

    private PrivacyChipViewController mPrivacyChipViewController;
    private FrameLayout mFrameLayout;

    @Captor
    private ArgumentCaptor<Intent> mIntentArgumentCaptor;
    @Captor
    private ArgumentCaptor<Runnable> mRunnableArgumentCaptor;

    @Mock
    private PrivacyItemController mPrivacyItemController;
    @Mock
    private Handler mHandler;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(/* testClass= */ this);
        mFrameLayout = new FrameLayout(mContext);
        CarOngoingPrivacyChip carOngoingPrivacyChip = new CarOngoingPrivacyChip(mContext);
        mContext = spy(mContext);
        mPrivacyChipViewController =
                new PrivacyChipViewController(mContext, mHandler, mPrivacyItemController);
        carOngoingPrivacyChip.setId(R.id.privacy_chip);
        mFrameLayout.addView(carOngoingPrivacyChip);
    }

    @Test
    public void addPrivacyChipView_privacyChipViewPresent_addCallbackCalled() {
        mPrivacyChipViewController.addPrivacyChipView(mFrameLayout);

        verify(mPrivacyItemController).addCallback(any());
    }

    @Test
    public void addPrivacyChipView_privacyChipViewNotPresent_addCallbackNotCalled() {
        mPrivacyChipViewController.addPrivacyChipView(new View(getContext()));

        verify(mPrivacyItemController, never()).addCallback(any());
    }

    @Test
    public void onClick_intentNotNull() {
        mPrivacyChipViewController.onClick(/* view= */ null);

        verify(mHandler).post(mRunnableArgumentCaptor.capture());
        mRunnableArgumentCaptor.getValue().run();
        verify(mContext).startActivityAsUser(mIntentArgumentCaptor.capture(), any());

        assertThat(mIntentArgumentCaptor.getValue()).isNotNull();
    }

    @Test
    public void onClick_intentActionReviewOngoingPermissionUsage() {
        mPrivacyChipViewController.onClick(/* view= */ null);

        verify(mHandler).post(mRunnableArgumentCaptor.capture());
        mRunnableArgumentCaptor.getValue().run();
        verify(mContext).startActivityAsUser(mIntentArgumentCaptor.capture(), any());

        assertThat(mIntentArgumentCaptor.getValue().getAction()).isEqualTo(
                Intent.ACTION_REVIEW_ONGOING_PERMISSION_USAGE);
    }

    @Test
    public void onClick_intentCategoryDefault() {
        mPrivacyChipViewController.onClick(/* view= */ null);

        verify(mHandler).post(mRunnableArgumentCaptor.capture());
        mRunnableArgumentCaptor.getValue().run();
        verify(mContext).startActivityAsUser(mIntentArgumentCaptor.capture(), any());

        assertThat(mIntentArgumentCaptor.getValue().getCategories()).containsExactly(
                Intent.CATEGORY_DEFAULT);
    }

    @Test
    public void onClick_intentFlagActivityNewTask() {
        mPrivacyChipViewController.onClick(/* view= */ null);

        verify(mHandler).post(mRunnableArgumentCaptor.capture());
        mRunnableArgumentCaptor.getValue().run();
        verify(mContext).startActivityAsUser(mIntentArgumentCaptor.capture(), any());

        assertThat(mIntentArgumentCaptor.getValue().getFlags()).isEqualTo(
                Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}