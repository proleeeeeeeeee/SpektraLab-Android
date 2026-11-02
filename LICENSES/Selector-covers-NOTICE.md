# Film selector cover provenance

The original four selector cutouts in `app/src/main/res/drawable-nodpi/` are byte-for-byte copies of the transparent cutouts in the existing SpektraLab Android design prototype (`assets/covers/*-cutout.png`). No new images were generated for this fix.

Original product photographs recorded by that design:

- Kodak Gold 200: CameraWorld, https://www.cameraworld.co.uk/kodak-eastman-gold-200-36-colour-neg.html
- Kodak Portra 400: Safelight Berlin, https://safelightberlin.com/products/kodak-portra-400-35mm
- CineStill 800T: Khrome, https://khrome.de/en/products/cinestill-800t-tungsten-135-36
- Kodak Tri-X 400: Kodak, https://www.kodak.com/en/still-film/home/

The prototype used imagegen to extract transparent backgrounds while retaining packaging. Those packaging photographs and trademarks retain their owners' rights; this notice does not relicense them as GPL artwork or establish public redistribution rights. They are reused from that existing design to identify the simulated film choices. The previous prototype README and image-edit-notes.md retain the source/edit history.

Paper choices use bounded live samples of the user's current photograph rendered with each compatible paper. No private sample photograph is bundled. Frame choice illustrations remain original vector drawings.

The Portra 160 and Portra 800 product photographs are copied from the pinned upstream FilmCovers resource directory without pixel changes. Their upstream `.jpg` filenames contain PNG bytes; the Android copies use the correct `.png` extension. The other newly exposed films use correctly named text profile cards because no verified packaging photograph is bundled. A profile card does not claim to reproduce manufacturer packaging.
