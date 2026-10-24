[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$corpusRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$sourceRoot = [System.IO.Path]::GetFullPath((Join-Path $corpusRoot 'source\receipts'))
$outputRoot = [System.IO.Path]::GetFullPath((Join-Path $corpusRoot 'assets\receipts'))

if (-not $sourceRoot.StartsWith($corpusRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Receipt source path escaped the corpus directory.'
}
if (-not $outputRoot.StartsWith($corpusRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Receipt output path escaped the corpus directory.'
}

New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null

$font = [System.Drawing.Font]::new('Segoe UI', 25, [System.Drawing.FontStyle]::Regular)
$headerFont = [System.Drawing.Font]::new('Segoe UI', 25, [System.Drawing.FontStyle]::Bold)
$background = [System.Drawing.Color]::FromArgb(255, 250, 238)
$normalInk = [System.Drawing.Color]::FromArgb(42, 42, 38)
$fadedInk = [System.Drawing.Color]::FromArgb(145, 140, 128)
$random = [System.Random]::new(2401026)

try {
    Get-ChildItem -LiteralPath $sourceRoot -Filter '*.txt' | Sort-Object Name | ForEach-Object {
        $lines = Get-Content -LiteralPath $_.FullName -Encoding UTF8
        $height = [Math]::Max(760, 150 + ($lines.Count * 48))
        $bitmap = [System.Drawing.Bitmap]::new(900, $height)
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        try {
            $graphics.Clear($background)
            $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
            $ink = if ($_.BaseName -eq 'receipt-faded-central') { $fadedInk } else { $normalInk }
            $brush = [System.Drawing.SolidBrush]::new($ink)
            try {
                $y = 58
                for ($index = 0; $index -lt $lines.Count; $index++) {
                    $lineFont = if ($index -eq 0) { $headerFont } else { $font }
                    $graphics.DrawString($lines[$index], $lineFont, $brush, 58, $y)
                    $y += 48
                }

                if ($_.BaseName -eq 'receipt-faded-central') {
                    $noisePen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(25, 90, 82, 70), 1)
                    try {
                        0..180 | ForEach-Object {
                            $x1 = $random.Next(20, 880)
                            $y1 = $random.Next(20, $height - 20)
                            $graphics.DrawLine($noisePen, $x1, $y1, $x1 + $random.Next(2, 24), $y1)
                        }
                    }
                    finally {
                        $noisePen.Dispose()
                    }
                }
            }
            finally {
                $brush.Dispose()
            }

            $outputPath = Join-Path $outputRoot ($_.BaseName + '.png')
            $bitmap.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
            Write-Output $outputPath
        }
        finally {
            $graphics.Dispose()
            $bitmap.Dispose()
        }
    }
}
finally {
    $font.Dispose()
    $headerFont.Dispose()
}
