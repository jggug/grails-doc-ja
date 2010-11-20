
def langProp = new ConfigSlurper().parse(new File("./src/ja.groovy").toURL())
def titles = langProp.titles

def docFiles  = new File('original/grails-doc/src/guide')
docFiles.eachFile{
    def name = it.name.replaceAll(/\.gdoc/,'')
    if(!titles[name]){
        println "'${name}':'${name}',"
    }
}

