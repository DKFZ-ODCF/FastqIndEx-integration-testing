#!/bin/bash

set -uvx

declare mode="$1"
declare listOfFiles="$2"
declare baseOutputFolder="$3"
declare pathPrefix="$4"

[[ -z "$listOfFiles" ]] && echo "List of FASTQ files is empty. You need to call the masstest with [file with a list of fastq files] and [base output folder]." && exit 1

[[ -z "$baseOutputFolder" ]] && echo "There was no output folder present. You need to call the masstest with [file with a list of fastq files] and [base output folder]. " && exit 2

[[ ! -f "$listOfFiles" ]] && echo "File does not exist" && exit 3

[[ ! -d "$baseOutputFolder" ]] && echo "Base output folder does not exist, please create it." && exit 4

for f in `cat $listOfFiles`; do
	f=$(readlink -f ${pathPrefix}${f})
	[[ -z "$f" ]] && continue
	[[ ! -r "$f" ]] && continue
	
	declare targetDirectory="$baseOutputFolder"/"$f"
	echo $targetDirectory
	mkdir -p "$targetDirectory"

    if [[ "$mode" == "LSF" ]]; then
    	echo "groovy ~/Projekte/FQIndexerChunkTest/src/Test.groovy $f $targetDirectory" | bsub -W 4800 -n 8 -R "span[hosts=1]" -cwd $HOME -J FQIndExTest -R "rusage[mem=288]" -M 288
    elif [[ "$mode" == "LOCAL" ]]; then
        groovy ~/Projekte/FQIndexerChunkTest/src/Test.groovy $f $targetDirectory
    fi
done
