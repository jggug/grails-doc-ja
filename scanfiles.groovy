import static groovyx.gpars.GParsPool.runForkJoin
import static groovyx.gpars.GParsPool.withPool

def srcDir = new File('./grails-doc/src/en/guide')
def langSrcDir = new File('./src/ja/guide')

def result
withPool() {
    result = runForkJoin(srcDir){files ->
        long count = 0
        def done = []
        def todo = []
        def notfound = []
        def updated =[]
        files.eachFile {file->
            if (file.isDirectory()) {
                forkOffChild(file)
            } else {
                def gdocFile = file.path.replace( srcDir.path ,'')
                def target = new File(langSrcDir,gdocFile)
                if(target.exists()){
                    def text = target.text
                    if(text =~ /.*\{hidden\}.*/){
                        int linenum=1
                        def buf = []
                        file.eachLine{line->
                            if(!text.contains(line) && !(line =~ /^h\d\./)){
                                buf << "${linenum.toString().padRight(3)}: ${line}"
                            }
                            linenum++
                        }
                        done << [src:target,updated:buf]
                    }else{
                        if(text.length()>0){
                            todo << file
                            if(text != file.text){
                                updated << file
                            }
                        }else{
                            notfound << file
                        }
                    }
                }else{
                    notfound << target
                }
                count++
            }
        }

        return [count:count+childrenResults.count.sum(0),
                done:done+childrenResults.done.flatten(),
                todo:todo+childrenResults.todo.flatten(),
                notfound:notfound+childrenResults.notfound.flatten(),
                updated:updated+childrenResults.updated.flatten()]
    }
}

def escape = {
    it = it.replaceAll('!','\\\\!')
    it = it.replaceAll('\\{','\\\\{')
    it = it.replaceAll('\\}','\\\\}')
    return it
}

StringBuffer report = new StringBuffer()
report << """
h1. Document Translation Report.

* *File Count* - ${result.count}
* *Done* - ${result.done.size()}
* *TODO* - ${result.todo.size()}
* *Not found or new* - ${result.notfound.size()}
"""

report << '\nh2. Original document updated after translation.:\n'
result.done.sort().each{
    if(it.updated.size()>0){
        report << "\n*${it.src}*\n\n{code}\n"
        it.updated.each{line->
            report << "${escape(line)}\n"
        }
        report << "{code}\n"
    }
}

report << '\nh2. Not done.\n\n'
result.todo.sort().each{
    def isUpdated = result.updated.contains(it)?' *(original file updated)* ':''
    report << "* ${isUpdated}${it}\n"
}

report << '\nh2. File not found (new file added) or empty file\n\n'
result.notfound.sort().each{
    report << "* ${it}\n"
}

println report.toString()
