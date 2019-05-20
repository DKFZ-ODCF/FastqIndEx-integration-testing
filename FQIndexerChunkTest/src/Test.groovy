import groovy.transform.CompileStatic

@CompileStatic
class Test {
    boolean keepLargeOutputfiles = false
    List<Integer> chunksList = [4, 8, 12, 16, 24, 32, 48]

    File fastqfile = new File("/data/michael/temp/test.fastq.gz")
    File outputBase = new File("/data/michael/temp/")
    File indexfile = new File("/data/michael/temp/test.fastq.fqi_file")
    File indexpipefile = new File("/data/michael/temp/test.fastq.fqi_pipe")
    File namedPipe1 = new File("/data/michael/temp/test.fastq.fifo1")
    File namedPipe2 = new File("/data/michael/temp/test.fastq.fifo2")
    File namedPipe3 = new File("/data/michael/temp/test.fastq.fifo3")

    File md5file = new File(fastqfile.absolutePath + "_md5sum")
    File linecountfile = new File(fastqfile.absolutePath + "_linecount")

    List<Thread> listOfStartedThreads = [].asSynchronized()
    List<File> chunkMD5Files = [].asSynchronized()
    List<File> linecountFiles = [].asSynchronized()

    static int exec(String command) {
        println "Running command: ${command}"
        def proc = (["bash", "-c", "${command}"]).execute()
        int res = proc.waitFor()
        println proc.errorStream.text
        return res
    }

    Thread run(String threadID, String command, File file) {
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

    static void main(String[] args) {
        new Test().run(args)
    }


    List<Thread> waitForThreads() {
        listOfStartedThreads.findAll().each { Thread t -> t.join() }
        listOfStartedThreads.clear()

    }

    void run(String[] args) {
        if (!checkAndPrepareFilenames(args)) {
            System.exit(1)
        }

        if (!prepareIndexAndMetafiles()) {
            println "Something went wrong when preparing files. Exitting."
            System.exit(2)
        }

        runTests()

        if (!compareMD5Files()) {
            println "MD5 files or Linecount files differ, please check the job or process output! Test failed."
            System.exit(3)
        }
        println "Finished"
    }

    boolean checkAndPrepareFilenames(String[] args) {
        if (args.length >= 2) {
            // Reset everything to proper location. Otherwise it is in debug mode.
            fastqfile = new File(args[0])
            outputBase = new File(args[1])
            if (!fastqfile.exists()) {
                println "FASTQ file does not exist"
                return false
            }
            if (!outputBase.exists()) {
                println "Output directory '${outputBase}' does not exist, please create"
                return false
            }
            indexfile = new File(outputBase, fastqfile.name + ".fqi_file")
            indexpipefile = new File(outputBase, fastqfile.name + ".fqi_pipe")
            namedPipe1 = new File(outputBase, fastqfile.name + ".np1")
            namedPipe2 = new File(outputBase, fastqfile.name + ".np2")
            namedPipe3 = new File(outputBase, fastqfile.name + ".np3")
            md5file = new File(outputBase, fastqfile.name + ".md5")
            linecountfile = new File(outputBase, fastqfile.name + ".lc")
            println "Working with the following files and pipes:" +
                    "\n\t${fastqfile}" +
                    "\n\t${indexfile}" +
                    "\n\t${indexpipefile}" +
                    "\n\t${md5file}" +
                    "\n\t${linecountfile}" +
                    "\n\t${namedPipe1}" +
                    "\n\t${namedPipe2}" +
                    "\n\t${namedPipe3}"

            if (args.length >= 3) {
                println "A list of chunk counts is provided, try to use this"
                try {
                    chunksList = args[2].split("[,]").collect { String val -> val.toInteger() } as List<Integer>
                    println "The chunk counts list is now: ${chunksList}"
                } catch (NumberFormatException) {
                    println "The provided chunk counts list ${args[2]} is invalid. It needs to consist of comma separated integer values."
                    return false
                }
            }
            if (args.length >= 4) {
                try {
                    keepLargeOutputfiles = args[3].toBoolean()
                    if (keepLargeOutputfiles) println "Will keep large output files for this run, keepLargeOutputfiles is set to true."
                } catch (Exception ex) {
                    println "Could not parse boolean state for keep large output files, needs to be true or false (default)."
                    return false
                }
            }
        } else if (args.length == 0) {
            println "Debug mode active."
        } else {
            println "Can't run. Eiter don't supplement parameters for debug mode or call like:\n\t" +
                    "groovy fqitest.groovy [FASTQ file] [target directory]"
            return false
        }
        return true
    }

    boolean prepareIndexAndMetafiles() {
// Create named pipe for shared input.
        if (!namedPipe1.exists()) {
            exec("mkfifo '${namedPipe1}' '${namedPipe2}' '${namedPipe3}'")
        }

        if (!md5file.exists() || !linecountfile.exists() || !indexpipefile.exists()) {
            // To make sure that everything runs now:
            if (linecountfile.exists()) linecountfile.delete()
            if (indexpipefile.exists()) indexpipefile.delete()

            listOfStartedThreads << run("md5", "cat '${namedPipe1}' | gunzip -c | md5sum > '${md5file}'", md5file)
            listOfStartedThreads << run("linecount", "cat '${namedPipe2}' | gunzip -c | wc -l > '${linecountfile}'", linecountfile)
            listOfStartedThreads << run("pipedIndex", "cat '${namedPipe3}' | fastqindex index -w -f=- -i='${indexpipefile}' -b=128", indexpipefile)
            if (listOfStartedThreads) {
                listOfStartedThreads << Thread.start {
                    exec("cat '${fastqfile}' | tee '${namedPipe1}' '${namedPipe2}' > '${namedPipe3}'")
                }
            }
        } else {
            println "md5sum, linecount and piped index file already exist."
        }

        listOfStartedThreads << run("index", "fastqindex index -f='${fastqfile}' -i='${indexfile}' -b=128", indexfile)

        waitForThreads()

        if (md5file.size() == 0 || linecountfile.size() == 0 || indexfile.size() == 0 || indexfile.size() != indexpipefile.size()) {
            println "Check file sizes, something went wrong."
            return false
        }
        return true
    }

    void runTests() {
        println "Run tests with different chunks: ${chunksList}"
        long noOfRecords = (long) (linecountfile.text.toLong() / 4)

        for (int chunks in chunksList) {
            println "Testing with ${chunks} chunks"
            long modulo = noOfRecords % chunks
            long chunksize = (long) (noOfRecords / chunks) as long
            println "Caluclated a chunk size of ${chunksize} with a modulo of ${modulo} resulting in a total of ${chunksize * chunks + modulo} entries compared to ${noOfRecords}"
            List<File> chunkFilesByFile = createChunkFiles(indexfile, "file", chunks, chunksize, modulo)
            waitForThreads()
            List<File> chunkFilesByPipe = createChunkFiles(indexpipefile, "pipe", chunks, chunksize, modulo)
            waitForThreads()

            // Join chunks, calc md5, count lines
            performChunkFileTests(chunkFilesByFile, chunks, "file")
            waitForThreads()
            performChunkFileTests(chunkFilesByPipe, chunks, "pipe")
            waitForThreads()
        }
        println "Finished"
    }

    List<File> createChunkFiles(File index, String outSuffix, long chunks, long chunksize, long modulo) {
        List<File> chunkFiles = []
        File chunkDir = new File(outputBase, "chunk_${chunks.toString().padLeft(3, "0")}_${outSuffix}")
        if (!keepLargeOutputfiles) {
            chunkDir.deleteDir()
        }
        if (!chunkDir.exists())
            chunkDir.mkdir()
        for (int i = 0; i < chunks; i++) {
            chunkFiles << new File(chunkDir, "chunk_${chunks.toString().padLeft(3, "0")}_${i.toString().padLeft(3, "0")}.fastq_${outSuffix}")
            long count = chunksize
            if (i == chunks - 1) // last chunk! Add modulo
                count += modulo
            if (!chunkFiles[-1].exists()) { // Only create, if necessary
                def cmdByFile = "fastqindex extract -f='${fastqfile}' -i='${index}' -s=${i * chunksize} -n=${count} -o='${chunkFiles[-1]}'"
                listOfStartedThreads << run(chunkFiles[-1].name, cmdByFile, chunkFiles[-1])
            }
        }
        return chunkFiles
    }

    void performChunkFileTests(List<File> chunkFiles, long chunks, String suffix) {
        File chunkDir = new File(outputBase, "chunk_${chunks.toString().padLeft(3, "0")}_${suffix}")
        File mergedFile = new File(chunkDir, "chunks_merged.txt")
        listOfStartedThreads << Thread.start {
            File npLinecount = new File(chunkDir, "npLinecount")
            File npMD5sum = new File(chunkDir, "npMD5sum")
            File mergedMD5sumFile = new File("${mergedFile}_md5sum")
            File lineCountfile = new File("${mergedFile}_linecount")

            chunkMD5Files << mergedMD5sumFile
            linecountFiles << lineCountfile

            exec("mkfifo '${npLinecount}' '${npMD5sum}'")
            Thread lineCount = Thread.start {
                exec("cat '${npLinecount}' | wc -l > '${lineCountfile}'")
            }
            Thread md5sum = Thread.start {
                exec("cat '${npMD5sum}' | md5sum > '${mergedMD5sumFile}'")
            }
            Thread chunkThread = Thread.start {
                exec("cat '${chunkFiles.join("' '")}' | tee ${npLinecount} ${npMD5sum} > ${mergedFile}")
            }
            lineCount.join()
            md5sum.join()
            chunkThread.join()
            if (!keepLargeOutputfiles) {
                mergedFile.delete()
                chunkFiles.each { File file -> file.delete() }
            }
        }
    }

    boolean compareMD5Files() {
        String reference = md5file.text
        boolean md5Equals = true
        chunkMD5Files.each {
            md5Equals &= reference == it.text
        }

        String lcRef = linecountfile.text
        boolean lcEquals = true
        linecountFiles.each {
            lcEquals &= lcRef == it.text
        }
        return md5Equals && lcEquals
    }
}
