package io.legado.app.data.entities


import io.legado.app.constant.BookType
import io.legado.app.constant.AppPattern
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.FileUtils
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.localBook.EpubFile
import io.legado.app.model.localBook.UmdFile
import io.legado.app.model.localBook.CbzFile
import java.nio.charset.Charset
import java.io.File
import kotlin.math.max
import kotlin.math.min
import org.jsoup.Jsoup
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import mu.KotlinLogging
import io.legado.app.utils.*
import com.jayway.jsonpath.DocumentContext
import com.fasterxml.jackson.annotation.JsonProperty;

val logger = KotlinLogging.logger {}

@JsonIgnoreProperties("variableMap", "infoHtml", "tocHtml", "config", "rootDir", "localBook", "epub", "epubRootDir", "onLineTxt", "localTxt", "umd", "realAuthor", "unreadChapterNum", "folderName", "pdfImageWidth", "localFile", "kindList", "_userNameSpace", "bookDir", "userNameSpace")
data class Book(
        override var bookUrl: String = "",                   // 详情页Url(本地书源存储完整文件路径)
        var tocUrl: String = "",                    // 目录页Url (toc=table of Contents)
        var origin: String = BookType.local,        // 书源URL(默认BookType.local)
        var originName: String = "",                //书源名称
        override var name: String = "",                   // 书籍名称(书源获取)
        override var author: String = "",                 // 作者名称(书源获取)
        override var kind: String? = null,                    // 分类信息(书源获取)
        var customTag: String? = null,              // 分类信息(用户修改)
        var coverUrl: String? = null,               // 封面Url(书源获取)
        var customCoverUrl: String? = null,         // 封面Url(用户修改)
        var intro: String? = null,            // 简介内容(书源获取)
       var customIntro: String? = null,      // 简介内容(用户修改)
       var charset: String? = null,                // 自定义字符集名称(仅适用于本地书籍)
        var type: Int = 0,                          // @BookType
       var group: Long = 0,                         // 自定义分组索引号
        var latestChapterTitle: String? = null,     // 最新章节标题
        var latestChapterTime: Long = System.currentTimeMillis(),            // 最新章节标题更新时间
        var lastCheckTime: Long = System.currentTimeMillis(),                // 最近一次更新书籍信息的时间
        var lastCheckCount: Int = 0,                // 最近一次发现新章节的数量
        var totalChapterNum: Int = 0,               // 书籍目录总数
       var durChapterTitle: String? = null,        // 当前章节名称
       var durChapterIndex: Int = 0,               // 当前章节索引
       var durChapterPos: Int = 0,                 // 当前阅读的进度(首行字符的索引位置)
       var durChapterTime: Long = System.currentTimeMillis(),               // 最近一次阅读书籍的时间(打开正文的时间)
        override var wordCount: String? = null,
       var canUpdate: Boolean = true,              // 刷新书架时更新书籍信息
       var order: Int = 0,                         // 手动排序
       var originOrder: Int = 0,                   //书源排序
        var useReplaceRule: Boolean = true,         // 正文使用净化替换规则
        var variable: String? = null,                // 自定义书籍变量信息(用于书源规则检索书籍信息)
        var readConfig: ReadConfig? = null,
        @get:JsonProperty("isInShelf") var isInShelf: Boolean = false,               // 是否加入到书架
        var lastCheckError: String? = null               // 上次更新错误
    ) : BaseBook {

    fun isLocalBook(): Boolean {
        return origin == BookType.local
    }

    fun isLocalTxt(): Boolean {
        return isLocalBook() && originName.endsWith(".txt", true)
    }

    fun isLocalEpub(): Boolean {
        return isLocalBook() && originName.endsWith(".epub", true)
    }

    fun isLocalPdf(): Boolean {
        return isLocalBook() && originName.endsWith(".pdf", true)
    }

    fun isEpub(): Boolean {
        return originName.endsWith(".epub", true)
    }

    fun isCbz(): Boolean {
        return originName.endsWith(".cbz", true)
    }

    fun isPdf(): Boolean {
        return originName.endsWith(".pdf", true)
    }

    fun isUmd(): Boolean {
        return originName.endsWith(".umd", true)
    }

    fun isOnLineTxt(): Boolean {
        return !isLocalBook() && type == 0
    }

    override fun equals(other: Any?): Boolean {
        if (other is Book) {
            return other.bookUrl == bookUrl
        }
        return false
    }

    override fun hashCode(): Int {
        return bookUrl.hashCode()
    }

    @delegate:Transient
    override val variableMap: HashMap<String, String> by lazy {
        GSON.fromJsonObject<HashMap<String, String>>(variable).getOrNull() ?: hashMapOf()
    }

    override fun putVariable(key: String, value: String?) {
        if (value != null) {
            variableMap[key] = value
        } else {
            variableMap.remove(key)
        }
        variable = GSON.toJson(variableMap)
    }

    override var infoHtml: String? = null

    override var tocHtml: String? = null

    fun getRealAuthor() = author.replace(AppPattern.authorRegex, "")

    fun getUnreadChapterNum() = max(totalChapterNum - durChapterIndex - 1, 0)

    fun getDisplayCover() = if (customCoverUrl.isNullOrEmpty()) coverUrl else customCoverUrl

    fun getDisplayIntro() = if (customIntro.isNullOrEmpty()) intro else customIntro

    fun fileCharset(): Charset {
        return charset(charset ?: "UTF-8")
    }

    private fun config(): ReadConfig {
        if (readConfig == null) {
            readConfig = ReadConfig()
        }
        return readConfig!!
    }

    fun setDelTag(tag: Long) {
        config().delTag =
            if ((config().delTag and tag) == tag) config().delTag and tag.inv() else config().delTag or tag
    }

    fun getDelTag(tag: Long): Boolean {
        return config().delTag and tag == tag
    }

    fun getPdfImageWidth(): Float {
        return config().pdfImageWidth
    }

    fun setPdfImageWidth(pdfImageWidth: Float) {
        config().pdfImageWidth = pdfImageWidth
    }

    fun getFolderName(): String {
        //防止书名过长,只取9位
        var folderName = name.replace(AppPattern.fileNameRegex, "")
        folderName = folderName.substring(0, min(9, folderName.length))
        return folderName + MD5Utils.md5Encode16(bookUrl)
    }

    @Transient
    private var rootDir: String = ""

    fun setRootDir(root: String) {
        if (root.isNotEmpty() && !root.endsWith(File.separator)) {
            rootDir = root + File.separator
        } else {
            rootDir = root
        }
    }

    fun getLocalFile(): File {
        if (originName.startsWith(rootDir)) {
            originName = originName.replaceFirst(rootDir, "")
        }
        logger.info("getLocalFile rootDir: {} originName: {}", rootDir, originName)
        if (isEpub() && originName.indexOf("localStore") < 0 && originName.indexOf("webdav") < 0) {
            // 非本地/webdav书仓的 epub文件
            return FileUtils.getFile(File(rootDir + originName), "index.epub")
        }
        if (isCbz() && originName.indexOf("localStore") < 0 && originName.indexOf("webdav") < 0) {
            // 非本地/webdav书仓的 cbz文件
            return FileUtils.getFile(File(rootDir + originName), "index.cbz")
        }
        if (isPdf() && originName.indexOf("localStore") < 0 && originName.indexOf("webdav") < 0) {
            // 非本地/webdav书仓的 pdf文件
            return FileUtils.getFile(File(rootDir + originName), "index.pdf")
        }
        return File(rootDir + originName)
    }

    @Transient
    private var _userNameSpace: String = ""

    fun setUserNameSpace(nameSpace: String) {
        _userNameSpace = nameSpace
    }

    override fun getUserNameSpace(): String {
        return _userNameSpace
    }

    fun getBookDir(): String {
        return FileUtils.getPath(File(rootDir), "storage", "data", _userNameSpace, name + "_" + author)
    }

    fun getSplitLongChapter(): Boolean {
        return false
    }

    fun toSearchBook(): SearchBook {
        return SearchBook(
                name = name,
                author = author,
                kind = kind,
                bookUrl = bookUrl,
                origin = origin,
                originName = originName,
                type = type,
                wordCount = wordCount,
                latestChapterTitle = latestChapterTitle,
                coverUrl = coverUrl,
                intro = intro,
                tocUrl = tocUrl,
//                originOrder = originOrder,
                variable = variable
        ).apply {
            this.infoHtml = this@Book.infoHtml
            this.tocHtml = this@Book.tocHtml
            this.setUserNameSpace(this@Book.getUserNameSpace())
        }
    }

    fun getEpubRootDir(): String {
        // 根据 content.opf 位置来确认root目录
        // var contentOPF = "OEBPS/content.opf"

        val defaultPath = "OEBPS"

        // 根据 META-INF/container.xml 来获取 contentOPF 位置
        val containerRes = File(bookUrl + File.separator + "index" + File.separator + "META-INF" + File.separator + "container.xml")
        if (containerRes.exists()) {
            try {
                val document = Jsoup.parse(containerRes.readText())
                val rootFileElement = document
                        .getElementsByTag("rootfiles").get(0)
                        .getElementsByTag("rootfile").get(0);
                val result = rootFileElement.attr("full-path");
                System.out.println("result: " + result)
                if (result != null && result.isNotEmpty()) {
                    return File(result).parentFile?.let{
                        it.toString()
                    } ?: ""
                }
            } catch (e: Exception) {
                e.printStackTrace();
                // Log.e(TAG, e.getMessage(), e);
            }
        }

        // 返回默认位置
        return defaultPath
    }

    fun updateFromLocal(onlyCover: Boolean = false) {
        try {
            if (isEpub()) {
                EpubFile.upBookInfo(this, onlyCover)
            } else if (isUmd()) {
                UmdFile.upBookInfo(this, onlyCover)
            } else if (isCbz()) {
                CbzFile.upBookInfo(this, onlyCover)
            }
        } catch(e: Exception) {
            e.printStackTrace()
        }
    }

    fun workRoot(): String {
        return rootDir
    }

    companion object {
        const val hTag = 2L
        const val rubyTag = 4L
        const val imgTag = 8L
        const val imgStyleDefault = "DEFAULT"
        const val imgStyleFull = "FULL"
        const val imgStyleText = "TEXT"

        fun initLocalBook(bookUrl: String, localPath: String, rootDir: String = ""): Book {
            val fileName = File(localPath).name
            val nameAuthor = LocalBook.analyzeNameAuthor(fileName)
            val book = Book(bookUrl, "", BookType.local, localPath, nameAuthor.first, nameAuthor.second).also {
                it.canUpdate = false
            }
            book.setRootDir(rootDir)
            book.updateFromLocal()
            return book
        }

        fun fromJsonDoc(doc: DocumentContext): Result<Book> {
            return kotlin.runCatching {
                val readConfig = doc.read<Any>("$.readConfig")
                Book(
                    bookUrl = doc.readString("$.bookUrl")!!,
                    tocUrl = doc.readString("$.tocUrl")!!,
                    origin = doc.readString("$.origin") ?: BookType.local,
                    originName = doc.readString("$.originName") ?: "",
                    name = doc.readString("$.name")!!,
                    author = doc.readString("$.author") ?: "",
                    kind = doc.readString("$.kind"),
                    customTag = doc.readString("$.customTag"),
                    coverUrl = doc.readString("$.coverUrl"),
                    customCoverUrl = doc.readString("$.customCoverUrl"),
                    intro = doc.readString("$.intro"),
                    customIntro = doc.readString("$.customIntro"),
                    charset = doc.readString("$.charset"),
                    type = doc.readInt("$.type") ?: 0,
                    group = doc.readLong("$.group") ?: 0,
                    latestChapterTitle = doc.readString("$.latestChapterTitle"),
                    latestChapterTime = doc.readLong("$.latestChapterTime") ?: System.currentTimeMillis(),
                    lastCheckTime = doc.readLong("$.lastCheckTime") ?: System.currentTimeMillis(),
                    lastCheckCount = doc.readInt("$.lastCheckCount") ?: 0,
                    totalChapterNum = doc.readInt("$.totalChapterNum") ?: 0,
                    durChapterTitle = doc.readString("$.durChapterTitle"),
                    durChapterIndex = doc.readInt("$.durChapterIndex") ?: 0,
                    durChapterPos = doc.readInt("$.durChapterPos") ?: 0,
                    durChapterTime = doc.readLong("$.durChapterTime") ?: System.currentTimeMillis(),
                    wordCount = doc.readString("$.wordCount"),
                    canUpdate = doc.readBool("$.canUpdate") ?: true,
                    order = doc.readInt("$.order") ?: 0,
                    originOrder = doc.readInt("$.originOrder") ?: 0,
                    useReplaceRule = doc.readBool("$.useReplaceRule") ?: true,
                    variable = doc.readString("$.variable"),
                    readConfig = if (readConfig != null) kotlin.runCatching {
                        ReadConfig(
                            reverseToc = doc.readBool("$.readConfig.reverseToc") ?: false,
                            pageAnim = doc.readInt("$.readConfig.pageAnim") ?: -1,
                            reSegment = doc.readBool("$.readConfig.reSegment") ?: false,
                            imageStyle = doc.readString("$.readConfig.imageStyle"),
                            useReplaceRule = doc.readBool("$.readConfig.useReplaceRule") ?: false,
                            delTag = doc.readLong("$.readConfig.delTag") ?: 0L
                        )
                    }.getOrNull() else null,
                    isInShelf = doc.readBool("$.isInShelf") ?: false
                )
            }
        }

        fun fromJson(json: String): Result<Book> {
            return fromJsonDoc(jsonPath.parse(json))
        }

        fun fromJsonArray(jsonArray: String): Result<ArrayList<Book>> {
            return kotlin.runCatching {
                val sources = arrayListOf<Book>()
                val doc = jsonPath.parse(jsonArray).read<List<*>>("$")
                doc.forEach {
                    val jsonItem = jsonPath.parse(it)
                    fromJsonDoc(jsonItem).getOrThrow().let { source ->
                        sources.add(source)
                    }
                }
                sources
            }
        }
    }

    data class ReadConfig(
        var reverseToc: Boolean = false,
        var pageAnim: Int = -1,
        var reSegment: Boolean = false,
        var imageStyle: String? = null,
        var useReplaceRule: Boolean = false,   // 正文使用净化替换规则
        var delTag: Long = 0L,   // 去除标签
        var pdfImageWidth: Float = 800f  // pdf 生成图片宽度
    )

    class Converters {
        fun readConfigToString(config: ReadConfig?): String = GSON.toJson(config)

        fun stringToReadConfig(json: String?) = GSON.fromJsonObject<ReadConfig>(json)
    }
}