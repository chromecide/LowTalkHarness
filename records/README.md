# Walk records

One file per completed walk, copied out of the server's data folder and committed here.

A record in this folder is a claim about a specific jar: the file name carries the Hytale version, the LowTalk
version and the content hash of the jar that was walked, and every result inside it carries the same hash. The
harness refuses to let a result from another build count towards a release, so a record here with no carried
results is evidence that a whole tree was walked against one artefact rather than assembled from several.

`run-*.json` in the server's folder is the live one and moves as testing happens. These do not.

## What the hash covers

The code, and not the version string. It is a SHA-256 over the jar's entries, sorted by name, with the
manifest's `Version` value blanked before hashing. A changed class, a changed shipped asset, a changed
server-version range or a changed dependency all move it. Bumping 0.4.0 to 0.5.0 does not.

That distinction was learned the hard way and is worth keeping written down. The hash used to cover the whole
jar, manifest included, so the 0.5.0 bump moved the id and the gate discounted fifty-six results — against a
jar that differed from the walked one in exactly one of its 412 entries, `manifest.json`, and in exactly one
line of that entry:

```
LowTalk-0.4.0.jar  manifest.json  CRC d331ba3d      "Version": "0.4.0",
LowTalk-0.5.0.jar  manifest.json  CRC 468e1b1f      "Version": "0.5.0",
```

All 411 other entries were CRC-identical. The walked jar was rebuilt from its commit to make that comparison
and reproduced byte-for-byte, so the builds are reproducible and anyone can check it.

**One record carries ids that were recomputed rather than observed.** `0.7.0-LowTalk-0.5.0-b6ad2eca.json` was
walked on the 0.4.0 jar, whose stamps read `0.4.0 (1c63e59c)` under the old whole-jar hash. Changing how the
hash is computed moves the id for every past record, so those stamps were recomputed to `b6ad2eca`, which is
what that same jar hashes to now. The version was left at `0.4.0`, because that is what was walked. Nothing
about what happened was changed — only the function used to name the artefact it happened to.
