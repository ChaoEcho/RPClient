package me.kafuuneko.rpclient.libs.chat

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import java.io.InputStream
import java.io.OutputStream
import java.io.Writer
import java.util.Base64

/**
 * 消息级图片归档协议，RPClient 字段优先于酒馆原生字段。
 * - 自有 Base64 只写入 extra.rpclient，酒馆可以忽略。
 * - 酒馆新版 media 与旧版 image/image_swipes 统一为有序引用。
 */
internal object ChatArchiveImageCodec {
    const val MAX_IMAGES = 256

    /** 每次只解析一张自有图片；大消息不先构造包含全部 Base64 的 JSON 树。 */
    fun readExtension(
        reader: JsonReader,
        transform: (ChatArchiveImage) -> ChatArchiveImage
    ): Pair<JsonObject, List<ChatArchiveImage>?> {
        val metadata = JsonObject()
        var images: List<ChatArchiveImage>? = null
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            require(!metadata.has(name)) { "Duplicate image extension field" }
            if (name != "images") {
                metadata.add(name, JsonParser.parseReader(reader))
                continue
            }
            // 暂存后仅留下资源键；空数组保留协议优先级与版本校验语义。
            val restored = mutableListOf<ChatArchiveImage>()
            reader.beginArray()
            while (reader.hasNext()) {
                require(restored.size < MAX_IMAGES) { "Too many archive images" }
                val image = JsonParser.parseReader(reader).asJsonObject
                val descriptor = ChatArchiveImage(string(image, "mime_type"), string(image, "data"))
                require(descriptor.data != null && descriptor.mimeType != null) { "Incomplete embedded image" }
                restored += transform(descriptor)
            }
            reader.endArray()
            metadata.add("images", JsonArray())
            images = restored
        }
        reader.endObject()
        return metadata to images
    }

    /** 解析版本化扩展；自有 images 存在时不再合并原生字段，避免重复图片。 */
    fun decode(extra: JsonObject?): List<ChatArchiveImage> {
        if (extra == null) return emptyList()
        val custom = extra.getAsJsonObject("rpclient")
        if (custom?.has("images") == true) {
            require(custom.get("schema_version")?.asInt == 1) { "Unsupported image archive version" }
            val images = custom.getAsJsonArray("images")
            require(images.size() <= MAX_IMAGES) { "Too many archive images" }
            return images.map { element ->
                val image = element.asJsonObject
                ChatArchiveImage(string(image, "mime_type"), string(image, "data")).also {
                    require(it.data != null && it.mimeType != null) { "Incomplete embedded image" }
                }
            }
        }
        // 新版数组是权威输入；旧版滑动图片列表保持顺序并去除同一引用的重复项。
        val media = extra.get("media")?.takeIf { it.isJsonArray }?.asJsonArray
        val urls = if (media != null) media.mapNotNull {
            val item = it.takeIf { value -> value.isJsonObject }?.asJsonObject
            if (item != null && string(item, "type") == "image") string(item, "url") else null
        } else buildList {
            extra.get("image_swipes")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach {
                if (it.isJsonPrimitive && it.asJsonPrimitive.isString) add(it.asString)
            }
            string(extra, "image")?.let(::add)
        }.distinct()
        require(urls.size <= MAX_IMAGES) { "Too many archive images" }
        return urls.filter { it.isNotBlank() }.map { url ->
            if (url.startsWith("data:", ignoreCase = true)) {
                val match = Regex("""^data:(image/[a-zA-Z0-9.+-]+);base64,([\s\S]*)$""", RegexOption.IGNORE_CASE)
                    .matchEntire(url) ?: error("Invalid embedded image")
                ChatArchiveImage(mimeType = match.groupValues[1], data = match.groupValues[2])
            } else ChatArchiveImage(sourceUrl = url)
        }
    }

    /** 按需提供 ASCII 字节，避免为整段 Base64 再分配一个同尺寸的字节数组。 */
    fun openData(data: String): InputStream = Base64.getDecoder().wrap(object : InputStream() {
        private var mPosition = 0
        override fun read(): Int {
            if (mPosition == data.length) return -1
            val value = data[mPosition++].code
            require(value <= 127) { "Invalid Base64 character" }
            return value
        }
    })

    /** 为小型协议调用编码图片；真实文件导出使用流式入口避免整份 Base64 常驻内存。 */
    fun encode(images: List<ChatArchiveImage>): JsonObject = JsonObject().apply {
        addProperty("schema_version", 1)
        add("images", JsonArray().apply {
            images.filter { it.data != null }.forEach { image ->
                require(image.mimeType != null) { "Missing embedded image MIME type" }
                add(metadata(image).apply { addProperty("data", image.data) })
            }
        })
    }

    /** 写出单张图片元数据和可选字节；Base64 包装流关闭时不关闭归档 writer。 */
    fun writeImage(image: ChatArchiveImage, writer: Writer, writeBytes: ((OutputStream) -> Unit)? = null) {
        val json = metadata(image).toString()
        if (writeBytes == null) {
            writer.write(json)
            return
        }
        // Base64 字母表不需要 JSON 转义，编码器仅持有当前数据块。
        writer.write(json.dropLast(1))
        if (json.length > 2) writer.write(",")
        writer.write("\"data\":\"")
        val output = object : OutputStream() {
            override fun write(value: Int) { writer.write(value) }
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                writer.write(String(bytes, offset, length, Charsets.US_ASCII))
            }
        }
        Base64.getEncoder().wrap(output).use(writeBytes)
        writer.write("\"}")
    }

    private fun metadata(image: ChatArchiveImage): JsonObject = JsonObject().apply {
        image.mimeType?.let { addProperty("mime_type", it) }
    }

    private fun string(json: JsonObject, key: String): String? = json.get(key)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
}
