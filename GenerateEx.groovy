import org.grails.doc.DocEngine
import org.grails.doc.PdfBuilder

import org.radeox.api.engine.*
import org.radeox.engine.context.BaseInitialRenderContext

def ant = new AntBuilder()

BASEDIR = System.getProperty("base.dir") ?: '.'
GRAILS_HOME = System.getProperty("grails.home")
CONTEXT_PATH = DocEngine.CONTEXT_PATH
SOURCE_FILE = DocEngine.SOURCE_FILE
LANG = System.getProperty("doc.lang")

props = new Properties()
new File("${BASEDIR}/resources/doc.properties").withInputStream {input ->
    props.load(input)
}
new File("${GRAILS_HOME}/build.properties").withInputStream {input ->
    props.load(input)
}

def lang = LANG

langProp = new ConfigSlurper().parse(new File("./src/${lang}/titles.groovy").getText('UTF-8'))
title = props.title
version = props."grails.version"
authors = props.author

def compare = [equals: { false },
               compare: {o1, o2 ->
    def idx1 = o1.name[0..o1.name.indexOf(' ') - 1]
    def idx2 = o2.name[0..o2.name.indexOf(' ') - 1]
    def nums1 = idx1.split(/\./).findAll { it.trim() != ''}*.toInteger()
    def nums2 = idx2.split(/\./).findAll { it.trim() != ''}*.toInteger()
    // pad out with zeros to ensure accurate comparison
    while (nums1.size() < nums2.size()) {
        nums1 << 0
    }
    while (nums2.size() < nums1.size()) {
        nums2 << 0
    }
    def result = 0
    for (i in 0..<nums1.size()) {
        result = nums1[i].compareTo(nums2[i])
        if (result != 0) break
    }
    result
}] as Comparator

files = new File("${BASEDIR}/src/guide").listFiles().findAll { it.name.endsWith(".gdoc") }.sort(compare)
files_i18n = new File("./src/${lang}/guide").listFiles().findAll { it.name.endsWith(".gdoc") }.sort(compare)
context = new BaseInitialRenderContext()
context.set(CONTEXT_PATH, "..")
context.setParameters([:]) // required by some macros

ant = new AntBuilder()
cache = [:]

engine = new DocEngine(context)
templateEngine = new groovy.text.SimpleTemplateEngine()
context.setRenderEngine(engine)


/* Guide documentation */

book = [:]
for (f in files) {
    def chapter = f.name[0..-6]
    book[chapter] = f
}
/* doc files from the $lang dir */
for (f in files_i18n) {
    def chapter = f.name[0..-6]
    book[chapter] = f
    println f
}



chaptersOnlyToc = new StringBuffer()
fullToc = new StringBuffer()
toc = new StringBuffer()
soloToc = new StringBuffer()
fullContents = new StringBuffer()
chapterContents = new StringBuffer()
chapterTitle = null
chapterHeader = null
chapterToc = new StringBuffer()

void writeChapter() {
    new File("./output/guide/${chapterTitle}.html").withWriter("UTF-8") {
        template.make(title:chapterTitle,
                      header:chapterHeader,
                      toc:chapterToc.toString(),
                      content:chapterContents.toString(),
                      path:"..").writeTo(it)
    }
    chapterToc.delete(0,chapterToc.size()) // clear buffer
    chapterContents.delete(0,chapterContents.size()) // clear buffer

}

ant.mkdir(dir: "./output/guide")
ant.mkdir(dir: "./output/guide/pages")

// Deletion {original} tag @fumokmm
def deleteOriginal(contents) {
  contents.replaceAll(/(?s)\{original\}.*?\{original\}/, '')
}

new File("${BASEDIR}/resources/style/guideItem.html").withReader("UTF-8") {reader ->
    template = templateEngine.createTemplate(reader)

    for (entry in book) {
        //println "Generating documentation for $entry.key"
        def titleView = langProp.titles[entry.key]?:entry.key
        def title = entry.key
        def level = 0
        def matcher = (title =~ /^(\S+?)\.? /) // drops last '.' of "xx.yy. "
        if (matcher.find()) {
            level = matcher.group(1).split(/\./).size() - 1
        }
        def margin = level * 10

        // level 0=h1, (1..n)=h2
        def hLevel = level==0 ? 1 : 2
        //def header = "<h$hLevel><a name=\"${title}\">${title}</a></h$hLevel>"
        def header = "<h$hLevel><a name=\"${title}\">${titleView}</a></h$hLevel>"

        // links to anchor, not page
        //def tocEntry = "<div class=\"tocItem\" style=\"margin-left:${margin}px\"><a href=\"#${title}\">${title}</a></div>"
        def tocEntry = "<div class=\"tocItem\" style=\"margin-left:${margin}px\"><a href=\"#${title}\">${titleView}</a></div>"

        if (level == 0) {
            if (chapterTitle) // initially null, to collect sections
                writeChapter()

            chapterTitle = title // after previous used to write prev chapter
            chapterTitleView = titleView
            chapterHeader = header

            // links to page, not anchor
            //chaptersOnlyToc << "<div class=\"tocItem\" style=\"margin-left:${margin}px\"><a href=\"${chapterTitle}.html\">${chapterTitle}</a></div>"
            chaptersOnlyToc << "<div class=\"tocItem\" style=\"margin-left:${margin}px\"><a href=\"${chapterTitle}.html\">${chapterTitleView}</a></div>"
        }
        else {
            chapterToc << tocEntry
            chapterContents << header
        }        // level 0=h1, (1..n)=h2


        fullToc << tocEntry
        context.set(SOURCE_FILE, entry.value)
        context.set(CONTEXT_PATH, "..")
// deleteOriginalを使うように修正 @fumokmm
//      def body = engine.render(entry.value.text, context)
        def body = engine.render(deleteOriginal(entry.value.text), context)

        fullContents << header << body
        chapterContents <<  body

        new File("./output/guide/pages/${title}.html").withWriter("UTF-8") {
            template.make(title:title, header:header,
                          toc:"", content:body, path:"../..").writeTo(it)
        }
    }
}
if (chapterTitle) // write final chapter collected (if any seen)
    writeChapter()

/* Resources */

ant.mkdir(dir: "./output")
ant.mkdir(dir: "./output/img")
ant.mkdir(dir: "./output/css")
ant.mkdir(dir: "./output/ref")

ant.copy(file: "${BASEDIR}/resources/style/index.html", todir: "./output")
ant.copy(todir: "./output/img") {
    fileset(dir: "${BASEDIR}/resources/img")
}
ant.copy(todir: "./output/css") {
    fileset(dir: "${BASEDIR}/resources/css")
}
ant.copy(todir: "./output/ref") {
    fileset(dir: "${BASEDIR}/resources/style/ref")
}

/* Reference documentation */
vars = [
        title: langProp.props.title,
        subtitle: langProp.props.subtitle,
        footer: props.footer + langProp.props.footer,
        authors: props.authors + langProp.props.authors,
        version: props."grails.version",
        copyright: props.copyright + langProp.props.copyright,

        toc: fullToc.toString(),
        body: fullContents.toString()
]

new File("${BASEDIR}/resources/style/layout.html").withReader("UTF-8") {reader ->
    template = templateEngine.createTemplate(reader)
    new File("./output/guide/single.html").withWriter("UTF-8") {out ->
        template.make(vars).writeTo(out)
    }
    vars.toc = chaptersOnlyToc
    vars.body = ""
    new File("./output/guide/index.html").withWriter("UTF-8") {out ->
        template.make(vars).writeTo(out)
    }
}

menu = new StringBuffer()

void writeReferenceItem(File file, String path, String section, String name) {
    context.set(SOURCE_FILE, file)
    context.set(CONTEXT_PATH, path)

    def divClass = (name == "Usage") ? "menuUsageItem" : "menuItem"
    def nameRef = langProp.refTitle[name]?:name
    menu << "<div class='${divClass}'><a href=\"${section}/${name}.html\" target=\"mainFrame\">${nameRef}</a></div>"

    def content = engine.render(file.text, context)
    new File("./output/ref/${section}/${name}.html").withWriter("UTF-8") {
        template.make(content:content).writeTo(it)
    }
}

files = new File("${BASEDIR}/src/ref").listFiles().toList().sort()
reference = [:]
new File("${BASEDIR}/resources/style/referenceItem.html").withReader("UTF-8") {reader ->
    template = templateEngine.createTemplate(reader)
    for(f in files) {
        if(f.directory && !f.name.startsWith(".")) {

            def section = f.name
            def sectionName = langProp.refTitle[f.name]?:f.name
            menu << "<h1 class=\"menuTitle\">${sectionName}</h1>"
            new File("./output/ref/${section}").mkdirs()

            def usageFile = new File("${BASEDIR}/src/ref/${section}.gdoc")
            if (usageFile.exists()) {
                writeReferenceItem(usageFile, "../..", section, "Usage")
            }

            def items = f.listFiles().findAll{it.name.endsWith(".gdoc")}.sort()
            for(item in items) {
                //println "Generating reference item: ${name}"
                writeReferenceItem(item, "../..", section, item.name[0..-6])
            }
        }
    }
}
vars.menu = menu
new File("${BASEDIR}/resources/style/menu.html").withReader("UTF-8") {reader ->
    template = templateEngine.createTemplate(reader)
    new File("./output/ref/menu.html").withWriter("UTF-8") {out ->
        template.make(vars).writeTo(out)
    }
}

//PdfBuilder.build(BASEDIR)

println "Done. Look at output/index.html"
