# Changelog

## 0.5.12 (unreleased): knife-edge ridges; the sampler is over

Sammi on the 0.5.11 sampler: "can we make the gullies less fluffy?", then, of the three looks,
"I can't really distinguish between them". He sent two reference pictures (a summit where thin
sharp ridges meet and run a long way down; a row of horns over a lake valley). Claude's reading,
which he accepted: the references are made of knife-edge ridges with hollow faces between; the
mod makes rounded shoulders, flat even faces and a few wide soft grooves, "like it has been
sanded". On the pictures' fine detail being out of reach at 170 blocks: "Fine I understand
about the detail I just want the feel".

- New last step (`ridges` in Massif.java): the lines where water gathers on a face are cut down
  into couloirs (up to 30 blocks), and the rock rises from every couloir at one fixed angle
  (62 degrees) until it meets the rock rising from the next. Where they meet is an edge; nothing
  rounds it afterwards. Summits are spared: no couloir is cut so deep that its rock would stand
  below one.
- The sampler is gone: every mountain is look A plus this. `/locate mountain a|b|c` removed.
- Two things tried first and NOT kept, both fine in the preview and no better in his world: a
  wider spread of the water on the coarse grid (it took a quilted criss-cross of bumps out of
  the preview's dense peaks; on real chunks before and after were nearly the same), and less
  noise with narrower gullies.
- New testing aids: `tools/preview/shade.py` (a low-sun shaded view: the plain renders hide
  gullies altogether); a file /tmp/atlas_set of NAME=number lines sets any Massif setting on the
  dev server.

Checked on a dev server, real chunks, two seeds, no errors in the logs:
- -5999978045571072206 at 4200 -650: five summits y 193, 198, 280, 283, 270 before; 193, 198,
  280, 280, 268 after. In renders from his camera spots the shoulders are notched into small
  horns; the big faces are still mostly plain.
- 6021084639467433954 at -7500 5600: six summits within 3 blocks of before, bar one 6 lower
  (218 to 212). The broad humped snowy top of his 0.5.4 screenshots is a cluster of sharp horns.
  Ground on the mountains is 12 blocks lower on average, 45 at most, never higher.
- Erosion 689 ms per region on average, 1439 at worst.

Not tested: in the game by eye. Known: in the preview a small patch of criss-cross lines
remains where many couloirs crowd together.

## 0.5.11 (unreleased, SAMPLER BUILD): three looks for the shape

Sammi after 0.5.10: "im not sure if the mountain shape is right yet". Claude agreed (faces
plain close up, lone peaks near cones, some humped tops) and proposed a sampler, the method he
has said works best; he said "show me".

Every peak has one of three looks, blended smoothly between neighbours:
- A as before (0.5.6's shape).
- B the faces carved twice as deep (cut and noise both doubled: `LOOK_CARVE`).
- C the same deep carve on a sharper mountain: its height as a share of the summit's is raised
  to the power 1.4 (`LOOK_SHARP`), which keeps the summit, steepens the upper faces, narrows the
  top and eases the lower slopes.
`/locate mountain a`, `b`, `c` find the nearest good-sized one of each (within 16,000 blocks: tall
ones of one look can be far apart; on the test seed the nearest B was 7,000 away); `/locate mountain` now
says which look a mountain is.

Checked on a dev server, real chunks, seed -5999978045571072206 at 4200 -650, each look forced
on every peak in turn: five summits at y 193, 198, 280, 283, 270 in A and B; 189, 196, 280,
279, 266 in C (up to 4 lower). In renders from his camera spots C is plainly sharper; A and B
are hard to tell apart without sun and shadow. The commands answer; no errors in the log.
Not tested: B and C on the snowy seed (the run was cut short); in the game.

## 0.5.10 (unreleased): biomes: no jungle on mountain feet, no snow patches beside the mountains

Sammi, seed -6677413799414465054, six screenshots: "somethings messing with the biomes". A round
patch of snow and spruce in green forest (-1341 1932), jungle trees and bamboo at the feet of
mountains in cold forest, a block of land with sheer grey walls and jungle on top (-1419 1686).
Reproduced on a dev server (1024 square at -1380 1750) and the climate read at each spot.

- Jungle (the mod's fault, there since 0.5.2 on every seed): on a mountain the biomes are shown
  a temperature of 0.4 so that the rock is vanilla's stony peaks. That was applied from 3 blocks
  of height on any mountain ground; the erosion and weirdness that go with it only from 7 blocks
  and only on rock or open grass. Ground with the temperature alone (wooded feet, and a ring
  round every mountain) was vanilla's warm band: jungle wherever it is wet. Now all three switch
  together at one hard step, on rock and open grass only. Jungle leaves, vines and bamboo in the
  square: 4,501 columns then 85. On the two earlier seeds: 2,113 then 38, and 6,300 then 63.
- Snow patches (vanilla's own grove biome, which the mod removes inland but did not near the
  coast): the mod shows vanilla's cold slopes a gentler erosion so they are forest or taiga,
  not grove. That began at continentalness -0.05; vanilla's slope biomes begin at -0.11. In the
  strip between, grove was left. Now it begins at -0.11. Snow below y 140 in the square: 14,070
  columns then 29; powder snow 2,797 then 0. Snow on the summits unchanged (6,700 and 6,805).
- Not changed: where peaks stand, the shape, heights. The same summits at the same heights on
  all three seeds.

NOT FIXED, the sheer-walled block: where a mountain is under about 20 blocks tall the ground is
vanilla's own rough land; above that it is the mountain's clean shape standing on vanilla's
SMOOTH target height. Where vanilla's rough land stands far above its own target (33 blocks at
-1400 1638) the two meet at a wall. In the test square 1.4% of wooded mountain ground has a
step of 10 blocks or more, against 0.2% of rock. This is the "steep walls" and "ledge at a
mountain's foot" of the 0.5.1 screenshots, never touched.

Checked on a dev server, real generated chunks, three seeds (this one; 6021084639467433954 at
-7500 5600; -5999978045571072206 at 4200 -650), no errors in the logs. `tools/build_overhaul.py`
reproduced the committed data pack exactly before these edits. Not tested: in the game by eye.

## 0.5.9 (unreleased): commands that work like vanilla's

Sammi: "can we redo the commands this mod adds keep the ones you need but for my 'basic' user
experience can we make it close to how vanilla works e.g. /locate biome atlas mountains".
There is no Atlas biome to locate (the mountains use vanilla's own peak biomes on purpose), so
vanilla's /locate gains a word of its own instead.

- `/locate mountain`: the nearest mountain within 6,000 blocks, answered as vanilla answers:
  "The nearest mountain is at [x, y, z] (n blocks away, n blocks tall)", coordinates green and
  clickable (they put a /tp in the chat box). The y is the real ground at the top.
- `/locate mountain tallest`: the tallest within 6,000 blocks.
- `/atlasmountains`: the version, and a pointer to the two above.
- Everything that was under `/atlasmountains` is now under `/atlasmountains debug` (mountains,
  df, dfmap, biomemap, peaks, snapshot, heightmap), and needs cheats or operator as /locate does
  (before: anyone). `tools/dev` scripts updated.
- No change to the terrain: the shape is 0.5.8's.

Checked on a dev server (seed -5999978045571072206) through the server console: all three new
commands answer; `/locate biome` still works; the old `/atlasmountains peaks` is refused; the
block under the place /locate mountain gave is solid with air at feet and head; at the
"tallest" one the ground is solid and the block at feet is not air (a layer of snow, most likely).
A first version gave a y one block too high; found by this check and put right. No errors in
the log. Not tested: clicking the coordinates in the game's chat; a player without cheats.

## 0.5.8 (unreleased): the shape of 0.5.6 back; 0.5.7's shape changes undone

Sammi's seven screenshots of 0.5.7 on a new seed, -5999978045571072206, hot country (badlands,
savanna) around 4200 -650: plain smooth pointed cones, three in a row at 3780 -758; almost no
snow; a hole through a summit near 4263 -1016.

What Claude did, in order, all on real generated chunks at that spot (1024 square at 4200 -650),
none of it sent as a jar:
1. Guessed the round starting lump was the cause; made it uneven (warped, creased). Still cones.
2. Guessed one rock angle everywhere was the cause; let it vary. In the preview this made a lone
   peak smoother, not better. (The preview's plain 0.5.7 peak was craggy: the preview has no
   real land and does not show this fault.)
3. Read out the erosion at his peaks: to reach their set heights the mountains were rising 2.2
   to 5 times their lump (1.5 is the setting), because the real ground falls 50 to 70 blocks
   within 100 of each summit. Capped the rise at 2 with a bigger last stretch. Still cones.
4. Ran the 0.5.6 code on this seed and spot. NOT cones: shoulders, broken tops, snow. So the
   cones came from 0.5.7.

This build: `Massif.java` is 0.5.6's again. Undone: erosion on the real ground, mountains
rising to their own height, the noise taken off near summits, the cap on the last stretch.
Kept from 0.5.7: grass and trees on steeper feet (`GRASS_SLOPE` 1.1), which is a surface rule
and not part of the shape.

Back again as a result, and still to do, ONE AT A TIME and each checked on BOTH seeds before a
jar: the tower (seed 6021084639467433954, -7616 5824), the domes, sides stretched steeper than
the rock should stand. Not looked at: the hole through a summit; no snow in hot country.

Measured at five summits on the new seed, how far downhill turns from "straight away from the
summit" (0 is a perfect cone; gullies raise it too, so it is a rough guide): 0.5.6 52 degrees
on average, 0.5.7 42.

Not tested: this exact build has been compiled but not run (its shape code is 0.5.6's, run on
both seeds; its grass rule is 0.5.7's, run on both seeds); in the game by eye.

## 0.5.7 (unreleased): the tower, the domes, the hard line where rock meets forest

Three things Claude had listed from Sammi's screenshots of 0.5.4 (seed 6021084639467433954) and
he asked to have tried: "Nice try those next". Three changes in one build, at his asking.

- The tower (summit -7616 5824, seen from -7732 5949). Cause, measured: the mountain's own slope
  was 43 degrees, but vanilla's land under it fell 60 blocks in the same 108 towards a river:
  56 degrees in all. Slopes were only measured on the mountain, as if the ground were flat.
  Now the water and the rock slides work on the real ground (vanilla's land plus the mountain).
- Found on the way, and behind steep sides everywhere: erosion left most mountains well short of
  their set height and a last step stretched them up to it, which also stretched 52-degree
  slopes past 60. Now each mountain rises as fast as it needs to arrive at its set height
  during the erosion (it gets within about a tenth), and the last stretch is at most 1.2.
  A FIRST TRY without this, only capping the stretch, was not sent: summits 30 to 45 blocks low
  and flat-topped.
- THIS CHANGES HEIGHTS, which he has said not to touch unless he asks: a mountain hemmed in by
  a river and falling land now comes out LOWER than set instead of as a wall. On the test
  square, six summits, 0.5.6 then 0.5.7: y 225 230, 269 268, 289 277, 218 219, 259 242, 252 254.
- Domes. Cause: the noise on the lumps is smooth and up to 34 blocks, so near a summit it made a
  rounded bump that became the summit. It now fades out within 70 blocks of every summit.
- The hard line. Cause: the feet of these mountains are steeper than the old flanks, so nearly
  all of the foot counted as too steep for anything to grow. `GRASS_SLOPE` 0.85 to 1.1 (about
  48 degrees at the very foot, less and less higher up, as before).

Checked on a dev server on his seed, 1024-block square at -7500 5600, real generated chunks, no
errors in the log. South from the tower's summit the ground went y 252, 233, 211, 183, 166,
148, 121, 102, 94 every 12 blocks; it now goes 212, 194, 181, 173, 158, 145, 130, 116, 100
(the top of that mountain has moved a little; its highest point is y 254). Erosion 1428 ms per
region on average, 1895 at worst (0.5.6: 787 and 1370): the land is now looked up for every
sample.

Seen in renders from his camera spots: the tower is a group of sharper, lower-angled peaks;
grass runs up the feet. The big mountain of his first screenshot still has a broad snowy top.

Not tested: in the game by eye; other seeds; cold biomes; a river with water in it running
between two peaks (0.5.6).

## 0.5.6 (unreleased): a river valley between two peaks of a group is covered

Sammi on the 0.5.5 comparison: "the valley running through is forcing that side to be silly
steep". Measured on his seed 6021084639467433954 between the summits at -7025 5637 and
-7068 5828 (195 blocks apart, a vanilla river valley at y 95 between them): 186 blocks in 90,
64 degrees. Offered three ways out (lower peaks near rivers; let the group cross the valley;
keep it) he chose: "burry it".

- Where a river runs between two peaks of a group (the second-nearest peak's lump reaches the
  column at 15% of its height or more, fully at 32%: `BURY_FROM`, `BURY_FULL` in Massif.java)
  the mountains no longer fade out for it, and meet over it at a saddle.
- A river along the outside of a group, where only one peak reaches, keeps its valley as before.
- THIS BENDS A STANDING RULE ("do not change the river settings unless he asks"): he asked. The
  settings in build_overhaul.py are untouched; three places that kept rivers clear now stand
  aside where a river is covered: the fade in Massif, the 12-block ease in HornsFunction, and
  the data pack's own limit (`river_wall`: nothing higher than 1.73 times its distance from the
  channel, a 60-degree wall). Claude missed the third at first and the valley was still there.
- Unchanged: where peaks stand, heights, the face carve, surface rules.

Checked on a dev server on his seed, 1024-block square at -6850 5700, real generated chunks, no
errors in the log:
- Along the line between those two summits the lowest ground was y 95; it is now a saddle at
  y 184. Summits y 289 and 259 (0.5.5: 289 and 258).
- A first try read "covered" to the nearest 4-block sample and the ground stepped by up to 9
  blocks every 4; it is now read smoothly and the steps are gone (columns with a jump over 12
  blocks: 0.32% of the square, the same as 0.5.5).
- Erosion 787 ms per region on average, 1370 at worst. Regions hold 1.8 MB each (was 1.2).
- Water covers 7.5% of the square before and after: no river with water in it was covered here.

Not tested: a river WITH WATER running between two peaks (what the two ends look like where it
goes under); in the game by eye; other seeds; cold biomes.

## 0.5.5 (unreleased): the faces carved

Sammi's seven in-game screenshots of 0.5.4 (seed 6021084639467433954). Claude's reading, which he
agreed to test: groups and outlines better, but below the summits nearly every face is one plain
smooth slope (two cones side by side at -6865 5832, a flat-faced horn at -6841 5586). Shown
before, a light carve (A) and a deep carve (B) from those two camera spots, real chunks, he
chose A: "A is looking best i think".

- The faces get their own pass on the 4-block grid (`gullies` in `Massif.java`): a little noise,
  then water run down them eight times, each time cutting most where most has gathered, so
  runnels deepen into gullies with ribs between. Nothing is cut on crests and summits or on the
  lowest 22 blocks of a mountain. What is cut counts as gully for the snow, so caps have tongues.
- Nothing else changed: the big shape, the groups, where peaks stand, heights, surface rules.

Checked on a dev server on his seed, 1024-block square at -6850 5700, real generated chunks, no
errors in the log: the summits at -7025 5637 and -7068 5828 are at y 289 and 258 before and
after. Erosion 728 ms per region on average, 1451 at worst (0.5.4 on the same square: 577 and
1190). The effect is subtle; B (cut 4.5, noise 7, full at 20) was clearer and he passed it over.

Not tested: in the game by eye; other seeds; cold biomes; coasts.

RAISED BY HIM, NOT TOUCHED: "the valley running through is forcing that side to be silly steep".
Measured between those two summits (195 blocks apart, a vanilla river valley at y 95 between
them): the ground falls 186 blocks in 90, about 64 degrees. Cause: each mountain fades to
nothing towards the river, then is scaled back up to its set height, which stretches it past
the 52 degrees the rock is meant to stand at. Not his other listed points either: the tower at
-7732 5949, the rounded domes, the hard line where rock meets forest.

## 0.5.4 (unreleased): the shape made by running water; peaks in a group joined

Sammi on 0.5.3: "I don't think that's good enough". Agreed plan: a preview tool first, then let
erosion make the shape. On 14 Nov 2026 he was shown four versions from the preview (A 0.5.3,
B separate peaks eroded hard, C joined peaks rising while eroded, D the same with ice) and chose
C, then: "ill check what this look like in vanilla. im worried the references were sending you
down a wrong rabbit hole". This build is C in the game, to be judged there, not against the
reference pictures.

- Shape (new file `Massif.java`): each peak gets a rough lump wider than the gap to its
  neighbours, so the peaks of a group share a base and meet at saddles. The lumps rise slowly
  while running water cuts them (each cell lowered by the square root of the area draining
  through it) and rock steeper than 52 degrees slides. Valleys and ridges come from the
  drainage. Each mountain is then scaled so its summit is its set height.
- THIS BENDS A STANDING RULE: until now every peak was a separate mountain. Peaks in a group are
  now joined. Groups are still separate, and there is no ridge line drawn by hand.
- Gone: the crest, cross profile, legs, scoop, swell and droplet erosion of 0.5.0 to 0.5.3
  (in the repository's history, commit 7afdb25), and the scoop sampler. No sampler in this build.
- Unchanged: where peaks generate, their spacing and their heights; the fade towards rivers;
  snow, bands of growth, rock; vanilla's land is not lowered.
- Preview tool (`tools/preview`): compiles the mod's own ErodedPeaks, Massif and SnowLine against
  stand-ins for the game and draws a 1024-block square in about 10 seconds. Its land, rivers and
  mountain country are stand-ins, so it shows shapes, not places.

Checked on a dev server on his seed 5300383603861270436, 1024-block square at -200 -450, real
generated chunks, no errors in the log:
- The summit at 123 -350 is at y 330 (0.5.2 and 0.5.3: 330).
- Erosion: 630 ms per region on average, 1234 at worst (0.5.3: 411 and 870).
- Surface of the whole square: rock 18%, snow 2.4%, grass and trees 60%, water 8.8%.
- In the preview, two neighbouring regions eroded separately differ by 0.4 blocks on average and
  5 at worst across the band where they are blended.

Known, seen in renders of that square: the upper third of a big mountain is a pointed pyramid
with smooth faces; the water cuts the valleys and lower ridges and barely reaches the tops. Two
fixes were tried in the preview and dropped (the old droplets silt up the valleys and leave the
tops as they were; making water cut small slopes harder rounds the summits). In his world the
peaks are sparser than in the preview pictures he chose from, so groups are smaller.

Not tested: in the game by eye; any other seed; cold biomes; coasts; a long flight.

## 0.5.3 (unreleased, SAMPLER BUILD): "more realistic": snow in the hollows, bands of growth, scooped faces, a shared base

Sammi on five in-game screenshots of 0.5.2: "I want them to be more realistic". Claude listed
five differences from real mountains and his reference pictures; he chose four: (1) snow in the
wrong places, (2) no bands of vegetation, (3) faces that bulge where real ones are scooped,
(5) peaks standing on flat ground like separate objects. Not chosen: (4) every side alike.

- Snow (1). The snow line is now compared with the ground 20 blocks round about, not with each
  column's own height, so a hollow counts as being as high as its rim and holds snow; ribs lose
  up to 3 blocks and ground steeper than about 50 degrees up to 7 (`snowShift`). At his spot
  (163 331 -523) 0.5.2 had white along the ribs and bare hollows; 0.5.3 has white in the hollows
  and bare ribs. It is patches there, not one cap. Bare columns inside caps: 0.63% then 0.25%
  of snow columns.
- Bands (2). From the foot up: vanilla's own biome with its trees (lowest 12% of the height),
  open grass with no trees (to 24%), grass in patches among the stone (to 34%), then rock; ground
  too steep for its height is rock anywhere. The open grass is the peak biome with grass and
  dirt laid by the surface rule. On the mountains' area: rock 59% then 43%, open grass 21% then
  36%, trees 9% then 12%.
- Scoop (3), THE SAMPLER: away from the legs each face is lowered, most half-way up, so the legs
  stand as edges round a bowl. Three depths, 14, 22 and 30 blocks for a full-size peak:
  S light, M medium, L deep (`/atlasmountains peaks` lists the nearest of each). The crest
  length, the old sampler he never chose between, is now drawn for each peak and not labelled.
- Shared base (5): every peak lifts the ground round it by a quarter of its height, fading over
  about half a cell; lifts add up where peaks are close (capped at 40% of the nearer peak), so a
  group stands on one swell and meets at saddles. Summits are not raised (two checked: y 330
  and 207, as in 0.5.2). Mountains now cover 46% of the test square (0.5.2: 33%).
- Erosion: 411 ms per region on average, 870 at worst, on the test server (0.5.2: 318 and 658).

Checked on a dev server on his seed 5300383603861270436, 1024-block square at -200 -450, real
generated chunks, no errors in the log. Not tested: in the game; other seeds; cold biomes.

Known: trees still run up between two peaks at his spot 165 139 -868. That ground is a vanilla
hill the mountains sit on, low on the mountains by the rule. The swell raises land beside the
sea and lakes too; what that does to coasts is unchecked. The steep walls beside water and the
overhanging ledge of his 0.5.1 screenshots are untouched.

## 0.5.2 (unreleased, SAMPLER BUILD): grass up the gentle lower slopes, more structure, a height mistake put right

Sammi's nine in-game screenshots of 0.5.1 (seed 5300383603861270436). Agreed reading: three
mountains still "too soft" (plain smooth cones), two of the kind he wants (jagged tops, ribs),
holes in some snow caps. He added: "the flats should be grass and go up the mountain a bit
where it's less steep, but natural".

- Grass. Low on a mountain, ground that is gentle enough keeps vanilla's own surface and biome
  (grass, trees): at the foot anything under about 40 degrees, less and less higher up, none
  above 38% of the mountain's height, with a wandering edge (`GRASS_SLOPE`, `GRASS_UP` in
  ErodedPeaks.java; new density function `peak_rocky`). On his seed, 1024-block square at
  -200 -450: of the mountains' area, rock 79% then 59%, grass and trees 10% then 34%.
- A MISTAKE IN 0.5.0 AND 0.5.1, FIXED: every mountain stood about 20 blocks taller than its set
  height. Claude had compared summits with the height the peaks command teleports to (20 above
  the summit), "found" erosion taking 19 blocks off, and built every mountain 19 taller to make
  up for it. Measured properly now: two summits at y 330 and 207 against set heights 328 and
  207. Snow caps are smaller as a result (the snow line is a quarter of the height below the
  summit).
- Softness. The soft mountains in his screenshots were not all one sampler look (as far as ten
  peaks in the test square can show: one soft one is an M, another an L or an S), so no look is
  dropped. Every mountain gets one more leg (4, 3, 3 at least), stronger detail noise before
  erosion (9 for 5.5), half as many droplets again and a deeper bite. Measured effect is small:
  on the two big M mountains, how far the downhill direction turns away from "straight down
  from the summit" (0 on a perfect cone) went from 34 to 41 degrees. Expect a modest change.
- Snow holes: NOT confirmed fixed. Two changes (only real gullies, not sediment, count as
  gullies for the snow; sheltered hollows hold snow lower). Claude's count of bare columns
  inside caps did not go down, and the large holes of his screenshots did not show up in
  renders of this square in either version.
- Erosion now takes 318 ms per region on average, 658 at worst, on the test server (0.5.1: 175
  and 391).
- `/atlasmountains peaks` can list more than three per look on a test server
  (`-Datlasmountains.list=60`); players see three as before.

Not touched: the steep walls where a mountain meets a river or the coast, and the overhanging
ledge at a mountain's foot (both seen in his screenshots, neither asked for).
Not tested: in the game; any other seed; cold biomes.

## 0.5.1 (unreleased, SAMPLER BUILD): no rock patches without a mountain; mountains by rivers keep their summits

Sammi's first in-game looks at 0.5.0. (1) A screenshot at -9837 -2238, seed -3550034452994718772:
a flat patch of bare rock in a grassy hollow, no mountain. (2) Six screenshots on seed
-4410578526466350703: "some times nice and angularly eroded, sometimes too soft" (a sharply
gullied snowy horn on one hand; smooth bullet-shaped cones with rounded tops and a low smooth
dome on the other).

- The rock patch was the rock line of 0.4.10 (in mountain country, ground higher than 100 blocks
  under the snow line is rock). Next to a small, low peak that line fell below the ordinary land
  around it. Claude first blamed the fade towards rivers and was wrong: the patch was still there
  with the fade changed, and the peak-height map showed no mountain under it at all. The rock
  line is now off (`ROCK_BELOW` in build_overhaul.py); the eroded mountains sit on top of the
  land, so there is no land showing through them for it to cover. Checked on his seed and spot:
  bare rock within 110 blocks of it 7,969 columns before, 625 after (vanilla's own stone and
  gravel); across the 640-block square, rock with no mountain under it 24,464 then 4,382.
- The fade towards rivers was a flat 90 blocks, applied after erosion. A summit nearer a river
  than that was squashed into a rounded bullet or a dome. Each mountain now fades over 0.7 of
  its own summit's distance to the river (16 to 90 blocks), before erosion, so the summit is not
  touched and the river side is carved like the rest. Checked by rendering 0.5.1 from four of
  his six camera spots on his seed: the bullets and the dome in his screenshots are pointed,
  gullied mountains (or, for the dome, grass) in the renders. Not measured, and not seen in game.
- Not changed: the three sampler looks, erosion strength, snow and rock bands. "Too soft" is
  only answered as far as the river fade caused it; plain smooth faces are as they were.

Test-server note: a 1280-block debug snapshot ran the server out of memory after it was written
(the same happened with a 1408 snapshot on 0.4.13). Whether the eroded regions in memory (up to
36, 1.2 MB each) add to that in normal play is not known.

## 0.5.0 (unreleased, SAMPLER BUILD): the old mod's eroded mountains, one per peak, in a normal world

Sammi's in-game verdict on 0.4.13 (four screenshots from above, a badlands area): "these feel too
geometric and far away from my reference images". Agreed reading: every 0.4 peak is flat planes
with flat panels; the references are eroded shapes. Then: "scrap this newer one as the mountains
looked better on the old mountains, we just need to find a way to get them to generate singularly
like these", meaning "the old old one where we had to change the world gen type", and "is there
any way to get them in vanilla world gen as a 1 for 1 of vanilla mountains?" That is this build.
(He asked for a "rectangular" top and withdrew it ten minutes later; it is not built.)

What changed:
- Shape (new file `ErodedPeaks.java`): each peak is one stand-alone mountain of the old mod's
  design: a short crest through the summit, the old cross profile (rock face at 53 degrees for
  the upper half, concave flank below), two to four legs with small notched peaks running out
  from the summit, the old detail noise, then the old erosion (thermal, water droplets, valley
  fill, thermal). The old design is scaled as a whole to each peak's height.
- Erosion runs on a 1024-block region at a time, the first time a column in it is needed, and is
  kept in memory (not on disk). Regions hand over to each other across a 128-block band.
- Different from the old mod: droplets land only on mountains and three times as thickly, and
  erosion treats the top three quarters of a mountain as rock (the old mod: the top half). The
  first test at the old numbers gave smooth cones with gullies too faint to see.
- The mountain's height is laid on top of vanilla's land at each column. Near a river it fades
  out over 90 blocks; the straight river walls of 0.4.8 (the flat slabs and domes) are gone.
- Unchanged: where peaks generate, their spacing and their heights; snow, rock bands, biomes;
  normal world type. The 0.4 flat-plane shape is still in `HornsFunction.java` but not used
  (`ERODED = true`); it goes once he has chosen a look.
- 0.4.13 (the raised valley floor) is reverted.

SAMPLER: every peak has one of three looks (`/atlasmountains peaks` lists the nearest of each):
- S very short crest: nearly a point, with three or four legs (a horn).
- M short crest, two or three legs.
- L medium crest (room for a second, lower summit), two or three legs.

Checked on a dev server on his seed (-4547848503839837251), from real generated chunks, the
896-block square of his earlier screenshots (at 6480 -6050):
- The four nearest peaks are where they were in 0.4.12 and their summits come out at the heights
  they were given (y 313, 328, 326, 254 against 313, 327, 326, 253). Erosion takes about 19
  blocks off a summit; each mountain is built that much bigger to make up for it.
- Mountains cover 45% of the square (0.4.12: 53%). Slope on them: 37 degrees typical, 58 at the
  ninetieth percentile. Surface: bare rock 40%, snow 3.0%, grass and trees 48%, water 9.7%.
- Erosion: 21 regions with mountains, 175 ms each on average, slowest 391 ms. The server started
  in 10 seconds and generated the square in 183 seconds (0.4.13: about 210).
- No errors in the server log.

Not tested: in the game by eye; any second place or seed; cold biomes; badlands (where his 0.4.13
screenshots were taken); what the fade towards rivers looks like close up; a long flight (regions
are dropped from memory beyond 36 and eroded again if revisited); other mods. The rock and snow
rules were written for the flat-plane peaks and have not been retuned.

Known risks: a very short crest (look S) can read as a round cone, which he rejected on
6 Nov 2026. The lower half of each mountain is the old concave flank, in bare rock here where the
old mod had grass; he rejected a gentle skirt on the 0.4 peaks.

## 0.4.13 (unreleased, SCRAPPED in 0.5.0): a high valley floor; the peaks rise straight out of it

Sammi drew lines on a comparison of 0.4.11 and 0.4.12, just under the peaks' dark faces:
"Everything below those lines looks quite bad." Traced back onto the terrain his lines were 65 to
100 blocks under the snow line (median 79), about the level of the land under the summits. Below
them the mountains ran on down another 100 blocks into vanilla's river valleys: 73% of the square
was below the line (half of that vanilla's own slopes, a fifth river wall, a fifth peak faces).
Shown two mock-ups, A (same shape, grass and trees below the line) and B (the ground raised to
the line), he chose B.

What changed:
- The land in mountain country is raised to a valley floor 79 blocks under the snow line
  (`FLOOR_BELOW` in HornsFunction.java), rolling by a few blocks. Land already higher is left
  alone. Fully raised within 160 blocks of the peaks, sloping down to vanilla's land by 400
  blocks out with a wandering outline (`FLOOR_FULL`, `FLOOR_END`, `FLOOR_EDGE` in
  build_overhaul.py), and coming up out of the sea and lakes over the coast (`FLOOR_COAST`).
- On the raised floor 70% of vanilla's lumpy 3-D terrain noise is taken off (`FLOOR_CALM`).
- The floor keeps vanilla's own surface: grass, trees, whatever the biome puts there. The rock
  line moved up to match (`ROCK_BELOW` 100 to 60), so rock starts about 20 blocks above the floor.
- River walls are off where the floor is raised (the rivers are under it). They still stand at
  the outer edge of mountain country.
- The foot sampler of 0.4.12 is switched off with F1 (faces straight down, no skirt) on every
  peak. Sammi did not choose between F1 and F2; Claude chose, because the floor buries most of
  the difference.
- Where peaks generate, their spacing, their heights and their shape above the line are unchanged
  (erosion, inland, river settings and "cell" untouched; the six nearest peaks at the test spot
  are at the same places with the same heights as in 0.4.12).

This bends an earlier rule on purpose: until now vanilla's land always stayed at its own height.
It is now raised (never lowered) around the peaks.

Checked on a dev server on his seed (-4547848503839837251), from real generated chunks:
- The 896-block square of his screenshots (at 6480 -6050), 0.4.12 then 0.4.13: bare rock 52% then
  27% of the surface; grass, trees and other growth 40% then 67%; water 7.4% then 4.6%; the
  middle ground height y 103 then y 146.
- A 1408-block square round the same spot: land raised on 62% of it, by 42 blocks in the middle
  case, 81 at the ninetieth percentile, 141 at most. The floor lies at y 130 to 184. Its slope:
  8 degrees typical, 17 at the ninetieth percentile. The outer slope: 21 degrees typical, 53 at
  the ninetieth percentile (vanilla's own roughness shows through there).
- The nine summits in it stand 57 to 129 blocks above the open ground round them. (The six
  nearest summits are at y 207 to 327; in 0.4.12 their faces and river walls ran down to rivers
  at y 62.)
- Peak faces still running more than 10 blocks below the line: 2.0% of the square, nearly all
  at lake and sea shores, where the floor is not raised (the left peak at his screenshot 2).
- A second place by the sea (896 square at -5600 2700): same picture, three summits 98 to 110
  above the ground round them.
- No errors in the server log in any run. No snow on the floor at either place.

Known consequences, none of them seen in the game yet:
- The mountains are smaller to the eye: rock peaks 60 to 130 blocks tall standing on green high
  ground, where 0.4.12 had bare rock 200 blocks from river to summit. From outside, mountain
  country is a broad green rise with the rock peaks on top.
- Rivers stop at the outer slope and their valleys inside mountain country are filled in.
  Strips of river biome are left on the floor without water.
- Peaks differ less in visible height than before, because the floor follows the peaks up.
- Not tested: cold biomes (vanilla lays snow on high ground in taiga and the like, so a raised
  floor there may be snow-covered), villages and other structures on raised ground, and how long
  generation takes compared with 0.4.12.

## 0.4.12 (unreleased, SAMPLER BUILD): the skirt goes; two ways to end a peak

Sammi's in-game look at 0.4.11 (six screenshots, new world, seed -4547848503839837251): "looking a
bit weird in a few places, not very realistic at all": a giant flat tilted slab, a dome, a ramp,
faces striped like a corrugated roof. Then, on a view of three peaks' tops: "this top part looks
good, I don't like the bottom parts; if we just had the mountain more or less stop here".

Measured on his seed, 896-block square at 6480 -6050: of the ground the peaks formed, only a
quarter was their own tops and faces; a third was the gentle skirt (the slab and the ramp, with
the buttress stripes running on down it) and the rest river wall (the dome, and the cliff under
the slab). So the skirt of 0.4.6 to 0.4.11 (lowest 40% of the height, at 30 degrees) is removed.
Where peaks generate and their spacing are unchanged; tops, faces, steps, snow and rock unchanged.

SAMPLER: every peak has one of two feet (`/atlasmountains peaks` lists the three nearest of each):
- F1 no skirt: the faces run straight on down, at their own 49 degrees, to the ground.
- F2 short flare: only the lowest 15% of the height eases out, to about 39 degrees.

Checked on a dev server on his seed, from real generated chunks, same square, 0.4.11 then 0.4.12:
- Peaks cover 73% then 53% of the square. Their own tops and faces: 18% then 28% of the square.
  Gentle ground: 24% then 6%. River wall: 31% then 19%. Grass or trees: 21% then 35%.
- The slab at his screenshot 3 is gone and the ramp at screenshot 4 is a narrow ridge. The dome
  at screenshot 4 is still there: it is river wall, which this build does not touch.
- Risk: the skirt was added (0.4.6) because the hills at a peak's foot looked "not connected to the
  rest of the terrain". More vanilla land shows between peaks again.
- Erosion (the old mod ran a real erosion simulation; this overhaul has none) was proposed as the
  route to realism and is parked.

## 0.4.11 (unreleased): look C+ on every peak

Sammi, from the 0.4.10 sampler in the game: "I think C+ is the best but still not perfect". What
is not perfect is not yet said; this build only makes C+ the look of every peak so he can judge
a whole world of it. Nothing else changes.

- Faces: buttresses (flat panels 46 wide, each set up to 8 blocks in or out, with a sharp gully
  up to 20 deep between each pair). Top: the steeper horn top (top 30% at 58 degrees, 49 below).
- The sampler and the looks passed over (A big spurs, A+, C with the old shallower top) are removed.

Checked on a dev server on his seed, from real generated chunks:
- The C+ peak at 6506 4687 is the same as in the sampler: within 40 blocks of its summit 99.6% of
  columns have the same height (the rest, up to 6 blocks, are where neighbours that had other
  looks overlap it).
- 768-block square at 6420 4760: no snow or ice more than 30 blocks below the snow line; rock on
  peaks andesite 32%, stone 25%, tuff 24%, deepslate 13%, smooth basalt 3%, snow 2.2%; 16.5% of
  the peak area steeper than 65 degrees; no errors in the server log. Generated in 3 min 10 s.

## 0.4.10 (unreleased, SAMPLER BUILD): stepped edges, less regular sides, four looks, a rock line

Sammi and Claude agreed 0.4.9's shape was "a pyramid with decoration" and to work on the outline.
His three reference pictures arrived on 13 Nov 2026 (a row of horn peaks over a lake valley; one
big summit with ridges stepping down from it; a WorldPainter row of sharp, uneven peaks at
sunset). Agreed plan: steps on the edges, ribs to the summit, less regular sides, skirt left
alone. Where peaks generate and their spacing are unchanged.

Shape:
- Steps on the edges. A peak's one or two smaller pieces now stand ON its edges (the lines where
  two faces meet), so an edge drops into a notch, rises to a sub-summit and carries on down. On
  two different edges, or both on one. In 0.4.7 to 0.4.9 they stood in front of the middle of a
  face and read as small pyramids stuck on.
- Less regular sides: each face's steepness varies by a quarter (35 to 50 degrees), was an eighth.
- Ribs nearer the summit: they come in over 15 blocks (was 50), but are never more than half the
  drop of the face they are on. Without that limit one test summit was cut to a needle (64 columns
  across, 12 blocks below its point; 110 with it).
- SAMPLER, four looks (`/atlasmountains peaks` lists the two nearest of each):
  A big spurs; A+ big spurs with a steeper horn top; C buttresses; C+ buttresses with a steeper
  horn top. The horn top (top 30% at 58 degrees, 49 below it) is from the reference pictures; it
  is an option, not the default, because he chose the shallowest profile for 0.4.2. 0.4.9's B
  (very big spurs) is dropped.

Surface:
- Rock line: in mountain country, ground higher than about 100 blocks under the snow line is
  rock, vanilla's own land included. Vanilla hills coming through a peak's face used to keep
  their own surface, a white or green patch high on a rock face.
- No vanilla snow on the valley sides: vanilla's own land in low-erosion country inland is shown
  one erosion band up to the biomes, so it is meadow, forest or taiga by its climate, not snowy
  slopes and grove under peaks that are bare rock up to their snow line. (Frozen climates stay snowy.)
- The temperature the biomes see is taken over a few blocks before the weirdness and erosion, so
  no ring of snowy-slope biome can form round a peak's foot.

Checked on a dev server on his seed (7926463379904956983), from real generated chunks:
- 768-block square at 6420 4760. Snow or ice more than 30 blocks below the snow line: none
  (0.4.9: 4.8% of the square). Grass or trees higher than 92 blocks under the snow line: none.
  Rock on peaks: andesite 31%, stone 24%, tuff 24%, deepslate 14%, smooth basalt 5%, snow 2.7%.
- Separate points above y 180 with a notch of 15+ blocks behind them: 5 (0.4.9: 7). The steps
  have shallower notches than the old face shoulders; that is the cost of putting them on edges.
- Steepness on peaks about as 0.4.9 (16.7% of the peak area steeper than 65 degrees).
- Generating that square took 2 min 46 s (0.4.9: 3 min 14 s).
- Known: a patch of meadow still shows on one face about 100 blocks under the snow line, at the
  rock line's lower edge; the A+ summit is still slim (398 columns across, 24 blocks below its
  point, against 800 to 1,400 for the other looks).
- The test server ran out of memory once, after generating four large squares in a row and then
  five map dumps at once. Rerun on a fresh server without error. Not seen in normal use, not
  investigated further.

## 0.4.9 (unreleased, SAMPLER BUILD): realistic snow, mid-grey rock, and three face looks to choose from

Sammi's in-game look at 0.4.8 (seven screenshots, seed 7926463379904956983): "there's still
something off with them, they're looking okay but a bit weird". What the screenshots showed: a
white cap on every shoulder half-way down a mountain ("white tents"); faces that read as plain
flat pyramids again, the ribs invisible; near-black rock from a fifth of the way up, clashing with
vanilla's light stone beside it; one dead-flat river wall hundreds of blocks wide; and blotchy rock
close up. His reply: "make the snow more realistic for sure and do the other changes, shape needs
work". Where peaks generate and their spacing are unchanged.

Snow (new, PeakSnowCondition.java, SnowLine.java, HornsFunction.snowLine):
- One smooth snow line over a whole range, not a cap on a share of every piece's own height. Each
  main peak wants snow on its top quarter; at any column the line is the average of the nearby
  peaks' wishes, the nearest counting most. Shoulders have no say, so they are only white if they
  reach into their mountain's snow. (A first try, one flat line per range from its average summit,
  left most of a row of peaks he had liked with no snow at all: summits in one range differ by 60
  blocks or more.)
- The snow reaches about 14 blocks lower in the gullies and starts as much higher on the ribs, so
  tongues run down the gullies and dark ribs stand out of the cap's lower edge. The edge wanders
  by about 9 blocks, and for 12 blocks above it the snow is in patches.
- Warm country: the line is 30 blocks higher (a dusting on the tallest summits only). Vanilla's
  coldest band: 12 lower. Vanilla's cold peak biome now starts 25 blocks above the line, where the
  mod's own snow is already complete.

Rock:
- Bands measured down from the snow line: smooth basalt and deepslate in a band about 30 blocks
  deep under the snow, tuff then stone on the body, andesite only about 200 blocks down (in
  proportion on smaller peaks). 0.4.7 was two-thirds light grey, 0.4.8 two-thirds dark; this is
  about a fifth dark, half mid-grey, under a third light.
- Finer mix between rock types (half the roll is per-block, and only the middle 60% of each gap is
  mixed), so no camouflage blotches close up.

Shape (SAMPLER: rib_depth is -1, every peak gets one of three looks; `/atlasmountains peaks`
lists the two nearest good-sized peaks of each with a teleport):
- A big spurs: ridges and gullies 22 blocks deep, 44 apart.
- B very big spurs: 32 deep, 56 apart.
- C buttresses: flat panels 46 wide, each set up to 8 blocks in or out, with a sharp gully up to
  20 deep between each pair.
  (0.4.6 to 0.4.8: 10 deep, 34 apart, which in the game read as one smooth sheet.)
- River walls carry the face's own ribs and gullies (fading out at the river), so a wall is no
  longer one bare slab.

Checked on a dev server on his seed, from real generated chunks, at his seven screenshot positions
(renders sent with the build, beside his screenshots):
- 768-block square at 6420 4760: rock on peaks andesite 29%, stone 27%, tuff 19%, deepslate 14%,
  smooth basalt 7%, snow 4%. Of 7 separate summits above y 200, 6 have snow, reaching 11 to 58
  blocks below the summit; the lowest (y 252) has none.
- Snow cover against the line: under 1% more than 25 blocks below it, 11% in the 10 blocks below,
  50% in the first 12 above, 91% in the next 13, complete above that.
- Trees or grass where a peak stands 12+ blocks above the land: 0.09% of columns. Along river
  channels a peak stands at most 8 blocks above the land.
- The same square generated twice is identical block for block.
- Generating a 640-block square took 2 min 22 s against 2 min 14 s for 0.4.8, run back to back.
- Not rerun on the first test seed. The three looks have only been seen in renders.

## 0.4.8 (unreleased): straight river walls, darker rock, peak biome on the whole peak

Sammi's in-game look at 0.4.7 (six screenshots, "........"): thin fins, rounded towers and domes,
whole mountainsides in light grey, and one peak that was right (dark rock under a ragged cap).
Where peaks generate and their spacing are unchanged.

What was wrong, measured on his seed (-3550591538524093002):
- The fins, towers and domes came from the rule that keeps peaks out of rivers. It limited a peak's
  height by vanilla's weirdness noise, so its surface was lumpy and anywhere from 22 to 71 degrees,
  and it decided the ground on 70% of the peak area in a 384-block square, taking a median 83
  blocks off. 0.4.6 had the same shapes under its snow. (My first guess, vanilla's raised terrain
  ceiling, was wrong: no vanilla land there is above y 203.)
- The light grey came from measuring the rock gradient from the land under each summit, which is
  often 100 blocks above the valley: everything below that was the lightest rock.

Changes:
- River walls. Beside a river a piece of a peak may stand no higher above the land than its real
  distance from the river channel times a slope, so a river runs in a straight-sided V between
  flat walls. The slope is 44 degrees (the peaks' own face angle) unless the piece's summit stands
  too close to a river to be reached at that angle; then it is as steep as that summit needs, up
  to 60 degrees. A first build with 44 degrees everywhere cut the top off 7 of the 9 summits in a
  test square, including the one peak he had liked.
- Rock gradient measured from y 70 (about the valley floors) up to each peak's snow cap, with the
  dark rocks starting lower: andesite and stone only in the bottom fifth or so, then tuff,
  deepslate, smooth basalt.
- Peak biome on the whole of every peak. The erosion the biomes see is now pushed down on peaks
  as weirdness already was. Before, peaks near the coast or in only just low enough erosion were
  plains, forest, meadow or savanna: no snow caps, stray trees, and (found in testing) a savanna
  village built up a rock wall. In warm country a peak's cap is stony peaks, not badlands.

Checked on a dev server on his seed, from real generated chunks, at his screenshot positions:
- The fins and slabs in his screenshots 4, 5 and 6 are gone; the peak in screenshot 2 keeps its
  pointed top and cap. `0.4.7 vs 0.4.8` renders were sent with the build.
- Summits in a 768-block square that do not sit on a river: 9. Cut by more than 3 blocks: 3 in
  0.4.8 (by 29, 41 and 56 blocks; their summits are 30 to 68 blocks from a river), 5 in 0.4.7, 1 in
  0.4.6. Nine more shoulder summits sit on a river channel and are removed in every version.
- Not less steep overall: ground steeper than 65 degrees is 19% of the peak area in both 0.4.7
  and 0.4.8. On wall ground the middle steepness is 64 degrees (0.4.7: 60), the steepest tenth is
  above 72 (0.4.7: 76). The walls are flat now, not gentle.
- Rock on peaks: andesite 1%, stone 9%, tuff 19%, deepslate 32%, smooth basalt 33%, snow 6%
  (0.4.7: 53, 15, 12, 9, 8, 4).
- Biome on peak columns: jagged, frozen or stony peaks on over 99% of one square and 91% of a
  coastal one (the rest is ocean biome under the outer skirts).
- The peak shape away from rivers is identical to 0.4.7 (compared column by column over the square).
- Generating the same two test squares took 3 min 36 s against 3 min 26 s for 0.4.7.

## 0.4.7 (unreleased): the look of Sammi's dark-rock valley screenshot, and more distinct peaks

Sammi on 12 Nov 2026, with a screenshot of the old mod (two big bare rock slopes, dark rock under
ragged snow caps, lighter rock lower down, grass only on the valley floor): "i want the mountains to
look like this ... and add more distinct peaks". Where peaks generate and their spacing are
unchanged. Look D (ribs and shoulders) and the skirt are kept.

Surface (new):
- Bare rock on every peak from foot to summit, shading up each peak's own face: andesite and stone
  at the foot, then tuff, deepslate and smooth basalt just under the snow (the gradient from his
  reference, measured against the height of each peak or shoulder).
- A snow cap on the top 28% of each peak and shoulder in cold and temperate country, with a ragged
  lower edge and a few bare holes. In warm country only a patchy dusting on the top 6%.
- Below its cap a peak's biome is vanilla's stony peaks whatever the climate, so no snow layers, ice
  sheets or trees there. Peak biomes start 4 blocks above the land (was 35), so the dirt-and-trees
  collar round each foot is gone. Vanilla's own crest land between the peaks gets the same bare-rock
  biome, so its snow and blue ice sheets are gone too.
- A river that cuts a peak runs through a gentler V (about 38 degrees, was about 55), so no huge smooth wall.

Shape:
- More distinct peaks: two or three shoulder peaks per peak (was one or two), each standing out in
  front of the middle of one of the main peak's faces, 0.55 to 0.85 of its height away, 0.65 of its
  height give or take a quarter. In 0.4.6 they stood closer in and in any direction and were mostly
  swallowed by the main peak.
- Pointier summits: the top 18% of each peak is 1.5 times as steep as the face (about 55 degrees).

Checked on a dev server (seed 12345), from real generated chunks:
- Peak field alone, 640-block square at x -800 z -100: 11 summits with 15+ blocks of prominence
  (0.4.6: 7), 8 with 30+ (0.4.6: 5). Allowing up to four shoulders gave no more: the peaks already
  cover 86% of that square, so extra shoulders are buried in neighbouring peaks.
- Snow or ice on the surface: 2.7% of that square (0.4.6: 25%). In a cold range (x 5450 z 5600) the
  cap covers the top 30 to 35 blocks of a 116-block peak, with bare rock below.
- Grass is down to 9% of that square (0.4.6: 28%): the skirts between peaks are bare rock now.

Debug: `/atlasmountains snapshot <size>` generates a square round the player and writes each
column's top block and height; `tools/render_snapshot.py` draws it as a map or a view, lit as
Minecraft lights blocks.

## 0.4.6 (unreleased): look D, and peaks that run down to the ground

- Sammi picked look D from the sampler: strong ribs down each face (10 blocks deep, about 34
  apart) and one or two shoulders. The sampler itself is removed.
- The skirt: the lowest 40% of a peak's height carries on down at 0.62 of the face's steepness
  until it meets the ground. Before, a peak stopped at a line part-way down and sat on a collar
  of vanilla hills ("the bottom hill terrain ... not connected to the rest of the terrain").
- Peaks are looked for three cells each way (was two), since a skirt reaches further.
- `/atlasmountains peaks` now lists the nearest peaks with a teleport to each.

## 0.4.5 (unreleased): sampler build, four looks in one world

Sammi on 0.4.4: "a bit too perfectly pyramidal", then ": /" at two screenshots where a peak
read as a tent. The shape had been swung from one extreme to the other four times in a day on
my guesses, and my renders could not show the differences that matter, so this build puts four
looks in one world for him to compare in the game itself:

- A plain pyramid (0.4.4), B light ribs, C strong ribs, D strong ribs and one or two shoulders.
  Every peak is given one of the four at random.
- Ribs: straight ridges and gullies running down each flat face, side by side, with sharp
  creases; they fade in below the summit.
- Shoulders: one or two smaller pyramids joined to the side of a peak.
- `/atlasmountains peaks` lists the nearest peak of each look with a teleport command.
- Temporary: `rib_depth` below zero switches the sampler on. To be replaced by the chosen look.

## 0.4.4 (unreleased): true pyramids with big flat sides

Sammi on 0.4.3: "they all seem rounded", "I want big flat side".

- Each peak is now 3 or 4 flat planes meeting at a point. In 0.4.3 the faces were made by
  bending a cone's angle, and my uneven spacing of the edges curved every face.
- Each peak is a solid of its own, built up from the land's level under its summit. Before, it
  was added on top of the land column by column, so every bump in the land showed on its faces.
- One face slope (44 degrees, give or take 12% face by face) from summit to foot; no extra
  steepening at the top, no gullies on the faces.
- Where a river meets a peak it runs through a V-shaped valley. (An interim version pressed the
  ground flat along the channel, which left a wall.)
- Where peaks generate and their spacing are unchanged.

## 0.4.3 (unreleased): pyramids that keep their angles

Sammi in the game on 0.4.2: "these aren't looking very angular, very smooth". Three causes, all
fixed here, and one over-correction on the way that he caught ("more star like than pyramidal").

- Peaks are no longer squashed. Each peak now reads the strength of mountain country once, at
  its summit, and is that fraction of its full height with its angles unchanged. Before, every
  column was multiplied by the local strength, which flattened most peaks into rounded humps
  (only 36% of peak ground was at full shape on his seed) and the clean cut then smoothed them.
- A peak that would be shorter than 60 blocks is left out.
- Pyramids: 3 or 4 fully flat sides (was 3 to 5, three-quarters flat). Five-sided peaks have
  edges too shallow to see and read as cones.
- Far fewer lines down each face (gully 0.03, was 0.12), and straighter, unevenly spaced edges.
- Where peaks generate and how far apart they are is unchanged, at Sammi's request.
- Tried and removed: ridges with V gullies between them and finer ribs on the faces. From above
  each peak was a pleated star.

## 0.4.2 (unreleased): shallower peaks

- Sammi chose the shallowest of three rendered options: 48 degrees at the summit easing to 35
  (0.4.1: 54 to 42). Summit heights are unchanged, so each peak is wider and neighbours meet
  higher up.

## 0.4.1 (unreleased): clean, flat-faced peaks

After Sammi's first look in-game (12 Nov 2026): the row of separate peaks seen from a distance was
right, but close up the summits were torn into shards, holes and blade-thin walls. He asked for
the even, angular slopes of the old mod on the new separate peaks, each peak straight-sided with a
sharp top.

- Clean faces: where a peak stands clear of the land, the ground is cut exactly at the peak's own
  height, block by block, with no 3-D roughness, and is solid for 12 blocks down. On a test peak
  99.1% of columns sit exactly at the intended height.
- Factor is vanilla's again. Raising it to 16 had let caves come within 3 blocks of the surface,
  which is where the cave mouths in the faces came from.
- Flat faces: between two ridges the face is now mostly flat, like the side of a pyramid, where
  it was a scooped hollow (`facet` 0.75).
- Sturdier: nearly straight sides, 54 degrees at the summit easing to 42 (was 62 to 39), and
  ridge lines wander less.
- Debug commands `dfmap` and `biomemap` write maps of a density function or of the biomes.

## 0.4.0 (unreleased): restart

- Started again from scratch (Sammi, 11 Nov 2026). The custom mountain generator, the continents,
  the mountain biomes, the world type, the debug commands and their tests are all removed; the
  world is vanilla again.
- New approach: work through vanilla's own `offset`, `factor` and `jaggedness` density functions.
  Two attempts at adjusting them (ridge lines, filled rings, wider spikes) moved the ground only
  about 4 blocks on a 250-block range, so they were dropped.
- Horn peaks, after Sammi's WorldPainter references: in low-erosion country away from the coast
  a field of separate pyramid peaks is added to the land
  (`atlasmountains:horns`): steep summits, flaring skirts, 3 to 5 ridges per peak, varied heights.
  River valleys are kept.
- The upper part of each peak takes vanilla's peak biome for its climate, so summits are rock,
  snow or ice, not forest.
- Vanilla's land is left at its own height under the peaks (a first build pressed it down to
  y 110). To make room the overworld is taller: build height y 320 to y 512, terrain ceiling
  y 256 to y 472-488.
- Debug commands: `mountains`, `heightmap`, `df`. Shape prototype and viewer under `tools/`.

## 0.3.0 (2026-11-10)

Minecraft 26.3.

- Ported to Minecraft 26.3, Fabric Loader 0.20.1, Fabric API 0.165.2 and Java 25.
- Mountains now fill vanilla's own mountain footprint: a network of ridges meeting at junctions,
  with small enclosed valleys where ridges part.
- Smaller, rougher mountain provinces; biomes elsewhere at twice vanilla size.
- Mountains about 20% lower (design summit y 232), with summit heights varying between massifs.
- Snow starts at a climate-dependent snow line instead of a hard biome edge.
- Province edges blend into vanilla terrain by height, so there are no walls, flooded rings or
  sunken plates at the edge.
- Faster region generation.
- Removed the older layout experiments (they are on the `archive/layout-experiments` branch).

## 0.2.0 (2026-11-08)

Minecraft 1.21.1.

- Continents from a large fractal noise: big organic landmasses and islands, spawn on land.
- Branching mountain chains placed inside mountain provinces, with foothills, dominant summits and
  taller peaks at junctions.
- Treeless biomes above the treeline; vanilla's own peaks removed.

## 0.1.0

Minecraft 1.21.1. First prototype: parallel horn-peak ranges over the whole world.
