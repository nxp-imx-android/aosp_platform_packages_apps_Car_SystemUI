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
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.android.systemui.R;
import com.android.systemui.privacy.PrivacyChipBuilder;
import com.android.systemui.privacy.PrivacyItem;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Car optimized {@link com.android.systemui.privacy.OngoingPrivacyChip}.
 */
public class CarOngoingPrivacyChip extends FrameLayout {

    private final int mIconMargin;
    private final int mIconSize;
    @ColorInt
    private final int mIconColor;
    private final int mSidePadding;
    private final Drawable mBackgroundDrawable;

    private LinearLayout mIconsContainer;
    private FrameLayout mBackgroundView;

    private List<PrivacyItem> mPrivacyItems = Collections.emptyList();

    public CarOngoingPrivacyChip(@NotNull Context context) {
        this(context, /* attrs= */ null);
    }

    public CarOngoingPrivacyChip(@NotNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, /* defStyleAttrs= */ 0);
    }

    public CarOngoingPrivacyChip(@NotNull Context context,
            @Nullable AttributeSet attrs, int defStyleAttrs) {
        this(context, attrs, defStyleAttrs, /* defStyleRes= */ 0);
    }

    public CarOngoingPrivacyChip(@NotNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttrs, int defStyleRes) {
        super(context, attrs, defStyleAttrs, defStyleRes);
        Resources res = getResources();

        mIconMargin = Math.round(res.getDimension(R.dimen.privacy_chip_icon_margin));
        mIconSize = res.getDimensionPixelSize(R.dimen.privacy_chip_icon_size);
        mIconColor = res.getColor(R.color.status_bar_clock_color, getContext().getTheme());
        mSidePadding = Math.round(res.getDimension(R.dimen.privacy_chip_side_padding));
        mBackgroundDrawable =
                res.getDrawable(R.drawable.system_bar_background_pill, getContext().getTheme());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mBackgroundView = requireViewById(R.id.background);
        mIconsContainer = requireViewById(R.id.icons_container);
    }

    public List<PrivacyItem> getPrivacyItemList() {
        return mPrivacyItems;
    }

    /**
     * Sets current {@link PrivacyItem} list and updates view.
     */
    public void setPrivacyItemList(@NonNull List<PrivacyItem> privacyItemList) {
        mPrivacyItems = privacyItemList;
        updateView();
    }

    private void updateView() {
        mBackgroundView.setBackground(mBackgroundDrawable);
        mBackgroundView.setPaddingRelative(mSidePadding, /* top= */ 0, mSidePadding,
                /* bottom= */ 0);

        if (!mPrivacyItems.isEmpty()) {
            PrivacyChipBuilder builder = new PrivacyChipBuilder(getContext(), mPrivacyItems);
            generateContentDescription(builder.joinTypes());
            setIcons(builder.generateIcons(), mIconsContainer);
            FrameLayout.LayoutParams layoutParams =
                    (LayoutParams) mIconsContainer.getLayoutParams();
            layoutParams.gravity = Gravity.CENTER;
            mIconsContainer.setLayoutParams(layoutParams);
        } else {
            mIconsContainer.removeAllViews();
        }
        requestLayout();
    }

    private void setIcons(List<Drawable> icons, ViewGroup iconsContainer) {
        iconsContainer.removeAllViews();
        for (int i = 0; i < icons.size(); i++) {
            Drawable icon = icons.get(i);
            icon.mutate().setTint(mIconColor);
            ImageView imageView = new ImageView(getContext());
            imageView.setImageDrawable(icon);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            mIconsContainer.addView(imageView, mIconSize, mIconSize);
            if (i != 0) {
                MarginLayoutParams layoutParams = (MarginLayoutParams) imageView.getLayoutParams();
                layoutParams.setMarginStart(mIconMargin);
                imageView.setLayoutParams(layoutParams);
            }
        }
    }

    private void generateContentDescription(String typesText) {
        setContentDescription(
                getResources().getString(R.string.ongoing_privacy_chip_content_multiple_apps,
                        typesText));
    }
}
