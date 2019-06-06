#!/bin/bash

set -uvx

declare listOfFiles="$1"
declare baseOutputFolder="$2"
declare pathPrefix="$3"

[[ -z "$listOfFiles" ]] && echo "List of FASTQ files is empty. You need to call the masstest with [file with a list of fastq files] and [base output folder]." && exit 1

[[ -z "$baseOutputFolder" ]] && echo "There was no output folder present. You need to call the masstest with [file with a list of fastq files] and [base output folder]. " && exit 2

[[ ! -f "$listOfFiles" ]] && echo "File does not exist" && exit 3

[[ ! -d "$baseOutputFolder" ]] && echo "Base output folder does not exist, please create it." && exit 4

for f in `cat $listOfFiles`; do
	f=$(readlink -f ${pathPrefix}${f})
	[[ -z "$f" ]] && continue
	[[ ! -r "$f" ]] && continue
	
	declare targetDirectory="$2"/"$f"
	echo $targetDirectory
	mkdir -p "$targetDirectory"

	echo "groovy ~/Projekte/FQIndexerChunkTest/src/Test.groovy $f $targetDirectory" | bsub -W 4800 -n 8 -R "span[hosts=1]" -cwd $HOME -J FQIndExTest -R "rusage[mem=288]" -M 288
done
