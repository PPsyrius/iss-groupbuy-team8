# Walkthrough screens: provenance

These eleven PNG files are frames from one screen recording of the buyer journey on BlocBuy.

- Source file: `blocbuy.mov` (8 min 27 s, 3004x1736, H.264). The video is not committed.
- Recorded: 24 October 2026 (the menu-bar clock reads 20:09 to 20:17 SGT), on the SWE5006 monolith behind the dev CloudFront distribution. The payments shown were made at 19:10 (leader) and 19:17 (member) SGT. Figures a03 to a07 come from an edited clip of the member's steps that was recorded the same evening and played inside the recording.
- Tool: ffmpeg 8.1.2 only. No frame was edited other than by the crop, blur and scale steps listed below; a08 also stacks two crops of one frame.
- Timestamps are seconds from the start of `blocbuy.mov`.
- Boxes are `x,y,w,h` in the original 3004x1736 frame. Blur boxes are applied first, then the crop.
- Blur: each blur box is pixelated (downscaled by 18 and scaled back with nearest neighbour) and then Gaussian blurred (sigma 6), so the names and avatars cannot be read.
- Every output is scaled to 1600 px wide (Lanczos) and saved as a palette PNG.

## Command

Each figure is made by one call of this script, with the arguments in the table:

```bash
# make.sh NAME TIME CROP(w:h:x:y) [BLUR x,y,w,h ...]
FF=/opt/homebrew/bin/ffmpeg
SRC=blocbuy.mov
name=$1; t=$2; crop=$3; shift 3
fc="[0:v]null[s0]"; i=0
for b in "$@"; do
  IFS=, read -r bx by bw bh <<<"$b"
  n=$((i+1))
  fc="$fc;[s$i]split=2[m$i][c$i];[c$i]crop=$bw:$bh:$bx:$by,scale='max(2,trunc(iw/18))':'max(2,trunc(ih/18))',scale=$bw:$bh:flags=neighbor,gblur=sigma=6[p$i];[m$i][p$i]overlay=$bx:$by[s$n]"
  i=$n
done
fc="$fc;[s$i]crop=$crop,scale=1600:-2:flags=lanczos,split[a][b];[a]palettegen=max_colors=${COLORS:-256}:stats_mode=full[pal];[b][pal]paletteuse=dither=${DITHER:-sierra2_4a}"
$FF -nostdin -y -ss "$t" -i "$SRC" -frames:v 1 -filter_complex "$fc" -compression_level 100 "$name.png"
```

## Figures

| File | Time (s) | Screen | Crop (x,y,w,h) | Blur boxes (x,y,w,h) | Arguments |
|---|---|---|---|---|---|
| `a01-listing.png` | 66 | Listing page, product and open team (from the member's signed-out invite landing) | 620,507,1764,1043 | none (the invite banner is outside the crop) | `COLORS=64 DITHER=bayer:bayer_scale=5 make.sh a01-listing 66 1764:1043:620:507` |
| `a02-leader-paid.png` | 53 | Leader's "Payment received" card | 885,431,1237,1019 | none (the header is outside the crop) | `make.sh a02-leader-paid 53 1237:1019:885:431` |
| `a03-member-invite-signed-out.png` | 66 | Member's invite landing, signed out: site header with "Sign in" and the invite banner | 600,64,2145,430 | 648,266,84,82 (leader avatar); 738,272,116,42 (leader name in the banner) | `make.sh a03-member-invite-signed-out 66 2145:430:600:64 648,266,84,82 738,272,116,42` |
| `a04-member-sign-in.png` | 68 | "Sign in to hold your seat" | 668,167,1788,954 | none | `make.sh a04-member-sign-in 68 1788:954:668:167` |
| `a05-member-invite-signed-in.png` | 86 | Member's invite landing, signed in | 520,95,1970,905 | 552,211,90,90 (leader avatar); 650,222,136,40 (leader name in the banner) | `make.sh a05-member-invite-signed-in 86 1970:905:520:95 552,211,90,90 650,222,136,40` |
| `a06-stripe-checkout.png` | 100 | Stripe Checkout in test mode | 668,48,1645,1323 | none (the email field is already blacked out in the clip) | `make.sh a06-stripe-checkout 100 1645:1323:668:48` |
| `a07-member-paid.png` | 130 | Member's "Payment received" card | 996,145,1014,838 | none (the header is outside the crop) | `make.sh a07-member-paid 130 1014:838:996:145` |
| `a08-member-my-blocs.png` | 155 | Member's My blocs, Completed tab (a screenshot shown in an image viewer): two regions of the same frame stacked, split by a 16 px grey band | top 730,316,1046,440 (title, tabs, Universal Travel Adapter team card); bottom 730,1322,1046,218 ("My orders" and its first row) | none (the header, browser sidebar and bookmarks are outside both crops) | see the a08 command below |
| `a09-leader-my-blocs-completed.png` | 190 | Leader's My blocs, Completed tab | 238,334,1514,948 | none (the header is outside the crop) | `make.sh a09-leader-my-blocs-completed 190 1514:948:238:334` |
| `a10-refunded-tab.png` | 220 | Leader's My blocs, Refunded tab (teams closed on 23 October) | 238,405,1514,1133 | none (the header is outside the crop) | `make.sh a10-refunded-tab 220 1514:1133:238:405` |
| `a11-refund-sent.png` | 256 | "Refund sent" card (a team closed on 23 October) | 885,431,1237,907 | none (the header is outside the crop) | `make.sh a11-refund-sent 256 1237:907:885:431` |

The a08 command (the two Sunscreen team cards between the regions are left out; the grey band marks the cut):

```bash
ffmpeg -nostdin -y -ss 155 -i blocbuy.mov -frames:v 1 -filter_complex "[0:v]split=2[t][b];[t]crop=1046:440:730:316[top];[b]crop=1046:218:730:1322[bot];color=c=0xcccccc:s=1046x16:d=1[gap];[top][gap][bot]vstack=3,scale=1600:-2:flags=lanczos,split[a][c];[a]palettegen=max_colors=256:stats_mode=full[pal];[c][pal]paletteuse=dither=sierra2_4a" -compression_level 100 a08-member-my-blocs.png
```

## Notes on frame choice

- a02 uses 53 s rather than 52 s because the pointer sits over the card at 52 s.
- a05 uses 86 s. The pointer inside the clip rests on "Join this bloc · S$22.00" from 74 s to 91 s, so no frame in that span is free of it; the text stays readable. The crop stops above the player controls and the desktop pointer.
- a08 uses 155 s rather than 166 s. The image viewer shows the same static screenshot from 147.5 s to 178.4 s, and at 155 s the pointer is not on the page.
- a01 shows the pointer inside the clip on the product photo, away from the text.
- Excluded from every figure: macOS menu bar, browser tabs, address bar, bookmarks, the Dock, the QuickTime player controls, the Finder window and the private chat window at 178.5 to 179.7 s.
