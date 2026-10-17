<#
Generates the image fixtures in metadata/src/test/resources/fixtures/ for the :metadata unit
tests. The output is committed, so tests don't need these tools installed.

Requires ImageMagick 7 (`magick`, with HEIC/AVIF support), libheif's `heif-enc` and exiftool
on PATH.
All metadata is fake: no fixture may contain personal data.

Two things are built by hand instead: the APNG (ImageMagick's APNG writer needs ffmpeg) and
the ICC profile (system profiles aren't ours to redistribute).
#>

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true

$fixtures = Join-Path $PSScriptRoot '../metadata/src/test/resources/fixtures'
New-Item -ItemType Directory -Force -Path $fixtures | Out-Null
$fixtures = (Resolve-Path $fixtures).Path

$work = Join-Path ([System.IO.Path]::GetTempPath()) "mw-fixtures-$PID"
New-Item -ItemType Directory -Force -Path $work | Out-Null

# region Helpers

function Fixture([string] $name) {
    return Join-Path $fixtures $name
}

<#
64×48 so a rotation or a width/height swap shows up in dimension checks. -strip and the
excluded PNG chunks keep ImageMagick from adding its own dates, so re-runs are byte-stable.
#>
function New-BaseImage([string] $path,
                       [string[]] $extraArgs = @()) {
    magick -size 64x48 gradient:'#d04040-#4040d0' `
           -fill white -draw 'rectangle 8,8 24,40' `
           -strip -define png:exclude-chunks=date,time `
           @extraArgs $path
}

function Set-Metadata([string] $path,
                      [string[]] $tags) {
    exiftool -q -overwrite_original -m @tags $path
}

$cameraExif = @(
    '-EXIF:Make=Fixture Camera Co',
    '-EXIF:Model=FX-100',
    '-EXIF:Software=Fixture Firmware 1.0',
    '-EXIF:Artist=Jane Fixture',
    '-EXIF:Copyright=Jane Fixture',
    '-EXIF:DateTimeOriginal=2026:09:12 14:30:00',
    '-EXIF:CreateDate=2026:09:12 14:30:00',
    '-EXIF:ModifyDate=2026:09:12 14:30:00',
    '-EXIF:OwnerName=Jane Fixture',
    '-EXIF:SerialNumber=SN-0042-FIXTURE',
    '-EXIF:LensSerialNumber=LENS-0042',
    '-EXIF:GPSLatitude=48.858217',
    '-EXIF:GPSLatitudeRef=N',
    '-EXIF:GPSLongitude=2.2945',
    '-EXIF:GPSLongitudeRef=E',
    '-EXIF:GPSAltitude=35',
    '-EXIF:GPSAltitudeRef=0'
)

<#
Big-endian integers, as PNG and ICC store them. BitConverter is little-endian on every
platform .NET runs on, so the low bytes are taken and reversed.
#>
function ConvertTo-BigEndian([uint64] $value,
                             [int] $size = 4) {
    [byte[]] $bytes = [System.BitConverter]::GetBytes($value)[0..($size - 1)]
    [array]::Reverse($bytes)
    return , $bytes
}

function Join-Bytes([object[]] $parts) {
    $stream = [System.IO.MemoryStream]::new()
    foreach ($part in $parts) {
        $stream.Write([byte[]] $part, 0, $part.Length)
    }
    return , $stream.ToArray()
}

function Get-Ascii([string] $text) {
    return , [System.Text.Encoding]::ASCII.GetBytes($text)
}

# .NET only ships CRC-32 in the System.IO.Hashing package, so this is the bitwise version from the PNG spec.
function Get-Crc32([byte[]] $bytes) {
    [uint32] $crc = 0xFFFFFFFFu
    foreach ($byte in $bytes) {
        $crc = $crc -bxor $byte
        for ($bit = 0; $bit -lt 8; $bit++) {
            if (($crc -band 1) -eq 1) {
                $crc = ($crc -shr 1) -bxor 0xEDB88320u
            } else {
                $crc = $crc -shr 1
            }
        }
    }
    return [uint32] ($crc -bxor 0xFFFFFFFFu)
}

function New-PngChunk([string] $type,
                      [byte[]] $data) {
    $typeAndData = Join-Bytes @((Get-Ascii $type), $data)
    return Join-Bytes @((ConvertTo-BigEndian $data.Length), $typeAndData, (ConvertTo-BigEndian (Get-Crc32 $typeAndData)))
}

function Get-PngChunkData([string] $path,
                          [string] $type) {
    $bytes = [System.IO.File]::ReadAllBytes($path)
    $found = [System.Collections.Generic.List[byte[]]]::new()
    $offset = 8
    while ($offset -lt $bytes.Length) {
        $length = [int] (([uint32] $bytes[$offset] -shl 24) -bor ([uint32] $bytes[$offset + 1] -shl 16) -bor
                         ([uint32] $bytes[$offset + 2] -shl 8) -bor [uint32] $bytes[$offset + 3])
        if ([System.Text.Encoding]::ASCII.GetString($bytes, $offset + 4, 4) -eq $type) {
            $found.Add($bytes[($offset + 8)..($offset + 7 + $length)])
        }
        $offset += 12 + $length
    }
    return , $found
}

<#
A minimal ICC v2 display profile: sRGB primaries, D50 white point and a plain 2.2 gamma.
Close enough to render sensibly, and ours to commit. Layout is a 128-byte header, the tag
table, then each tag's data padded to 4 bytes.
#>
function New-IccProfile {
    function ConvertTo-S15Fixed16([double] $value) {
        return ConvertTo-BigEndian ([uint32] [math]::Round($value * 65536))
    }

    function New-XyzTag([double] $x,
                        [double] $y,
                        [double] $z) {
        return Join-Bytes @((Get-Ascii 'XYZ '), [byte[]]::new(4),
                            (ConvertTo-S15Fixed16 $x), (ConvertTo-S15Fixed16 $y), (ConvertTo-S15Fixed16 $z))
    }

    # textDescriptionType: ASCII, then empty Unicode and ScriptCode descriptions.
    $name = Get-Ascii "Fixture RGB`0"
    $description = Join-Bytes @((Get-Ascii 'desc'), [byte[]]::new(4), (ConvertTo-BigEndian $name.Length), $name,
                                [byte[]]::new(4 + 4 + 2 + 1 + 67))
    $copyright = Join-Bytes @((Get-Ascii 'text'), [byte[]]::new(4), (Get-Ascii "No copyright, use freely`0"))
    $gamma = Join-Bytes @((Get-Ascii 'curv'), [byte[]]::new(4), (ConvertTo-BigEndian 1), (ConvertTo-BigEndian 0x0233 2))

    $tags = [ordered] @{
        desc = $description
        cprt = $copyright
        wtpt = New-XyzTag 0.9642 1.0 0.8249
        rXYZ = New-XyzTag 0.4361 0.2225 0.0139
        gXYZ = New-XyzTag 0.3851 0.7169 0.0971
        bXYZ = New-XyzTag 0.1431 0.0606 0.7141
        rTRC = $gamma
        gTRC = $gamma
        bTRC = $gamma
    }

    $offset = 128 + 4 + 12 * $tags.Count
    $table = [System.Collections.Generic.List[byte[]]]::new()
    $data = [System.Collections.Generic.List[byte[]]]::new()
    foreach ($signature in $tags.Keys) {
        $bytes = $tags[$signature]
        $padding = (4 - $bytes.Length % 4) % 4
        $table.Add((Join-Bytes @((Get-Ascii $signature), (ConvertTo-BigEndian $offset), (ConvertTo-BigEndian $bytes.Length))))
        $data.Add((Join-Bytes @($bytes, [byte[]]::new($padding))))
        $offset += $bytes.Length + $padding
    }

    $header = Join-Bytes @((ConvertTo-BigEndian $offset), [byte[]]::new(4), (ConvertTo-BigEndian 0x02100000),
                           (Get-Ascii 'mntrRGB XYZ '),
                           (ConvertTo-BigEndian 2026 2), (ConvertTo-BigEndian 9 2), (ConvertTo-BigEndian 12 2),
                           (ConvertTo-BigEndian 14 2), (ConvertTo-BigEndian 30 2), (ConvertTo-BigEndian 0 2),
                           (Get-Ascii 'acsp'), [byte[]]::new(28),
                           (ConvertTo-S15Fixed16 0.9642), (ConvertTo-S15Fixed16 1.0), (ConvertTo-S15Fixed16 0.8249),
                           [byte[]]::new(48))
    return Join-Bytes (@($header, (ConvertTo-BigEndian $tags.Count)) + $table + $data)
}

# fcTL for a full-canvas 64×48 frame shown for 1/5 s.
function New-ApngFrameControl([int] $sequence) {
    return Join-Bytes @((ConvertTo-BigEndian $sequence), (ConvertTo-BigEndian 64), (ConvertTo-BigEndian 48),
                        [byte[]]::new(8), (ConvertTo-BigEndian 1 2), (ConvertTo-BigEndian 5 2), [byte[]] (0, 0))
}

$xmp = @(
    '-XMP-dc:Creator=Jane Fixture',
    '-XMP-xmp:CreatorTool=Fixture Editor 2.0',
    '-XMP-photoshop:City=Paris'
)

# endregion Helpers

try {
    $icc = Join-Path $work 'fixture.icc'
    [System.IO.File]::WriteAllBytes($icc, (New-IccProfile))

    # region JPEG

    $thumbnail = Join-Path $work 'thumbnail.jpg'
    magick -size 32x24 xc:'#20a020' -strip $thumbnail

    New-BaseImage (Fixture 'jpeg-clean.jpg')

    New-BaseImage (Fixture 'jpeg-icc.jpg') @('-profile', $icc)

    $camera = Fixture 'jpeg-camera.jpg'
    New-BaseImage $camera
    Set-Metadata $camera ($cameraExif + $xmp + @(
        '-EXIF:Orientation#=6',
        "-ThumbnailImage<=$thumbnail",
        '-IPTC:By-line=Jane Fixture',
        '-IPTC:City=Paris',
        '-Comment=Fixture JPEG comment'
    ))

    $progressive = Fixture 'jpeg-progressive.jpg'
    New-BaseImage $progressive @('-interlace', 'Plane')
    Set-Metadata $progressive ($cameraExif + '-EXIF:Orientation#=1')

    # CMYK JPEGs carry an Adobe APP14 segment that must survive stripping.
    $cmyk = Fixture 'jpeg-cmyk.jpg'
    New-BaseImage $cmyk @('-colorspace', 'CMYK')
    Set-Metadata $cmyk $cameraExif

    <#
    Motion Photos append an MP4 after the JPEG's EOI marker. A minimal `ftyp` box plus some
    payload is enough to test that trailing data is detected and dropped.
    #>
    $motion = Fixture 'jpeg-motion-photo.jpg'
    New-BaseImage $motion
    Set-Metadata $motion ($cameraExif + '-XMP-GCamera:MicroVideo=1')
    # Adding arrays in PowerShell yields object[], so each concatenation is cast back to byte[].
    [byte[]] $trailer = [byte[]] (0x00, 0x00, 0x00, 0x18) +
                        [System.Text.Encoding]::ASCII.GetBytes('ftypmp42') +
                        [byte[]] (0x00, 0x00, 0x00, 0x00) +
                        [System.Text.Encoding]::ASCII.GetBytes('isommp42') +
                        [System.Text.Encoding]::ASCII.GetBytes('fake mp4 payload for tests')
    $stream = [System.IO.File]::Open($motion, [System.IO.FileMode]::Append)
    try {
        $stream.Write($trailer, 0, $trailer.Length)
    } finally {
        $stream.Dispose()
    }

    # endregion JPEG

    # region PNG

    New-BaseImage (Fixture 'png-clean.png')

    # exiftool writes EXIF as an eXIf chunk, XMP as iTXt, Comment as tEXt and ModifyDate as tIME.
    $png = Fixture 'png-metadata.png'
    New-BaseImage $png
    Set-Metadata $png ($cameraExif + $xmp + @(
        '-EXIF:Orientation#=6',
        '-PNG:Comment=Fixture PNG comment',
        '-PNG:Author=Jane Fixture',
        '-PNG:ModifyDate=2026:09:12 14:30:00'
    ))

    # ImageMagick's PNG writer drops a -profile it didn't convert with, so exiftool adds the iCCP chunk.
    $pngIcc = Fixture 'png-icc.png'
    New-BaseImage $pngIcc
    Set-Metadata $pngIcc @("-ICC_Profile<=$icc")

    <#
    Two solid frames, the first doubling as the default image. Their IDAT data comes from
    ImageMagick; the APNG chunks around it are written here, with a tEXt and tIME to strip.
    #>
    $frames = @('#d04040', '#40d040') | ForEach-Object -Begin { $index = 0 } -Process {
        $path = Join-Path $work "frame$index.png"
        magick -size 64x48 xc:$_ -strip -define png:exclude-chunks=date,time -depth 8 PNG24:$path
        $index++
        $path
    }
    $imageHeader = (Get-PngChunkData $frames[0] 'IHDR')[0]
    $frameData = $frames | ForEach-Object { , (Join-Bytes (Get-PngChunkData $_ 'IDAT')) }
    $apng = Join-Bytes @([byte[]] (0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
                         (New-PngChunk 'IHDR' $imageHeader),
                         (New-PngChunk 'acTL' (Join-Bytes @((ConvertTo-BigEndian 2), (ConvertTo-BigEndian 0)))),
                         (New-PngChunk 'tEXt' (Get-Ascii "Comment`0Fixture APNG comment")),
                         (New-PngChunk 'tIME' (Join-Bytes @((ConvertTo-BigEndian 2026 2), [byte[]] (9, 12, 14, 30, 0)))),
                         (New-PngChunk 'fcTL' (New-ApngFrameControl 0)),
                         (New-PngChunk 'IDAT' $frameData[0]),
                         (New-PngChunk 'fcTL' (New-ApngFrameControl 1)),
                         (New-PngChunk 'fdAT' (Join-Bytes @((ConvertTo-BigEndian 2), $frameData[1]))),
                         (New-PngChunk 'IEND' ([byte[]]::new(0))))
    [System.IO.File]::WriteAllBytes((Fixture 'png-animated.png'), $apng)

    # endregion PNG

    # region WebP

    # A simple lossy VP8 file can't hold metadata; exiftool converts it to extended VP8X.
    New-BaseImage (Fixture 'webp-simple.webp') @('-quality', '80')

    $webP = Fixture 'webp-metadata.webp'
    New-BaseImage $webP @('-quality', '80')
    Set-Metadata $webP ($cameraExif + $xmp + '-EXIF:Orientation#=6')

    $webPLossless = Fixture 'webp-lossless-metadata.webp'
    New-BaseImage $webPLossless @('-define', 'webp:lossless=true')
    Set-Metadata $webPLossless ($cameraExif + $xmp)

    New-BaseImage (Fixture 'webp-icc.webp') @('-quality', '80', '-profile', $icc)

    # endregion WebP

    # region GIF

    $gif = Fixture 'gif-metadata.gif'
    New-BaseImage $gif
    Set-Metadata $gif ($xmp + '-Comment=Fixture GIF comment')

    $animatedGif = Fixture 'gif-animated.gif'
    magick -size 64x48 -delay 20 -loop 0 `
           xc:'#d04040' xc:'#40d040' xc:'#4040d0' `
           -strip $animatedGif
    Set-Metadata $animatedGif @('-Comment=Fixture animated GIF comment')

    # ImageMagick stores the profile as an ICCRGBG1 application extension.
    New-BaseImage (Fixture 'gif-icc.gif') @('-profile', $icc)

    # endregion GIF

    # region HEIF

    # 8-bit, so it's HEVC Main as phones write it; ImageMagick's default 12-bit Range Extensions doesn't decode on Android.
    $heic = Fixture 'heif-metadata.heic'
    New-BaseImage $heic @('-depth', '8', '-quality', '60')
    Set-Metadata $heic ($cameraExif + $xmp)

    $avif = Fixture 'avif-metadata.avif'
    New-BaseImage $avif @('-quality', '60')
    Set-Metadata $avif ($cameraExif + $xmp)

    <#
    ImageMagick writes neither a thumbnail nor an ICC profile into HEIF, so this one comes from
    libheif's heif-enc: a `thmb` thumbnail item, the PNG's ICC profile as a `colr` property,
    and a `udes` user description, plus the same EXIF and XMP as the others.
    #>
    $heicThumbnail = Fixture 'heif-thumbnail-icc.heic'
    heif-enc -q 60 -t 32 --pitm-description 'Jane Fixture in Paris' -o $heicThumbnail $pngIcc
    Set-Metadata $heicThumbnail ($cameraExif + $xmp)

    # endregion HEIF

    # region BMP

    New-BaseImage (Fixture 'bmp-plain.bmp')

    <#
    A BITMAPV5HEADER with bV5CSType = PROFILE_LINKED stores a path to an ICC file, which can
    leak a username. ImageMagick can't write one, so it's built by hand: 2×2, 24-bit, with
    the null-terminated path stored after the pixel rows.
    #>
    [byte[]] $linkedPath = [System.Text.Encoding]::ASCII.GetBytes('C:\Users\JaneFixture\Profiles\fixture.icc') + [byte] 0
    $fileHeaderSize = 14
    $infoHeaderSize = 124
    $rowSize = 8 # 2 pixels × 3 bytes, padded to a multiple of 4
    $pixels = [byte[]] (0x40, 0x40, 0xD0, 0xD0, 0x40, 0x40, 0x00, 0x00,
                        0x40, 0xD0, 0x40, 0xFF, 0xFF, 0xFF, 0x00, 0x00)
    $pixelOffset = $fileHeaderSize + $infoHeaderSize
    $profileOffset = $infoHeaderSize + $pixels.Length # bV5ProfileData is relative to the info header
    $fileSize = $pixelOffset + $pixels.Length + $linkedPath.Length

    $memory = [System.IO.MemoryStream]::new()
    $writer = [System.IO.BinaryWriter]::new($memory)
    try {
        $writer.Write([System.Text.Encoding]::ASCII.GetBytes('BM'))
        $writer.Write([uint32] $fileSize)
        $writer.Write([uint32] 0)
        $writer.Write([uint32] $pixelOffset)

        $writer.Write([uint32] $infoHeaderSize)
        $writer.Write([int32] 2)               # width
        $writer.Write([int32] 2)               # height, bottom-up
        $writer.Write([uint16] 1)              # planes
        $writer.Write([uint16] 24)             # bits per pixel
        $writer.Write([uint32] 0)              # BI_RGB
        $writer.Write([uint32] ($rowSize * 2))
        $writer.Write([int32] 2835)            # 72 DPI
        $writer.Write([int32] 2835)
        $writer.Write([uint32] 0)              # colors used
        $writer.Write([uint32] 0)              # important colors
        $writer.Write([byte[]]::new(16))       # RGBA masks, unused for BI_RGB
        $writer.Write([uint32] 0x4C494E4B)     # bV5CSType = 'LINK' (PROFILE_LINKED)
        $writer.Write([byte[]]::new(36))       # endpoints
        $writer.Write([byte[]]::new(12))       # gamma
        $writer.Write([uint32] 4)              # LCS_GM_IMAGES
        $writer.Write([uint32] $profileOffset)
        $writer.Write([uint32] $linkedPath.Length)
        $writer.Write([uint32] 0)              # reserved

        $writer.Write($pixels)
        $writer.Write($linkedPath)
        $writer.Flush()
        [System.IO.File]::WriteAllBytes((Fixture 'bmp-linked-profile.bmp'), $memory.ToArray())
    } finally {
        $writer.Dispose()
    }

    # endregion BMP
} finally {
    Remove-Item -Recurse -Force $work
}

Get-ChildItem $fixtures | Sort-Object Name | Format-Table Name, Length -AutoSize
