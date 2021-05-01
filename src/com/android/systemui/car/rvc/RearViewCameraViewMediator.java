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

import android.car.Car;
import android.car.evs.CarEvsManager;
import android.car.evs.CarEvsManager.CarEvsStatusListener;
import android.car.evs.CarEvsStatus;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.UserHandle;
import android.util.Slog;

import androidx.annotation.NonNull;

import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.window.OverlayViewMediator;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;

import java.util.concurrent.Executor;

import javax.inject.Inject;

/**
 * View mediator for the rear view camera (RVC), which monitors the gear changes and shows
 * the RVC when the gear position is R and otherwise it hides the RVC.
 */
@SysUISingleton
public class RearViewCameraViewMediator implements OverlayViewMediator {
    private static final String TAG = "RearViewCameraView";
    private static final boolean DBG = false;

    private final RearViewCameraViewController mRearViewCameraViewController;
    private final CarServiceProvider mCarServiceProvider;
    private final BroadcastDispatcher mBroadcastDispatcher;
    private final Executor mMainExecutor;

    private CarEvsManager mEvsManager;
    private final CarEvsStatusListener mEvsStatusListener = new CarEvsStatusListener() {
        @Override
        public void onStatusChanged(@NonNull CarEvsStatus status) {
            if (DBG) Slog.d(TAG, "onStatusChanged status=" + status);
            if (status.getServiceType() != CarEvsManager.SERVICE_TYPE_REARVIEW) {
                return;
            }
            switch (status.getState()) {
                case CarEvsManager.SERVICE_STATE_REQUESTED:
                    mRearViewCameraViewController.setSessionToken(
                            mEvsManager.generateSessionToken());
                    mRearViewCameraViewController.start();
                    break;
                case CarEvsManager.SERVICE_STATE_INACTIVE:
                case CarEvsManager.SERVICE_STATE_UNAVAILABLE:
                    mRearViewCameraViewController.stop();
                    break;
            }
        }
    };

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DBG) Slog.d(TAG, "onReceive: " + intent);
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(intent.getAction())
                    && !CLOSE_SYSTEM_DIALOG_REASON_VALUE.equals(
                            intent.getStringExtra(CLOSE_SYSTEM_DIALOG_REASON_KEY))
                    && mRearViewCameraViewController.isShown()) {
                mRearViewCameraViewController.stop();
            }
        }
    };

    @Inject
    public RearViewCameraViewMediator(
            RearViewCameraViewController rearViewCameraViewController,
            CarServiceProvider carServiceProvider,
            BroadcastDispatcher broadcastDispatcher,
            @Main Executor mainExecutor) {
        if (DBG) Slog.d(TAG, "RearViewCameraViewMediator:init");
        mRearViewCameraViewController = rearViewCameraViewController;
        mCarServiceProvider = carServiceProvider;
        mBroadcastDispatcher = broadcastDispatcher;
        mMainExecutor = mainExecutor;
    }

    @Override
    public void registerListeners() {
        if (DBG) Slog.d(TAG, "RearViewCameraViewMediator:registerListeners");
        if (!mRearViewCameraViewController.isEnabled()) {
            Slog.i(TAG, "RearViewCameraViewController isn't enabled");
            return;
        }

        mCarServiceProvider.addListener(car -> {
            mEvsManager = (CarEvsManager) car.getCarManager(Car.CAR_EVS_SERVICE);
            if (mEvsManager == null) {
                Slog.e(TAG, "Unable to get CarEvsManager");
                return;
            }
            if (DBG) Slog.d(TAG, "Registering mEvsStatusListener.");
            mEvsManager.setStatusListener(mMainExecutor, mEvsStatusListener);
        });
        mBroadcastDispatcher.registerReceiver(mBroadcastReceiver,
                new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS), /* executor= */ null,
                UserHandle.ALL);
    }

    @Override
    public void setupOverlayContentViewControllers() {}
}
