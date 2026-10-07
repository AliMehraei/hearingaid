<#
.SYNOPSIS
  Builds the signed release files into dist\:
    hearingaid-<version>.apk       GitHub build (updates itself from GitHub releases)
    hearingaid-<version>-play.aab  Google Play bundle
    SHA256SUMS.txt                 checksum of the APK; the in-app updater refuses an APK not listed here

.DESCRIPTION
  Needs keystore.properties in the project root (git-ignored) pointing at the release key:
    storeFile=C:/Users/<you>/.hearingaid/hearingaid-release.jks
    storePassword=...
    keyAlias=hearingaid
    keyPassword=...
  Every update must be signed with the same key, or Android refuses to install it.

.EXAMPLE
  pwsh .\build-release.ps1
  gh release create v1.0.0 dist\hearingaid-1.0.0.apk dist\SHA256SUMS.txt --title "Hearing Aid 1.0.0" --notes-file notes.md
#>
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not (Test-Path keystore.properties)) {
    throw 'keystore.properties is missing, so the release cannot be signed. See the comment at the top of this script.'
}

$gradle = Get-Content app\build.gradle.kts -Raw
$version = [regex]::Match($gradle, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
if (-not $version) { throw 'versionName not found in app\build.gradle.kts' }
Write-Host "Building Hearing Aid $version" -ForegroundColor Cyan

& .\gradlew.bat --console=plain testGithubReleaseUnitTest assembleGithubRelease bundlePlayRelease
if ($LASTEXITCODE -ne 0) { throw "Gradle failed ($LASTEXITCODE)" }

New-Item -ItemType Directory -Force dist | Out-Null
Get-ChildItem dist -File | Remove-Item

$apk = "hearingaid-$version.apk"
$aab = "hearingaid-$version-play.aab"
Copy-Item app\build\outputs\apk\github\release\app-github-release.apk "dist\$apk"
Copy-Item app\build\outputs\bundle\playRelease\app-play-release.aab "dist\$aab"

# sha256sum format: "<hash>  <file name>". Only the APK goes on GitHub; the .aab is for Play.
$hash = (Get-FileHash "dist\$apk" -Algorithm SHA256).Hash.ToLowerInvariant()
Set-Content dist\SHA256SUMS.txt -Value "$hash  $apk" -Encoding ascii

Get-ChildItem dist | Format-Table Name, Length
Write-Host "Done. Upload dist\$apk and dist\SHA256SUMS.txt to a GitHub release tagged v$version." -ForegroundColor Green
