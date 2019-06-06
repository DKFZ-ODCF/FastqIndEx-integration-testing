package de.dkfz.odcf.fqindexer

import groovy.transform.CompileStatic
import org.apache.commons.io.filefilter.WildcardFileFilter

import static Methods.exec

@CompileStatic
class ConsistencyChecks {

    /**
     * Run a test over a range of result directories. The directories are collected from the filesystem first. Just
     * pass in a base directory with result directories.
     * @param args
     */
    static void main(String[] args) {
        File _baseDir = new File(args[0])
        println("Checking $_baseDir")
        IOHelpers.recursivelyGetFilesFromDir(_baseDir, "*.md5", "*chunk*")
                .collect { it.parentFile }
                .each { new ConsistencyChecks(it).check() }
    }

    File baseDir

    ConsistencyChecks(File baseDir) {
        this.baseDir = baseDir
    }

    boolean check() {
        println "Perform consistency checks for '${baseDir}'"

        return retrieveAndCompare("md5") & retrieveAndCompare("lc")
    }

    boolean checkSize(File file) {
        if (file.size() == 0) {
            println "\tA file has a size of zero. This means, that something went wrong during the test: '${file}'"
            return false
        }
        return true
    }

    boolean retrieveAndCompare(String ending) {
        File ref = IOHelpers.getFileFromDir(baseDir, "*.${ending}")
        List<File> chunkFiles = IOHelpers.recursivelyGetFilesFromDir(baseDir, "*.${ending}")
        chunkFiles.remove(ref)

        boolean result = true
        if (chunkFiles.size() != 14) {
            println "\tThere were less than 14 chunk specific '${ending}' files in the directory. Please check, if this is correct. This might lead to a faulty consistency checks."
            result = false
        }
        if (!Methods.compareFiles(ref, chunkFiles)) {
            println "\t${ending} files differed. If all result files are equal, the source FASTQ might contain or miss a newline at the end of file."
            result = false
        }

        result &= checkSize(ref)
        chunkFiles.each { result &= checkSize(it) }
        return result
    }
}
