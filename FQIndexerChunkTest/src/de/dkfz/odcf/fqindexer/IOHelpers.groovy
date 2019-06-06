package de.dkfz.odcf.fqindexer

import groovy.transform.CompileStatic
import org.apache.commons.io.filefilter.WildcardFileFilter

@CompileStatic
class IOHelpers {

    static List<File> recursivelyGetFilesFromDir(File _baseDir, String wildcard = "", String exclude = "") {
        List<File> _resultFiles = []
        _baseDir.eachFileRecurse {
            File f ->
                if ((wildcard && new WildcardFileFilter(wildcard).accept(f)) && !(exclude && new WildcardFileFilter(exclude).accept(f)))
                    _resultFiles << f
        }
        _resultFiles
    }

    static File getFileFromDir(File baseDir, String wildcard) {
        baseDir.listFiles().find { new WildcardFileFilter(wildcard).accept(it) }
    }
}
