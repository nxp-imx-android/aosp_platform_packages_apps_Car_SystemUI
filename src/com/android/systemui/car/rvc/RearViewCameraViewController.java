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

import static android.app.ActivityTaskManager.INVALID_TASK_ID;

import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.app.TaskStackListener;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.RemoteException;
import android.util.Slog;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout.LayoutParams;
import android.window.DisplayAreaAppearedInfo;
import android.window.DisplayAreaOrganizer;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.R;
import com.android.systemui.car.window.OverlayViewController;
import com.android.systemui.car.window.OverlayViewGlobalStateController;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;

import javax.inject.Inject;

/** View controller for the rear view camera. */
@SysUISingleton
public class RearViewCameraViewController extends OverlayViewController {
    private static final String TAG = "RearViewCameraView";
    private static final boolean DBG = false;

    private final Context mContext;
    private final ActivityTaskManager mActivityTaskManager;
    private final ComponentName mRearViewCameraActivity;
    private final DisplayAreaOrganizer mDisplayAreaOrganizer;

    ViewGroup mRvcView;
    private int mRvcTaskId = INVALID_TASK_ID;

    private final LayoutParams mRvcViewLayoutParams = new LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, /* weight= */ 1.0f);

    @VisibleForTesting
    View mView;

    private final TaskStackListener mTaskStackListener = new TaskStackListener() {
        @Override
        public void onTaskCreated(int taskId, ComponentName componentName) throws RemoteException {
            if (DBG) {
                Slog.d(TAG, "onTaskCreated: taskId=" + taskId + ", componentName="
                        + componentName + ", RvcTaskId=" + mRvcTaskId);
            }
            if (mRearViewCameraActivity.equals(componentName)) {
                mRvcTaskId = taskId;
            }
        }
    };

    private final RvcLayoutListener mRvcLayoutListener = new RvcLayoutListener();

    @Inject
    public RearViewCameraViewController(
            Context context,
            @Main Resources resources,
            OverlayViewGlobalStateController overlayViewGlobalStateController,
            ActivityTaskManager activityTaskManager,
            DisplayAreaOrganizer displayAreaOrganizer) {
        super(R.id.rear_view_camera_stub, overlayViewGlobalStateController);
        mContext = context;
        String rearViewCameraActivityName = resources.getString(
                R.string.config_rearViewCameraActivity);
        if (!rearViewCameraActivityName.isEmpty()) {
            mRearViewCameraActivity = ComponentName.unflattenFromString(rearViewCameraActivityName);
            if (DBG) Slog.d(TAG, "mRearViewCameraActivity=" + mRearViewCameraActivity);
        } else {
            mRearViewCameraActivity = null;
            Slog.e(TAG, "RearViewCameraViewController is disabled, since no Activity is defined");
        }
        mActivityTaskManager = activityTaskManager;
        if (isEnabled()) {
            mActivityTaskManager.registerTaskStackListener(mTaskStackListener);
        }
        mDisplayAreaOrganizer = displayAreaOrganizer;
    }

    @Override
    protected void onFinishInflate() {
        mRvcView = getLayout().findViewById(R.id.rear_view_camera_container);
        mRvcView.getViewTreeObserver().addOnGlobalLayoutListener(mRvcLayoutListener);
        getLayout().findViewById(R.id.close_button).setOnClickListener(v -> {
            stop();
        });
    }

    @Override
    protected void hideInternal() {
        super.hideInternal();
        if (DBG) Slog.d(TAG, "hideInternal: mActivityView=" + mView);
        if (mView == null) return;
        mRvcView.removeView(mView);
        // Release ActivityView since the Activity on ActivityView (with showWhenLocked flag) keeps
        // running even if ActivityView is hidden.
        mView = null;
    }

    @Override
    protected void showInternal() {
        super.showInternal();
        if (DBG) Slog.d(TAG, "showInternal: mActivityView=" + mView + ", RvcTaskId="
                + mRvcTaskId);
        if (mView != null) return;
        mView = new View(mRvcView.getContext());
        mView.setLayoutParams(mRvcViewLayoutParams);
        mRvcView.addView(mView, /* index= */ 0);
    }

    @VisibleForTesting
    void startRearViewCameraActivity(ActivityOptions activityOptions, Intent rearViewCameraIntent) {
        mContext.startActivity(rearViewCameraIntent, activityOptions.toBundle());
    }

    boolean isShown() {
        return mView != null;
    }

    boolean isEnabled() {
        return mRearViewCameraActivity != null;
    }

    @Override
    protected boolean shouldShowHUN() {
        return false;
    }

    @Override
    protected boolean shouldShowWhenOccluded() {
        // Returns true to show it on top of Keylock.
        return true;
    }

    @Override
    protected boolean shouldShowSystemBarInsets() {
        return true;
    }

    @Override
    protected boolean shouldShowStatusBarInsets() {
        return true;
    }

    // This listener contains the logic for creating and removing RVC display area. The geometry of
    // RVC display area is based on the one from {@link mRvcView}.
    private class RvcLayoutListener implements ViewTreeObserver.OnGlobalLayoutListener {

        private DisplayAreaAppearedInfo mDisplayAreaAppearedInfo;

        @Override
        public void onGlobalLayout() {
            if (DBG) {
                Slog.d(TAG, "onGlobalLayout: isShown=" + isShown()
                        + ", mRvcTaskId=" + mRvcTaskId + ", mDisplayAreaAppearedInfo="
                        + mDisplayAreaAppearedInfo);
            }
            if (isShown() && mDisplayAreaAppearedInfo == null) {
                createRvcDisplayArea();
            } else if (!isShown() && mDisplayAreaAppearedInfo != null) {
                removeRvcDisplayArea();
            }
        }

        private void createRvcDisplayArea() {
            Rect rect = new Rect();
            mRvcView.getBoundsOnScreen(rect);

            mDisplayAreaAppearedInfo = mDisplayAreaOrganizer.createTaskDisplayArea(
                    Display.DEFAULT_DISPLAY, DisplayAreaOrganizer.FEATURE_ROOT, "RVC");
            WindowContainerToken token = mDisplayAreaAppearedInfo.getDisplayAreaInfo().token;
            WindowContainerTransaction wct = new WindowContainerTransaction();
            wct.setBounds(token, rect);

            wct.setHidden(token, /* hidden= */ false);
            mDisplayAreaOrganizer.applyTransaction(wct);

            SurfaceControl surfaceControl = mDisplayAreaAppearedInfo.getLeash();
            SurfaceControl.Transaction st = new SurfaceControl.Transaction();
            st.setGeometry(surfaceControl, /* sourceCrop= */ null, rect, Surface.ROTATION_0);
            st.setVisibility(surfaceControl, true);
            st.apply();

            Intent rearViewCameraIntent = new Intent(Intent.ACTION_MAIN)
                    .setComponent(mRearViewCameraActivity)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                    .addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

            ActivityOptions activityOptions = ActivityOptions.makeBasic().setLaunchTaskDisplayArea(
                    token);
            startRearViewCameraActivity(activityOptions, rearViewCameraIntent);
        }

        private void removeRvcDisplayArea() {
            if (mRvcTaskId != INVALID_TASK_ID) {
                mActivityTaskManager.removeTask(mRvcTaskId);
                mRvcTaskId = INVALID_TASK_ID;
            }

            WindowContainerToken token = mDisplayAreaAppearedInfo.getDisplayAreaInfo().token;
            WindowContainerTransaction wct = new WindowContainerTransaction();
            wct.setHidden(token, true);
            mDisplayAreaOrganizer.applyTransaction(wct);

            SurfaceControl surfaceControl = mDisplayAreaAppearedInfo.getLeash();
            SurfaceControl.Transaction st = new SurfaceControl.Transaction();
            st.setVisibility(surfaceControl, false);
            st.apply();

            mDisplayAreaOrganizer.deleteTaskDisplayArea(token);
            mDisplayAreaAppearedInfo = null;
        }
    }
}
