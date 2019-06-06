package de.dkfz.odcf.fqindexer

import groovy.transform.CompileStatic

@CompileStatic
class Methods {

    static int exec(String command, List<String> outlines = null) {
        println "Running command: ${command}"
        def proc = (["bash", "-c", "${command}"]).execute()
        int res = proc.waitFor()
        if (outlines != null)
            outlines.addAll(proc.inputStream.readLines())
        println proc.errorStream.text
        return res
    }

    static Thread run(String threadID, String command, File file) {
        if (!file.exists()) {
            return Thread.start {
                println "Started ${threadID} Thread: ${command}"
                int res = exec(command)
                if (res != 0) {
                    println("${threadID} Thread failed with ${res}. Deleting result file.")
                    file.delete()
                } else {
                    println "Thread ${threadID} finished"
                }
            }
        } else {
            println "File ${file} already existed. Won't run Thread ${threadID}."
        }
    }

    static boolean compareStatusFiles(File md5file, List<File> chunkMD5Files, File linecountfile, List<File> linecountFiles) {
        boolean md5Equals = compareFiles(md5file, chunkMD5Files)
        boolean lcEquals = compareFiles(linecountfile, linecountFiles)

        return md5Equals && lcEquals
    }

    static boolean compareFiles(File ref, List<File> chunks) {
        String reference = ref.text
        boolean md5Equals = true
        chunks.each {
            boolean result = reference == it.text
            if(!result)
                println "\t\t${reference.trim()} != ${it.text.trim()}"
            md5Equals &= result
        }
        md5Equals
    }
}
