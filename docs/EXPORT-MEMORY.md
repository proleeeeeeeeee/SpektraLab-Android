# Export memory admission and bounded frame readback

The export sheet estimates input-side memory before queuing single or batch output.
It refreshes the current application memory budget every two seconds and blocks an
estimate above that budget with an actionable reason. The native guard still runs
when processing starts. There is no silent reduction of output dimensions.

The estimate mirrors native admission: 160 bytes per decoded input pixel plus
128 MiB reserve (176 bytes for an HDR estimate). RAW decoding additionally requires
the full sensor raster at 40 bytes/pixel plus reserve, even for Compact output.
Frames, codec and driver allocations can require more, so passing the estimate is
not a promise that a particular image/device can export. The memory budget no
longer raises a genuinely low available value to a 128 MiB minimum.

Film rendering, grain and halation remain full-image operations with the same
parameters. Only CPU ownership of the stable final frame readback changed. The
photo pass retains one floating-point result; the stable frame pass reads bounded
row bands (about 4 MiB), replacing pixels outside the exact image gate in that same
result. It no longer retains a second complete floating-point frame image. This is
not spatially tiled film processing and does not remove GPU working buffers.

## Measured example

Android 35 x86_64 isolated emulator; synthetic 2000 x 1500 input, grain=1,
halation=1, Strip 135 frame, exposure=1, temperature=12; original-size PNG output
2086 x 1931. Identical PNG SHA-256 before and after:

`3609781bdadf8e9f06c19017d1683fcabc49fed2853144f8a994ccfab1b9eb46`

| Metric | Before | After |
| --- | ---: | ---: |
| Frame CPU scratch capacity | 48,336,792 B | 4,180,344 B |
| Main float readback capacity | 48,336,792 B | 48,336,792 B |
| GPU live peak | 262,170,396 B | 262,170,396 B |
| GPU resident peak | 423,324,138 B | 423,324,138 B |
| Sampled process PSS | 178,012 KiB | 139,822 KiB |
| Sampled native heap | 111,437,040 B | 79,186,880 B |

Scratch capacity is an exact allocation metric. Process peaks were sampled every
50 ms and are affected by scheduling, runtime state and the emulator; they are not
portable guarantees. Timings were 23.6 s and 36.4 s in separate instrumented runs;
no speed improvement is claimed. The optimisation does not establish that an
arbitrary 12/50/200 MP original can fit a phone's GPU or application budget.
