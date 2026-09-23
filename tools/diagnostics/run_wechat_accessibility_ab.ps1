param(
    [Parameter(Mandatory = $true)]
    [string]$Serial,

    [int]$SettleMs = 1200
)

$ErrorActionPreference = "Stop"

$ProbePackage = "com.jev.probe.guofeng"
$ProbeAction = "com.jev.probe.debug.PROBE_ACCESSIBILITY"
$PrefsPath = "shared_prefs/jev_debug_accessibility_probe.xml"

$Profiles = @(
    [pscustomobject]@{
        Name = "A_CURRENT_STRICT"
        Component = "$ProbePackage/com.jev.probe.debug.ProfileAService"
    },
    [pscustomobject]@{
        Name = "B_HONEST_TOOL_FILTERED"
        Component = "$ProbePackage/com.jev.probe.debug.ProfileBService"
    },
    [pscustomobject]@{
        Name = "C_HONEST_TOOL_OPEN"
        Component = "$ProbePackage/com.jev.probe.debug.ProfileCService"
    },
    [pscustomobject]@{
        Name = "D_LEGACY_IDENTITY_OPEN"
        Component = "$ProbePackage/com.google.android.accessibility.selecttospeak.SelectToSpeakService"
    }
)

function Invoke-Adb {
    & adb -s $Serial @args
    if ($LASTEXITCODE -ne 0) {
        throw "adb failed: $($args -join ' ')"
    }
}

function Get-FocusLine {
    $text = (Invoke-Adb shell dumpsys window) -join "`n"
    return ($text -split "`n" | Where-Object { $_ -match "mCurrentFocus=" } | Select-Object -First 1)
}

function Get-PrefValue {
    param(
        [string]$XmlText,
        [string]$Name,
        [string]$Kind = "int"
    )
    if ($Kind -eq "string") {
        $m = [regex]::Match($XmlText, '<string name="' + [regex]::Escape($Name) + '">([^<]*)</string>')
        return $(if ($m.Success) { $m.Groups[1].Value } else { "" })
    }
    $m = [regex]::Match($XmlText, '<(?:int|long) name="' + [regex]::Escape($Name) + '" value="([^"]+)"')
    return $(if ($m.Success) { $m.Groups[1].Value } else { "" })
}

$deviceState = ((adb -s $Serial get-state 2>$null) -join "").Trim()
if ($LASTEXITCODE -ne 0 -or $deviceState -ne "device") {
    throw "Device is not online: $Serial (state=$deviceState)"
}

$probePath = (Invoke-Adb shell pm path $ProbePackage | Select-Object -First 1)
if (-not $probePath) {
    throw "Probe package is not installed: $ProbePackage"
}

$focus = Get-FocusLine
if (-not $focus.Contains("com.tencent.mm")) {
    throw "Open the target WeChat chat first. Current focus: $focus"
}

$rows = New-Object System.Collections.Generic.List[object]

$originalServices = ((adb -s $Serial shell settings get secure enabled_accessibility_services) -join "").Trim()
$originalEnabled = ((adb -s $Serial shell settings get secure accessibility_enabled) -join "").Trim()
if ($originalEnabled -notmatch "^[01]$") {
    $originalEnabled = "0"
}

try {
foreach ($profile in $Profiles) {
    Write-Host ("Testing " + $profile.Name + " ...")

    Invoke-Adb shell settings delete secure enabled_accessibility_services | Out-Null
    Invoke-Adb shell settings put secure accessibility_enabled 0 | Out-Null
    Start-Sleep -Milliseconds 250

    Invoke-Adb shell settings put secure enabled_accessibility_services $profile.Component | Out-Null
    Invoke-Adb shell settings put secure accessibility_enabled 1 | Out-Null
    Start-Sleep -Milliseconds $SettleMs

    $focus = Get-FocusLine
    if (-not $focus.Contains("com.tencent.mm")) {
        throw "WeChat lost focus during profile $($profile.Name): $focus"
    }

    Invoke-Adb shell am broadcast -a $ProbeAction -p $ProbePackage | Out-Null
    Start-Sleep -Milliseconds 450

    $xml = (Invoke-Adb shell run-as $ProbePackage cat $PrefsPath) -join "`n"

    $row = [pscustomobject]@{
        profile = Get-PrefValue $xml "profile" "string"
        source = Get-PrefValue $xml "source" "string"
        root_package = Get-PrefValue $xml "root_package" "string"
        event_count = Get-PrefValue $xml "event_count"
        last_event_package = Get-PrefValue $xml "last_event_package" "string"
        total_nodes = Get-PrefValue $xml "total_nodes"
        text_nodes = Get-PrefValue $xml "text_nodes"
        desc_nodes = Get-PrefValue $xml "desc_nodes"
        editable_nodes = Get-PrefValue $xml "editable_nodes"
        id_nodes = Get-PrefValue $xml "id_nodes"
        clickable_nodes = Get-PrefValue $xml "clickable_nodes"
        legacy_bkl_nodes = Get-PrefValue $xml "legacy_bkl_nodes"
        max_depth = Get-PrefValue $xml "max_depth"
        root_child_count = Get-PrefValue $xml "root_child_count"
        window_count = Get-PrefValue $xml "window_count"
        wechat_window_count = Get-PrefValue $xml "wechat_window_count"
    }
    $rows.Add($row)
}
}
finally {
    if ([string]::IsNullOrWhiteSpace($originalServices) -or $originalServices -eq "null") {
        Invoke-Adb shell settings delete secure enabled_accessibility_services | Out-Null
    } else {
        Invoke-Adb shell settings put secure enabled_accessibility_services $originalServices | Out-Null
    }
    Invoke-Adb shell settings put secure accessibility_enabled $originalEnabled | Out-Null
    Start-Sleep -Milliseconds 500
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outPath = Join-Path (Get-Location) ("wechat_accessibility_ab_" + $stamp + ".csv")
$rows | Export-Csv -LiteralPath $outPath -NoTypeInformation -Encoding UTF8
$rows | Format-Table -AutoSize
Write-Host ("Saved: " + $outPath)
