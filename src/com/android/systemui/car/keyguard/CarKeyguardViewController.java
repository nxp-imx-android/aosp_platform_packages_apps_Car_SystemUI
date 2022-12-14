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

package com.android.systemui.car.keyguard;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewRootImpl;

import androidx.annotation.VisibleForTesting;

import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardViewController;
import com.android.keyguard.ViewMediatorCallback;
import com.android.systemui.R;
import com.android.systemui.car.systembar.CarSystemBarController;
import com.android.systemui.car.window.OverlayViewController;
import com.android.systemui.car.window.OverlayViewGlobalStateController;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.statusbar.phone.BiometricUnlockController;
import com.android.systemui.statusbar.phone.KeyguardBouncer;
import com.android.systemui.statusbar.phone.KeyguardBouncer.Factory;
import com.android.systemui.statusbar.phone.KeyguardBypassController;
import com.android.systemui.statusbar.phone.NotificationPanelViewController;
import com.android.systemui.statusbar.phone.StatusBar;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.util.concurrency.DelayableExecutor;

import javax.inject.Inject;

import dagger.Lazy;

/**
 * Automotive implementation of the {@link KeyguardViewController}. It controls the Keyguard View
 * that is mounted to the SystemUIOverlayWindow.
 */
@SysUISingleton
public class CarKeyguardViewController extends OverlayViewController implements
        KeyguardViewController {
    private static final String TAG = "CarKeyguardViewController";
    private static final boolean DEBUG = true;

    private final DelayableExecutor mMainExecutor;
    private final KeyguardStateController mKeyguardStateController;
    private final KeyguardUpdateMonitor mKeyguardUpdateMonitor;
    private final Lazy<BiometricUnlockController> mBiometricUnlockControllerLazy;
    private final ViewMediatorCallback mViewMediatorCallback;
    private final CarSystemBarController mCarSystemBarController;
    private final Factory mKeyguardBouncerFactory;
    // Needed to instantiate mBouncer.
    private final KeyguardBouncer.BouncerExpansionCallback mExpansionCallback =
            new KeyguardBouncer.BouncerExpansionCallback() {
                @Override
                public void onFullyShown() {
                }

                @Override
                public void onStartingToHide() {
                }

                @Override
                public void onStartingToShow() {
                }

                @Override
                public void onFullyHidden() {
                }
            };

    private KeyguardBouncer mBouncer;
    private OnKeyguardCancelClickedListener mKeyguardCancelClickedListener;
    private boolean mShowing;
    private boolean mIsOccluded;

    @Inject
    public CarKeyguardViewController(
            @Main DelayableExecutor mainExecutor,
            OverlayViewGlobalStateController overlayViewGlobalStateController,
            KeyguardStateController keyguardStateController,
            KeyguardUpdateMonitor keyguardUpdateMonitor,
            Lazy<BiometricUnlockController> biometricUnlockControllerLazy,
            ViewMediatorCallback viewMediatorCallback,
            CarSystemBarController carSystemBarController,
            KeyguardBouncer.Factory keyguardBouncerFactory) {

        super(R.id.keyguard_stub, overlayViewGlobalStateController);

        mMainExecutor = mainExecutor;
        mKeyguardStateController = keyguardStateController;
        mKeyguardUpdateMonitor = keyguardUpdateMonitor;
        mBiometricUnlockControllerLazy = biometricUnlockControllerLazy;
        mViewMediatorCallback = viewMediatorCallback;
        mCarSystemBarController = carSystemBarController;
        mKeyguardBouncerFactory = keyguardBouncerFactory;
    }

    @Override
    protected int getFocusAreaViewId() {
        return R.id.keyguard_container;
    }

    @Override
    protected boolean shouldShowNavigationBarInsets() {
        return true;
    }

    @Override
    public void onFinishInflate() {
        mBouncer = mKeyguardBouncerFactory
                .create(getLayout().findViewById(R.id.keyguard_container), mExpansionCallback);
        mBiometricUnlockControllerLazy.get().setKeyguardViewController(this);
    }

    @Override
    public void notifyKeyguardAuthenticated(boolean strongAuth) {
        if (mBouncer != null) {
            mBouncer.notifyKeyguardAuthenticated(strongAuth);
        }
    }

    @Override
    public void showBouncer(boolean scrimmed) {
        if (mShowing && !mBouncer.isShowing()) {
            mBouncer.show(/* resetSecuritySelection= */ false);
        }
    }

    @Override
    public void show(Bundle options) {
        if (mShowing) return;

        mShowing = true;
        mKeyguardStateController.notifyKeyguardState(mShowing, /* occluded= */ false);
        mCarSystemBarController.showAllKeyguardButtons(/* isSetUp= */ true);
        start();
        reset(/* hideBouncerWhenShowing= */ false);
        notifyKeyguardUpdateMonitor();
    }

    @Override
    public void hide(long startTime, long fadeoutDuration) {
        if (!mShowing) return;

        mViewMediatorCallback.readyForKeyguardDone();
        mShowing = false;
        mKeyguardStateController.notifyKeyguardState(mShowing, /* occluded= */ false);
        mBouncer.hide(/* destroyView= */ true);
        mCarSystemBarController.showAllNavigationButtons(/* isSetUp= */ true);
        stop();
        mKeyguardStateController.notifyKeyguardDoneFading();
        mMainExecutor.execute(mViewMediatorCallback::keyguardGone);
        notifyKeyguardUpdateMonitor();
    }

    @Override
    public void reset(boolean hideBouncerWhenShowing) {
        mMainExecutor.execute(() -> {
            if (mShowing) {
                if (mBouncer != null) {
                    if (!mBouncer.isSecure()) {
                        dismissAndCollapse();
                    }
                    resetBouncer();
                }
                mKeyguardUpdateMonitor.sendKeyguardReset();
                notifyKeyguardUpdateMonitor();
            } else {
                // This is necessary in order to address an inconsistency between the keyguard
                // service and the keyguard views.
                // TODO: Investigate the source of the inconsistency.
                show(/* options= */ null);
            }
        });
    }

    @Override
    public void onFinishedGoingToSleep() {
        if (mBouncer != null) {
            mBouncer.onScreenTurnedOff();
        }
    }

    @Override
    public void setOccluded(boolean occluded, boolean animate) {
        mIsOccluded = occluded;
        getOverlayViewGlobalStateController().setOccluded(occluded);
        if (occluded) {
            mCarSystemBarController.showAllOcclusionButtons(/* isSetup= */ true);
        } else {
            if (mShowing && mBouncer.isSecure()) {
                mCarSystemBarController.showAllKeyguardButtons(/* isSetup= */ true);
            } else {
                mCarSystemBarController.showAllNavigationButtons(/* isSetUp= */ true);
            }
        }
    }

    @Override
    public void onCancelClicked() {
        if (mBouncer == null) return;

        getOverlayViewGlobalStateController().setWindowNeedsInput(/* needsInput= */ false);

        mBouncer.hide(/* destroyView= */ true);
        mKeyguardCancelClickedListener.onCancelClicked();
    }

    @Override
    public boolean isShowing() {
        return mShowing;
    }

    @Override
    public void dismissAndCollapse() {
        // If dismissing and collapsing Keyguard is requested (e.g. by a Keyguard-dismissing
        // Activity) while Keyguard is occluded, unocclude Keyguard so the user can authenticate to
        // dismiss Keyguard.
        if (mIsOccluded) {
            setOccluded(/* occluded= */ false, /* animate= */ false);
        }
        if (mBouncer != null && !mBouncer.isSecure()) {
            hide(/* startTime= */ 0, /* fadeoutDuration= */ 0);
        }
    }

    @Override
    public void startPreHideAnimation(Runnable finishRunnable) {
        if (mBouncer == null) return;

        mBouncer.startPreHideAnimation(finishRunnable);
    }

    @Override
    public void setNeedsInput(boolean needsInput) {
        getOverlayViewGlobalStateController().setWindowNeedsInput(needsInput);
    }

    /**
     * Add listener for keyguard cancel clicked.
     */
    public void registerOnKeyguardCancelClickedListener(
            OnKeyguardCancelClickedListener keyguardCancelClickedListener) {
        mKeyguardCancelClickedListener = keyguardCancelClickedListener;
    }

    /**
     * Remove listener for keyguard cancel clicked.
     */
    public void unregisterOnKeyguardCancelClickedListener(
            OnKeyguardCancelClickedListener keyguardCancelClickedListener) {
        mKeyguardCancelClickedListener = null;
    }

    @Override
    public ViewRootImpl getViewRootImpl() {
        return ((View) getLayout().getParent()).getViewRootImpl();
    }

    @Override
    public boolean isBouncerShowing() {
        return mBouncer != null && mBouncer.isShowing();
    }

    @Override
    public boolean bouncerIsOrWillBeShowing() {
        return mBouncer != null && (mBouncer.isShowing() || mBouncer.inTransit());
    }

    @Override
    public void keyguardGoingAway() {
        // no-op
    }

    @Override
    public void setKeyguardGoingAwayState(boolean isKeyguardGoingAway) {
        // no-op
    }

    @Override
    public void onStartedGoingToSleep() {
        // no-op
    }

    @Override
    public void onStartedWakingUp() {
        // no-op
    }

    @Override
    public void onScreenTurningOn() {
        // no-op
    }

    @Override
    public void onScreenTurnedOn() {
        // no-op
    }

    @Override
    public boolean shouldDisableWindowAnimationsForUnlock() {
        return false;
    }

    @Override
    public boolean isGoingToNotificationShade() {
        return false;
    }

    @Override
    public boolean isUnlockWithWallpaper() {
        return false;
    }

    @Override
    public boolean shouldSubtleWindowAnimationsForUnlock() {
        return false;
    }

    @Override
    public void blockPanelExpansionFromCurrentTouch() {
        // no-op
    }

    @Override
    public void registerStatusBar(StatusBar statusBar, ViewGroup container,
            NotificationPanelViewController notificationPanelViewController,
            BiometricUnlockController biometricUnlockController,
            View notificationContainer, KeyguardBypassController bypassController) {
        // no-op
    }

    /**
     * Hides Keyguard so that the transitioning Bouncer can be hidden until it is prepared. To be
     * called by {@link com.android.systemui.car.userswitcher.FullscreenUserSwitcherViewMediator}
     * when a new user is selected.
     */
    public void hideKeyguardToPrepareBouncer() {
        getLayout().setVisibility(View.INVISIBLE);
    }

    @VisibleForTesting
    void setKeyguardBouncer(KeyguardBouncer keyguardBouncer) {
        mBouncer = keyguardBouncer;
    }

    private void revealKeyguardIfBouncerPrepared() {
        int reattemptDelayMillis = 50;
        Runnable revealKeyguard = () -> {
            if (mBouncer == null) {
                if (DEBUG) {
                    Log.d(TAG, "revealKeyguardIfBouncerPrepared: revealKeyguard request is ignored "
                            + "since the Bouncer has not been initialized yet.");
                }
                return;
            }
            if (!mBouncer.inTransit() || !mBouncer.isSecure()) {
                showInternal();
            } else {
                if (DEBUG) {
                    Log.d(TAG, "revealKeyguardIfBouncerPrepared: Bouncer is not prepared "
                            + "yet so reattempting after " + reattemptDelayMillis + "ms.");
                }
                mMainExecutor.executeDelayed(this::revealKeyguardIfBouncerPrepared,
                        reattemptDelayMillis);
            }
        };
        mMainExecutor.execute(revealKeyguard);
    }

    private void notifyKeyguardUpdateMonitor() {
        mKeyguardUpdateMonitor.onKeyguardVisibilityChanged(mShowing);
        if (mBouncer != null) {
            mKeyguardUpdateMonitor.sendKeyguardBouncerChanged(isBouncerShowing());
        }
    }

    private void resetBouncer() {
        mMainExecutor.execute(() -> {
            hideInternal();
            mBouncer.hide(/* destroyView= */ false);
            mBouncer.show(/* resetSecuritySelection= */ true);
            revealKeyguardIfBouncerPrepared();
        });
    }

    /**
     * Defines a callback for keyguard cancel button clicked listeners.
     */
    public interface OnKeyguardCancelClickedListener {
        /**
         * Called when keyguard cancel button is clicked.
         */
        void onCancelClicked();
    }
}
