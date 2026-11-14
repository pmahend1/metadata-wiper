<#
Generates the video fixtures in metadata/src/test/resources/fixtures/ for the :metadata unit
tests. The output is committed, so tests don't need these tools installed.

Requires ffmpeg 7 or later (with libx264, libx265, libvpx-vp9 and libopus) and exiftool on PATH. Kept apart from
generate-fixtures.ps1 so regenerating videos never rewrites the image fixtures, whose bytes
depend on the ImageMagick and libheif versions.
All metadata is fake: no fixture may contain personal data.

Layouts ffmpeg won't write on request (telemetry and timecode tracks, co64, stz2, trailers) are
built in the tests by MovieBuilder instead.
#>

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true

$fixtures = Join-Path $PSScriptRoot '../metadata/src/test/resources/fixtures'
New-Item -ItemType Directory -Force -Path $fixtures | Out-Null
$fixtures = (Resolve-Path $fixtures).Path

$work = Join-Path ([System.IO.Path]::GetTempPath()) "mw-video-fixtures-$PID"
New-Item -ItemType Directory -Force -Path $work | Out-Null

# region Helpers

function Fixture([string] $name) {
    return Join-Path $fixtures $name
}

<#
One second of 64×48 video at 4 fps plus a sine tone, so each file is a few KB. 64×48 makes a
rotation or a width/height swap show up in dimension checks.
#>
$source = @('-f', 'lavfi', '-i', 'testsrc2=size=64x48:rate=4:duration=1',
            '-f', 'lavfi', '-i', 'sine=frequency=440:sample_rate=8000:duration=1')
$h264Aac = @('-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '40', '-pix_fmt', 'yuv420p',
             '-c:a', 'aac', '-b:a', '16k')

# Without ffmpeg's own version tags, so every tag in the ISO BMFF fixtures is one exiftool wrote.
$bitExact = @('-fflags', '+bitexact', '-flags:v', '+bitexact', '-flags:a', '+bitexact', '-map_metadata', '-1')

function New-Video([string] $path,
                   [string[]] $extraArgs) {
    ffmpeg -hide_banner -loglevel error -y @source @extraArgs $path
}

function Set-Metadata([string] $path,
                      [string[]] $tags) {
    exiftool -q -overwrite_original -m @tags $path
}

# mvhd, tkhd and mdhd creation and modification times, in UTC as QuickTime stores them.
$headerDates = @(
    '-QuickTime:CreateDate=2026:09:12 12:30:00',
    '-QuickTime:ModifyDate=2026:09:12 12:30:00',
    '-QuickTime:TrackCreateDate=2026:09:12 12:30:00',
    '-QuickTime:TrackModifyDate=2026:09:12 12:30:00',
    '-QuickTime:MediaCreateDate=2026:09:12 12:30:00',
    '-QuickTime:MediaModifyDate=2026:09:12 12:30:00'
)

# endregion Helpers

try {
    $cover = Join-Path $work 'cover.jpg'
    ffmpeg -hide_banner -loglevel error -y -f lavfi -i 'color=c=green:size=32x24' -frames:v 1 $cover

    # Matroska picks fonts out by MIME type; the bytes are never parsed, so they needn't be a real font.
    $font = Join-Path $work 'fixture.ttf'
    [System.IO.File]::WriteAllText($font, 'Not a real font, fixture bytes only')

    # region ISO BMFF

    # Android writes location and device into moov/udta (©xyz, ©mak, ©mod).
    $android = Fixture 'mp4-android.mp4'
    New-Video $android ($h264Aac + $bitExact)
    Set-Metadata $android ($headerDates + @(
        '-UserData:GPSCoordinates=48.858217, 2.2945, 35',
        '-UserData:Make=Fixture Camera Co',
        '-UserData:Model=FX-100',
        '-UserData:DateTimeOriginal=2026:09:12 14:30:00',
        '-XMP-dc:Creator=Jane Fixture',
        '-XMP-xmp:CreatorTool=Fixture Editor 2.0'
    ))

    # iPhones use moov/meta with keys + ilst instead, including the Live Photo pairing identifier.
    $apple = Fixture 'mov-apple.mov'
    New-Video $apple ($h264Aac + $bitExact + @('-f', 'mov'))
    Set-Metadata $apple ($headerDates + @(
        '-Keys:GPSCoordinates=48.858217, 2.2945, 35',
        '-Keys:Make=Fixture Camera Co',
        '-Keys:Model=FX-100',
        '-Keys:Software=Fixture Firmware 1.0',
        '-Keys:CreationDate=2026:09:12 14:30:00+02:00',
        '-Keys:ContentIdentifier=FIXTURE-0042-UUID'
    ))

    # 3GPP's own udta boxes: loci (location), auth, titl.
    $threeGpp = Fixture '3gp-metadata.3gp'
    New-Video $threeGpp ($h264Aac + $bitExact + @('-f', '3gp'))
    Set-Metadata $threeGpp ($headerDates + @(
        '-UserData:LocationInformation=Paris Role=shooting Lat=48.858217 Lon=2.2945 Alt=35',
        '-UserData:Author=Jane Fixture',
        '-UserData:Title=Fixture clip'
    ))

    # The ipod muxer writes the M4V brand; iTunes-style ilst carries the cover art.
    $m4v = Fixture 'm4v-cover.m4v'
    New-Video $m4v ($h264Aac + $bitExact + @('-f', 'ipod'))
    Set-Metadata $m4v @(
        "-ItemList:CoverArt<=$cover",
        '-ItemList:Artist=Jane Fixture',
        '-ItemList:Title=Fixture clip'
    )

    # The tkhd matrix carries the rotation, which a strip must leave alone.
    $rotated = Fixture 'mp4-rotated.mp4'
    New-Video $rotated ($h264Aac + $bitExact)
    Set-Metadata $rotated ($headerDates + @(
        '-Rotation=90',
        '-UserData:Make=Fixture Camera Co'
    ))

    # exiftool can't write fragmented files, so ffmpeg writes the tags (location as a 3GPP loci box).
    New-Video (Fixture 'mp4-fragmented.mp4') ($h264Aac + @(
        '-movflags', 'frag_keyframe+empty_moov',
        '-metadata', 'location=+48.8582+002.2945/',
        '-metadata', 'creation_time=2026-09-12T12:30:00Z',
        '-metadata', 'title=Fixture clip'
    ))

    # endregion ISO BMFF

    # region Matroska

    # ffmpeg writes DateUTC from creation_time and CRC-32 elements in every level-1 element.
    New-Video (Fixture 'mkv-tags.mkv') ($h264Aac + @(
        '-attach', $cover, '-metadata:s:t:0', 'mimetype=image/jpeg', '-metadata:s:t:0', 'filename=cover.jpg',
        '-attach', $font, '-metadata:s:t:1', 'mimetype=application/x-truetype-font', '-metadata:s:t:1', 'filename=fixture.ttf',
        '-metadata', 'title=Fixture clip',
        '-metadata', 'creation_time=2026-09-12T12:30:00Z',
        '-metadata', 'DATE_RECORDED=2026-09-12',
        '-metadata', 'LOCATION=+48.8582+002.2945/',
        '-metadata', 'ARTIST=Jane Fixture',
        '-metadata:s:v:0', 'title=Jane Fixture camera'
    ))

    # As a browser's MediaRecorder writes it: VP9 + Opus, MuxingApp/WritingApp, Tags.
    New-Video (Fixture 'webm-browser.webm') @(
        '-c:v', 'libvpx-vp9', '-deadline', 'realtime', '-b:v', '50k',
        '-c:a', 'libopus', '-b:a', '16k', '-ar', '48000',
        '-metadata', 'title=Fixture clip',
        '-metadata', 'DATE_RECORDED=2026-09-12',
        '-metadata', 'LOCATION=+48.8582+002.2945/',
        '-metadata:s:v:0', 'title=Jane Fixture camera'
    )

    # endregion Matroska

    # region AVI

    # ffmpeg writes these as a LIST INFO chunk (INAM, IART, ICRD, ICMT, ICOP, ISFT).
    New-Video (Fixture 'avi-info.avi') @(
        '-c:v', 'mjpeg', '-q:v', '10', '-c:a', 'pcm_s16le',
        '-metadata', 'title=Fixture clip',
        '-metadata', 'artist=Jane Fixture',
        '-metadata', 'date=2026-09-12',
        '-metadata', 'comment=Fixture comment',
        '-metadata', 'copyright=Jane Fixture'
    )

    # endregion AVI

    # region In-stream user data

    <#
    AVCHD and XAVC camcorders write the recording date and GPS as an MDPM block in H.264 SEI user
    data. ffmpeg can't write one from scratch, so h264_metadata inserts a fake one after
    filter_units has dropped x264's own SEI, which leaves MDPM the only user data, as on a camera.
    The option is a C string, so the block has no zero bytes: its rationals use 0x0101 rather than
    1 as the denominator. It isn't text either, so ffmpeg reads the option from a file (-/bsf:v).
    #>
    $mdpmEntries = @(
        0x18, 0x02, 0x20, 0x26, 0x09,   # time zone +01:00, 2026-09
        0x19, 0x12, 0x14, 0x30, 0x15,   # the 12th, 14:30:15
        0xB1, 0x4E, 0x20, 0x20, 0x20,   # latitude ref "N"
        0xB2, 0x30, 0x30, 0x01, 0x01,   # 48°
        0xB3, 0x33, 0x33, 0x01, 0x01,   # 51'
        0xB4, 0x73, 0x93, 0x03, 0xE8,   # 29.587"
        0xB5, 0x45, 0x20, 0x20, 0x20,   # longitude ref "E"
        0xB6, 0x02, 0x02, 0x01, 0x01,   # 2°
        0xB7, 0x11, 0x11, 0x01, 0x01,   # 17'
        0xB8, 0x9D, 0x08, 0x03, 0xE8    # 40.2"
    )
    $ascii = [System.Text.Encoding]::ASCII
    $mdpm = [byte[]] ($ascii.GetBytes('MDPM') + [byte] ($mdpmEntries.Count / 5) + $mdpmEntries)
    $mdpmFilter = Join-Path $work 'mdpm-bsf.txt'
    $filterText = 'filter_units=remove_types=6,h264_metadata=sei_user_data=17ee8c60-f84d-11d9-8cd6-0800200c9a66+'
    [System.IO.File]::WriteAllBytes($mdpmFilter, [byte[]] ($ascii.GetBytes($filterText) + $mdpm))
    $h264Mdpm = @('-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '40', '-pix_fmt', 'yuv420p', '-/bsf:v', $mdpmFilter)

    # AVCHD: 192-byte M2TS packets, H.264 with MDPM, AC-3 audio.
    New-Video (Fixture 'm2ts-camcorder.m2ts') ($h264Mdpm + @('-c:a', 'ac3', '-b:a', '64k', '-f', 'mpegts', '-mpegts_m2ts_mode', '1'))

    # Sony XAVC S: the same MDPM, in length-prefixed MP4 samples.
    New-Video (Fixture 'mp4-xavc.mp4') ($h264Mdpm + @('-c:a', 'aac', '-b:a', '16k', '-brand', 'XAVC') + $bitExact)

    # HEVC in 188-byte TS packets, with the encoder settings x265 writes as SEI user data.
    New-Video (Fixture 'ts-hevc.ts') @(
        '-c:v', 'libx265', '-preset', 'ultrafast', '-crf', '40', '-pix_fmt', 'yuv420p', '-x265-params', 'log-level=none',
        '-c:a', 'aac', '-b:a', '16k', '-f', 'mpegts'
    )

    # endregion In-stream user data
} finally {
    Remove-Item -Recurse -Force $work
}

Get-ChildItem $fixtures -Include *.mp4, *.mov, *.3gp, *.m4v, *.mkv, *.webm, *.avi, *.ts, *.m2ts -Recurse |
    Sort-Object Name | Format-Table Name, Length -AutoSize
