<#
Generates the image fixtures in metadata/src/test/resources/fixtures/ for the :metadata unit
tests. The output is committed, so tests don't need these tools installed.

Requires ImageMagick 7 (`magick`, with HEIC/AVIF support) and exiftool on PATH.
All metadata is fake: no fixture may contain personal data.

Not generated yet: APNG (ImageMagick's APNG writer needs ffmpeg) and ICC-profile fixtures
(system ICC files aren't ours to redistribute). Both come with the PNG milestone.
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

$xmp = @(
    '-XMP-dc:Creator=Jane Fixture',
    '-XMP-xmp:CreatorTool=Fixture Editor 2.0',
    '-XMP-photoshop:City=Paris'
)

# endregion Helpers

try {
    # region JPEG

    $thumbnail = Join-Path $work 'thumbnail.jpg'
    magick -size 32x24 xc:'#20a020' -strip $thumbnail

    New-BaseImage (Fixture 'jpeg-clean.jpg')

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

    # endregion GIF

    # region HEIF

    $heic = Fixture 'heif-metadata.heic'
    New-BaseImage $heic @('-quality', '60')
    Set-Metadata $heic ($cameraExif + $xmp)

    $avif = Fixture 'avif-metadata.avif'
    New-BaseImage $avif @('-quality', '60')
    Set-Metadata $avif ($cameraExif + $xmp)

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
