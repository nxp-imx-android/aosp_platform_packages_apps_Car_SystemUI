/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.car.rvc;

import static com.android.systemui.car.rvc.RearViewCameraViewController.CLOSE_SYSTEM_DIALOG_REASON_KEY;
import static com.android.systemui.car.rvc.RearViewCameraViewController.CLOSE_SYSTEM_DIALOG_REASON_VALUE;

import static com.google.common.truth.Truth.assertThat;

import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.content.ComponentName;
import android.content.Intent;
import android.testing.TestableLooper;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.window.DisplayAreaOrganizer;

import androidx.test.filters.SmallTest;

import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.window.OverlayViewGlobalStateController;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

@CarSystemUiTest
@RunWith(MockitoJUnitRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class RearViewCameraViewControllerTest extends SysuiTestCase {
    private static final long DEFAULT_TIMEOUT_MS = 2_000;
    private static final String TEST_ACTIVITY_NAME = "testPackage/testActivity";

    private RearViewCameraViewController mRearViewCameraViewController;

    @Mock
    private OverlayViewGlobalStateController mOverlayViewGlobalStateController;

    private BlockingQueue<Intent> mCapturedIntents = new ArrayBlockingQueue<>(2);

    private void setUpRearViewCameraViewController(String testActivityName) {
        mContext.getOrCreateTestableResources().addOverride(
                R.string.config_rearViewCameraActivity, testActivityName);
        mRearViewCameraViewController = new RearViewCameraViewController(
                mContext,
                mContext.getOrCreateTestableResources().getResources(),
                mOverlayViewGlobalStateController,
                ActivityTaskManager.getInstance(),
                new DisplayAreaOrganizer(mContext.getMainExecutor())) {

            @Override
            void startRearViewCameraActivity(ActivityOptions unusedActivityOptions, Intent intent) {
                mCapturedIntents.offer(intent);
            }

            @Override
            void broadcastIntent(Intent intent) {
                mCapturedIntents.offer(intent);
            }
        };

        mRearViewCameraViewController.inflate((ViewGroup) LayoutInflater.from(mContext).inflate(
                R.layout.sysui_overlay_window, /* root= */ null));
    }

    @Test
    public void testEmptyResourceDisablesController() {
        setUpRearViewCameraViewController("");

        assertThat(mRearViewCameraViewController.isEnabled()).isFalse();
    }

    @Test
    public void testNonEmptyResourceEnablesController() {
        setUpRearViewCameraViewController(TEST_ACTIVITY_NAME);

        assertThat(mRearViewCameraViewController.isEnabled()).isTrue();
    }

    @Test
    public void testShowInternal() throws InterruptedException {
        setUpRearViewCameraViewController(TEST_ACTIVITY_NAME);
        assertThat(mRearViewCameraViewController.isShown()).isFalse();
        assertThat(mRearViewCameraViewController.mView).isNull();

        mRearViewCameraViewController.showInternal();
        mRearViewCameraViewController.mRvcView.getViewTreeObserver().dispatchOnGlobalLayout();

        assertThat(mRearViewCameraViewController.isShown()).isTrue();
        assertThat(mRearViewCameraViewController.mView).isNotNull();

        // Assert broadcast event
        Intent closeSystemDialogsIntent = mCapturedIntents.poll(2, TimeUnit.SECONDS);
        assertThat(closeSystemDialogsIntent.getAction()).isEqualTo(
                Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        assertThat(closeSystemDialogsIntent.getStringExtra(
                CLOSE_SYSTEM_DIALOG_REASON_KEY)).isEqualTo(CLOSE_SYSTEM_DIALOG_REASON_VALUE);

        // Assert intent to create RVC view was fired
        Intent rearViewCameraIntent = mCapturedIntents.poll(2, TimeUnit.SECONDS);
        ComponentName expectedComponent = ComponentName.unflattenFromString(TEST_ACTIVITY_NAME);
        assertThat(rearViewCameraIntent.getComponent()).isEqualTo(expectedComponent);
    }

    @Test
    public void testHideInternal() {
        setUpRearViewCameraViewController(TEST_ACTIVITY_NAME);
        assertThat(mRearViewCameraViewController.isShown()).isFalse();
        mRearViewCameraViewController.showInternal();
        assertThat(mRearViewCameraViewController.isShown()).isTrue();

        mRearViewCameraViewController.hideInternal();

        assertThat(mRearViewCameraViewController.isShown()).isFalse();
        assertThat(mRearViewCameraViewController.mView).isNull();
    }
}
