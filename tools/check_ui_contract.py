#!/usr/bin/env python3
"""Source-level UI guardrails only. These checks do not claim rendered-device validation."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
UI=ROOT/'app/src/main/java/ls/iqooaugment/com/ui'

def main():
    main=(UI/'MainActivity.java').read_text(encoding='utf-8')
    kit=(UI/'UiKit.java').read_text(encoding='utf-8')
    theme=(UI/'AppearanceController.java').read_text(encoding='utf-8')
    glass=(UI/'LiquidGlassLayout.java').read_text(encoding='utf-8')
    checks={
        'appearance route removed':'APPEARANCE=' not in main and 'renderAppearance(' not in main,
        'appearance controls removed':'外观设置' not in main and 'setRounded(' not in main and 'setMode(' not in main,
        'old preferences ignored':'getSharedPreferences' not in theme,
        'system night mode retained':'UI_MODE_NIGHT_MASK' in theme,
        'fixed small glass radius':'setCornerRadius(4*d)' in glass,
        'native shared backdrop':'new IceBackdrop(ui.palette.dark)' in main,
        'flat button elevation':'setStateListAnimator(null)' in kit and 'setElevation(0)' in kit,
        'plain action rows no false navigation':'row.setBackground(touch())' in kit,
        'centered navigation text':'label.setGravity(Gravity.CENTER)' in main,
        'search header scrolls':'list.addHeaderView(header,null,false)' in main,
        'interactive search header':'list.setItemsCanFocus(true)' in main,
        'stale space filter normalized':'noneMatch(t->t.key.userId==homeSpace))homeSpace=-1' in main,
        'summary uses activation requirements':'boolean ready=enableBlocker().isEmpty()' in main,
        'scroll only stored after layout':'pageScroll.getHeight()>0' in main,
        'keyboard chrome adapts':'updateKeyboardChrome(' in main,
        'shared breadcrumb gap':'content.setPadding(dp(16),dp(DETAIL_CONTENT_GAP_DP),dp(16),0)' in kit,
        'first section has no extra margin':'lp.topMargin=first?0:dp(16)' in kit,
        'scalable path size':'UiMetrics.PATH_TEXT_SP' in kit,
        'no manual scaledDensity correction':'scaledDensity' not in kit,
        'disabled dialog actions styled':'button.setTextColor(enabledColors(palette.accent))' in kit,
        'disabled scan choices styled':'box.setTextColor(ui.enabledColors(ui.palette.text))' in main,
        'scope confirmation preserved':'选择要读取应用的空间' in main and 'model.scan(selected)' in main,
        'unsaved choices protected':'放弃未新增的选择？' in main,
        'target activation guard preserved':'if(!ensureReady())return' in main,
        'diagnostics confirmation preserved':'复制脱敏诊断？' in main and 'Diagnostics.summary(' in main,
    }
    for name,ok in checks.items():
        if not ok:raise AssertionError(name)
    print(f'PASS: {len(checks)} UI source-contract checks (not screenshot or interaction tests).')
    return len(checks)
if __name__=='__main__':main()
