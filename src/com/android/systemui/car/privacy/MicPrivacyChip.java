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

package com.android.systemui.car.privacy;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.constraintlayout.motion.widget.MotionLayout;

import com.android.systemui.R;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Car optimized Mic Privacy Chip View that is shown when microphone is being used.
 *
 * State flows:
 * Base state:
 * <ul>
 * <li>INVISIBLE - Start Mic Use ->> Mic Status?</li>
 * </ul>
 * Mic On:
 * <ul>
 * <li>Mic Status? - On ->> ACTIVE_INIT</li>
 * <li>ACTIVE_INIT - delay ->> ACTIVE</li>
 * <li>ACTIVE - Stop Mic Use ->> INACTIVE</li>
 * <li>INACTIVE - delay ->> INVISIBLE</li>
 * </ul>
 * Mic Off:
 * <ul>
 * <li>Mic Status? - Off ->> MICROPHONE_OFF</li>
 * <li>MICROPHONE_OFF - delay ->> INVISIBLE</li>
 * </ul>
 */
public class MicPrivacyChip extends MotionLayout {
    private final static boolean DEBUG = false;
    private final static String TAG = "MicPrivacyChip";
    private final static String TYPES_TEXT_MICROPHONE = "microphone";

    private final int mDelayPillToCircle;
    private final int mDelayToNoMicUsage;

    private AnimationStates mCurrentTransitionState;
    private boolean mIsInflated;
    private ScheduledExecutorService mExecutor;

    private enum AnimationStates {
        INVISIBLE,
        ACTIVE_INIT,
        ACTIVE,
        INACTIVE,
        MICROPHONE_OFF,
    }

    public MicPrivacyChip(@NonNull Context context) {
        this(context, /* attrs= */ null);
    }

    public MicPrivacyChip(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, /* defStyleAttrs= */ 0);
    }

    public MicPrivacyChip(@NonNull Context context,
            @Nullable AttributeSet attrs, int defStyleAttrs) {
        super(context, attrs, defStyleAttrs);

        mDelayPillToCircle = getResources().getInteger(R.integer.privacy_chip_pill_to_circle_delay);
        mDelayToNoMicUsage = getResources().getInteger(R.integer.privacy_chip_no_mic_usage_delay);

        mExecutor = Executors.newSingleThreadScheduledExecutor();
        mIsInflated = false;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mCurrentTransitionState = AnimationStates.INVISIBLE;
        mIsInflated = true;
    }

    private boolean isMicrophoneToggledOff() {
        // TODO(182826082): Implement Microphone off functionality
        return false;
    }

    private void setContentDescription(boolean isMicOff) {
        if (isMicOff) {
            // TODO(182826082): Implement Microphone off functionality
            setContentDescription(null);
        } else {
            setContentDescription(
                    getResources().getString(R.string.ongoing_privacy_chip_content_multiple_apps,
                            TYPES_TEXT_MICROPHONE));
        }
    }

    /**
     * Starts reveal animation for Mic Privacy Chip.
     */
    @UiThread
    public void animateIn() {
        if (!mIsInflated) {
            if (DEBUG) Log.d(TAG, "Layout not inflated");

            return;
        }

        if (mCurrentTransitionState == null) {
            if (DEBUG) Log.d(TAG, "Current transition state is null or empty.");

            return;
        }

        if (mCurrentTransitionState.equals(AnimationStates.INVISIBLE)) {
            if (DEBUG) Log.d(TAG, isMicrophoneToggledOff() ? "setTransition: micOffFromInvisible"
                    : "setTransition: activeInitFromInvisible");

            setTransition(isMicrophoneToggledOff() ? R.id.micOffFromInvisible
                    : R.id.activeInitFromInvisible);
        } else if (mCurrentTransitionState.equals(AnimationStates.INACTIVE)) {
            if (DEBUG) Log.d(TAG, isMicrophoneToggledOff() ? "setTransition: micOffFromInactive"
                    : "setTransition: activeInitFromInactive");

            setTransition(isMicrophoneToggledOff() ? R.id.micOffFromInactive
                    : R.id.activeInitFromInactive);
        } else {
            if (DEBUG) Log.d(TAG, "Early exit, mCurrentTransitionState= "
                    + mCurrentTransitionState);

            return;
        }

        mExecutor.shutdownNow();
        mExecutor = Executors.newSingleThreadScheduledExecutor();

        // TODO(182938429): Use Transition Listeners once ConstraintLayout 2.0.0 is being used.
        setContentDescription(false);
        setVisibility(View.VISIBLE);
        transitionToEnd();
        mCurrentTransitionState = AnimationStates.ACTIVE_INIT;
        if (!isMicrophoneToggledOff()) {
            mExecutor.schedule(MicPrivacyChip.this::animateToOrangeCircle, mDelayPillToCircle,
                    TimeUnit.MILLISECONDS);
        }
    }

    // TODO(182938429): Use Transition Listeners once ConstraintLayout 2.0.0 is being used.
    private void animateToOrangeCircle() {
        setTransition(R.id.activeFromActiveInit);

        // Since this is launched using a {@link ScheduledExecutorService}, its UI based elements
        // need to execute on main executor.
        getContext().getMainExecutor().execute(() -> {
            mCurrentTransitionState = AnimationStates.ACTIVE;
            transitionToEnd();
        });
    }

    /**
     * Starts conceal animation for Mic Privacy Chip.
     */
    @UiThread
    public void animateOut() {
        if (!mIsInflated) {
            if (DEBUG) Log.d(TAG, "Layout not inflated");

            return;
        }

        if (mCurrentTransitionState.equals(AnimationStates.ACTIVE_INIT)) {
            if (DEBUG) Log.d(TAG, "setTransition: inactiveFromActiveInit");

            setTransition(R.id.inactiveFromActiveInit);
        } else if (mCurrentTransitionState.equals(AnimationStates.ACTIVE)) {
            if (DEBUG) Log.d(TAG, "setTransition: inactiveFromActive");

            setTransition(R.id.inactiveFromActive);
        } else {
            if (DEBUG) Log.d(TAG, "Early exit, mCurrentTransitionState= "
                    + mCurrentTransitionState);

            return;
        }

        mExecutor.shutdownNow();
        mExecutor = Executors.newSingleThreadScheduledExecutor();

        if (mCurrentTransitionState.equals(AnimationStates.MICROPHONE_OFF)) {
            mCurrentTransitionState = AnimationStates.INACTIVE;
            mExecutor.schedule(MicPrivacyChip.this::reset, mDelayToNoMicUsage,
                    TimeUnit.MILLISECONDS);
            return;
        }

        // TODO(182938429): Use Transition Listeners once ConstraintLayout 2.0.0 is being used.
        mCurrentTransitionState = AnimationStates.INACTIVE;
        transitionToEnd();
        mExecutor.schedule(MicPrivacyChip.this::reset, mDelayToNoMicUsage,
                TimeUnit.MILLISECONDS);
    }

    // TODO(182938429): Use Transition Listeners once ConstraintLayout 2.0.0 is being used.
    private void reset() {
        if (isMicrophoneToggledOff()) {
            if (DEBUG) Log.d(TAG, "setTransition: invisibleFromMicOff");

            setTransition(R.id.invisibleFromMicOff);
        } else {
            if (DEBUG) Log.d(TAG, "setTransition: invisibleFromInactive");

            setTransition(R.id.invisibleFromInactive);
        }

        // Since this is launched using a {@link ScheduledExecutorService}, its UI based elements
        // need to execute on main executor.
        getContext().getMainExecutor().execute(() -> {
            mCurrentTransitionState = AnimationStates.INVISIBLE;
            transitionToEnd();
            setVisibility(View.GONE);
        });
    }
}
