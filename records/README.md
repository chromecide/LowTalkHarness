# Walk records

One file per completed walk, copied out of the server's data folder and committed here.

A record in this folder is a claim about a specific jar: the file name carries the Hytale version, the LowTalk
version and the content hash of the jar that was walked, and every result inside it carries the same hash. The
harness refuses to let a result from another build count towards a release, so a record here with no carried
results is evidence that a whole tree was walked against one artefact rather than assembled from several.

`run-*.json` in the server's folder is the live one and moves as testing happens. These do not.
