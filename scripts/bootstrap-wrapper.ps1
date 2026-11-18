<#
.SYNOPSIS
取得固定 Liftoff 模板中的 Gradle Wrapper，并在写入工程前核对官方校验和。
.DESCRIPTION
仅用于首次初始化或修复 Wrapper；普通开发直接使用 gradlew.bat。
所有写入限制在本仓库；保持 HTTPS 证书校验，不执行下载内容。
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$projectRoot = $repositoryRoot
$stagingRoot = Join-Path $repositoryRoot '.cache/wrapper-bootstrap'
$templateBase = 'https://raw.githubusercontent.com/libgdx/gdx-liftoff/v1.14.2.3/src/main/resources/generator'
$expectedWrapperHash = '238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5'
$expectedDistributionHash = 'bafd5ce9cfaea0fbccfdc8439a1ac42fbd4cd9c89dc9a988228d8a2639a58e6c'

New-Item -ItemType Directory -Path $stagingRoot -Force | Out-Null
$templateFiles = @('gradlew', 'gradlew.bat', 'gradle/wrapper/gradle-wrapper.jar')
foreach ($relativePath in $templateFiles) {
    $stagedFile = Join-Path $stagingRoot $relativePath
    New-Item -ItemType Directory -Path (Split-Path -Parent $stagedFile) -Force | Out-Null
    Invoke-WebRequest -Uri "$templateBase/$relativePath" -OutFile $stagedFile
}

# 本地固定值与官方远程值都必须匹配，防止误把下载成功当作取得正确文件。
foreach ($artifact in @(
    @{ Name = 'wrapper.jar'; Hash = $expectedWrapperHash },
    @{ Name = 'bin.zip'; Hash = $expectedDistributionHash }
)) {
    $checksumFile = Join-Path $stagingRoot ($artifact.Name + '.sha256')
    Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-9.8.0-$($artifact.Name).sha256" -OutFile $checksumFile
    if ((Get-Content -LiteralPath $checksumFile -Raw).Trim() -ne $artifact.Hash) {
        throw "Official checksum differs for $($artifact.Name); no Wrapper files installed."
    }
}
$actualWrapperHash = (Get-FileHash -LiteralPath (Join-Path $stagingRoot 'gradle/wrapper/gradle-wrapper.jar') -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualWrapperHash -ne $expectedWrapperHash) {
    throw "Wrapper JAR checksum mismatch; no Wrapper files installed. Actual: $actualWrapperHash"
}
foreach ($relativePath in $templateFiles) {
    $destination = Join-Path $projectRoot $relativePath
    New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $stagingRoot $relativePath) -Destination $destination -Force
}
Write-Output "Verified Gradle 9.8.0 Wrapper installed: $actualWrapperHash"
