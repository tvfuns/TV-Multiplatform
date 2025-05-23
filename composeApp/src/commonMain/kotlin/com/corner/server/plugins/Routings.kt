package com.corner.server.plugins

import cn.hutool.core.io.file.FileNameUtil
import com.corner.server.logic.proxy
import com.corner.ui.scene.SnackBar
import com.corner.util.toSingleValueMap
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import okhttp3.Response
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream

fun Application.configureRouting() {
    val log = LoggerFactory.getLogger("Routing")
    routing {
        staticResources("/static", "assets"){
            contentType {
                val suffix = FileNameUtil.getSuffix(it.path)
                when(suffix){
                   "js" -> ContentType.Text.JavaScript
                    "jsx" -> ContentType.Application.JavaScript
                    "html" -> ContentType.Text.Html
                    "txt" -> ContentType.Text.Plain
                    "htm" -> ContentType.Text.Html
                    else -> null
                }
            }
        }

//        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")

        get("/") {
            call.respondText("hello world!")
        }

        get("/postMsg") {
            val msg = call.request.queryParameters["msg"]
            if (msg?.isBlank() == true) {
                call.respond(HttpStatusCode.MultiStatus, "消息不可为空")
                return@get
            }
            SnackBar.postMsg(msg!!)
        }

        /**
         * 播放器必须支持range请求 否则会返回完整资源 导致拨动进度条加载缓慢
         */
        get("/proxy") {
            val parameters = call.request.queryParameters
            val paramMap = parameters.toSingleValueMap().toMutableMap()
            paramMap.putAll(call.request.headers.toSingleValueMap())
            log.info("proxy param:{}", paramMap)
            var response: Response? = null
            // 0 statusCode 1 content_type 2 body
            try {
                val objects: Array<Any> = proxy(paramMap) ?: arrayOf()
                if (objects.isEmpty()) errorResp(call)
                else if (objects[0] is Response) {
                    response = objects[0] as Response
                    response.headers.forEach { i -> if(!HttpHeaders.isUnsafe(i.first)) call.response.headers.append(i.first, i.second) }
                    log.debug("proxy resp code:{} headers:{}", response.code, response.headers)
                    call.respondOutputStream(
                        status = HttpStatusCode.fromValue(response.code),
                    ) {
                        response.body.byteStream().transferTo(this)
                    }
                } else if (HttpStatusCode.Found.value == objects[0]) {
                    call.respondRedirect(Url(objects[2] as String), false)
                } else {
                    if(objects.size == 4){
                        (objects[3] as HashMap<String,String>).forEach { t, u ->
                            call.response.headers.append(t, u)
                        }
                    }
                    call.respondOutputStream(ContentType.parse(objects[1].toString()),HttpStatusCode.fromValue(objects[0] as Int)){
                        (objects[2] as InputStream).transferTo(this)
                    }
                }
            }catch (ignore:IOException){
            } catch (e: Exception) {
                log.error("proxy 错误", e)
            } finally {
                response?.close()
            }
        }

//        get("/multi") {
//            val params = call.request.queryParameters
//            val url = params["url"]
//            if (StringUtils.isEmpty(url) || url!!.startsWith("http")) {
//                errorResp(call, "url不合法")
//                return@get
//            }
//            var thread = 5
//            if (StringUtils.isNotEmpty(params.get("thread"))) {
//                thread = params.get("thread")!!.toInt()
//            }
//            multiThreadDownload(url, thread, call)
//        }
    }
}

suspend fun errorResp(call: ApplicationCall) {
    call.respondText(
        text = HttpStatusCode.InternalServerError.description,
        contentType = ContentType.Application.OctetStream,
        status = HttpStatusCode.InternalServerError,
        {})
}

suspend fun errorResp(call: ApplicationCall, msg: String) {
    call.respondText(
        text = HttpStatusCode.InternalServerError.description,
        contentType = ContentType.Application.OctetStream,
        status = HttpStatusCode.InternalServerError,
        {})
}
