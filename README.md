# FastqIndEx-integration-testing

Test script(s) to perform integration tests for the FastqIndEx'er

## FQIndexerChunkTest

This script can e.g. be run on a cluster environment. 
It will:
- Take an existing FASTQ and create index files (piped and by file) and calculate its md5sum.
- Use extract to create 4, 8, 16, ..., 48 chunks of the FASTQ. Join them and calculate the new md5sum.
  This is done both with the "pipe" and the "file" index.
- If all md5sums match, everything is fine.

Note, that these tests can take, depending on the FASTQ, quite a while!
