package org.ergoplatform.ios.ui

import org.robovm.apple.uikit.*

open class CommonButton(caption: String): UIButton(UIButtonType.System) {

    init {
        titleLabel?.font = UIFont.getSystemFont(FONT_SIZE_BODY1)
        contentEdgeInsets = UIEdgeInsets(8.0, 8.0, 8.0, 8.0)
        layer.cornerRadius = 18.0
        layer.setMasksToBounds(false)
        setTitleColor(UIColor.label(), UIControlState.Normal)
        setTitleColor(UIColor.secondaryLabel(), UIControlState.Highlighted)
        setTitle(
            caption,
            UIControlState.Normal
        )

        backgroundColor = UIColor.systemGray4()
    }
}

class PrimaryButton(caption: String): CommonButton(caption) {

    init {
        backgroundColor = uiColorErgo
    }
}