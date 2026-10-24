[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$EdgeTtsCommand,

    [Parameter(Mandatory = $true)]
    [string]$FfmpegPath
)

$ErrorActionPreference = 'Stop'
$corpusRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$sourceRoot = [System.IO.Path]::GetFullPath((Join-Path $corpusRoot 'assets\speech-transcripts'))
$outputRoot = [System.IO.Path]::GetFullPath((Join-Path $corpusRoot 'assets\speech'))

if (-not $sourceRoot.StartsWith($corpusRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Speech source path escaped the corpus directory.'
}
if (-not $outputRoot.StartsWith($corpusRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Speech output path escaped the corpus directory.'
}
if (-not (Test-Path -LiteralPath $EdgeTtsCommand -PathType Leaf)) {
    throw 'Edge TTS command was not found.'
}
if (-not (Test-Path -LiteralPath $FfmpegPath -PathType Leaf)) {
    throw 'FFmpeg executable was not found.'
}

New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null
$temporaryRoot = [System.IO.Path]::Combine(
    [System.IO.Path]::GetTempPath(),
    'wisekoin-synthetic-speech-' + [guid]::NewGuid().ToString('N')
)
New-Item -ItemType Directory -Path $temporaryRoot | Out-Null

$cases = @(
    @{ Name = 'speech-north-colloquial'; Voice = 'vi-VN-NamMinhNeural'; Rate = '+0%' },
    @{ Name = 'speech-central-colloquial'; Voice = 'vi-VN-HoaiMyNeural'; Rate = '-5%' },
    @{ Name = 'speech-south-colloquial'; Voice = 'vi-VN-HoaiMyNeural'; Rate = '+8%' },
    @{ Name = 'speech-income'; Voice = 'vi-VN-NamMinhNeural'; Rate = '-2%' },
    @{ Name = 'speech-transfer'; Voice = 'vi-VN-HoaiMyNeural'; Rate = '+3%' },
    @{ Name = 'speech-relative-date'; Voice = 'vi-VN-NamMinhNeural'; Rate = '-8%' },
    @{ Name = 'speech-batch'; Voice = 'vi-VN-HoaiMyNeural'; Rate = '+10%' },
    @{ Name = 'speech-noisy-correction'; Voice = 'vi-VN-NamMinhNeural'; Rate = '+0%' }
)

try {
    foreach ($case in $cases) {
        $transcriptPath = Join-Path $sourceRoot ($case.Name + '.txt')
        $transcript = (Get-Content -Raw -LiteralPath $transcriptPath -Encoding UTF8).Trim()
        $temporaryMp3 = Join-Path $temporaryRoot ($case.Name + '.mp3')
        $outputM4a = Join-Path $outputRoot ($case.Name + '.m4a')

        & $EdgeTtsCommand `
            --voice $case.Voice `
            --rate $case.Rate `
            --text $transcript `
            --write-media $temporaryMp3
        if ($LASTEXITCODE -ne 0) {
            throw "Synthetic speech generation failed for $($case.Name)."
        }

        if ($case.Name -eq 'speech-noisy-correction') {
            & $FfmpegPath -hide_banner -loglevel error -y `
                -i $temporaryMp3 `
                -filter_complex "anoisesrc=d=60:c=pink:r=24000:a=0.04[n];[0:a][n]amix=inputs=2:duration=first:weights='1 0.12'" `
                -c:a aac -b:a 96k -movflags +faststart $outputM4a
        }
        else {
            & $FfmpegPath -hide_banner -loglevel error -y `
                -i $temporaryMp3 -c:a aac -b:a 96k -movflags +faststart $outputM4a
        }
        if ($LASTEXITCODE -ne 0) {
            throw "M4A encoding failed for $($case.Name)."
        }
        Write-Output $outputM4a
    }
}
finally {
    $resolvedTemporaryRoot = [System.IO.Path]::GetFullPath($temporaryRoot)
    $systemTemporaryRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    if ($resolvedTemporaryRoot.StartsWith($systemTemporaryRoot, [System.StringComparison]::OrdinalIgnoreCase) -and
        ([System.IO.Path]::GetFileName($resolvedTemporaryRoot)).StartsWith('wisekoin-synthetic-speech-')) {
        Remove-Item -LiteralPath $resolvedTemporaryRoot -Recurse -Force
    }
}
