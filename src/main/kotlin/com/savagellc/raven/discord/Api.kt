package com.savagellc.raven.discord

import okhttp3.Headers
import okhttp3.MediaType
import org.json.JSONArray
import org.json.JSONObject
import java.awt.image.BufferedImage
import java.net.URL
import javax.imageio.ImageIO
import javax.net.ssl.HttpsURLConnection
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

enum class RelationStatus(val n:Int) {
    FRIENDS(1),
    INCOMING(3),
    OUTGOING(4)
}
//277767292404891648
enum class OnlineStatus(val value:String, private val fName: String) {
    IDLE("idle", "Idle"),
    DO_NOT_DISTURB("dnd", "Do not disturb"),
    OFFLINE("invisible", "Invisible"),
    ONLINE("online", "Online");

    override fun toString(): String {
        return fName
    }
}

data class Response(
    val code: Int,
    val hasData: Boolean,
    val respMessage: String,
    val headers: Headers,
    val data: String
)

object ImageCache {
    val saved = HashMap<String, BufferedImage>()
    fun getImage(url:String): BufferedImage? {
        if(saved.containsKey(url)) return saved[url]
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.doOutput = true
        connection.setRequestProperty(
            "User-agent",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.145 Safari/537.36 Vivaldi/2.6.1566.49"
        )
        val img = ImageIO.read(connection.inputStream)
        saved[url] = img
        return saved[url]
    }
}

class Api(private val token: String, holdConnect:Boolean = false) {
    val webSocket = RavenWebSocket(token)
    val client = OkHttpClient()


    init {
        if(!holdConnect)
        webSocket.connect()
    }
    private fun request(
        path: String,
        contentType: String = "application/json",
        method: String = "GET",
        data: String? = null,
        withoutToken:Boolean = false
    ): Response {

        val request = if(withoutToken) Request.Builder()
            .url("https://discordapp.com/api$path")
            .addHeader("User-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.145 Safari/537.36 Vivaldi/2.6.1566.49")
            .method(method, data?.toRequestBody(contentType.toMediaType()))
            .build()
        else
            Request.Builder()
            .url("https://discordapp.com/api$path")
            .addHeader("User-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.145 Safari/537.36 Vivaldi/2.6.1566.49")
            .addHeader("Authorization", token)
            .method(method, data?.toRequestBody(contentType.toMediaType()))
            .build()
            val resp = client.newCall(request).execute()
        return Response(
            resp.code,
            resp.code == 200,
            resp.message,
            resp.headers,
            resp.body!!.string())
    }

    fun getDmChannels(): JSONArray {
        val response = request("/users/@me/channels")
        return JSONArray(response.data)
    }
    fun getGuilds(): JSONArray {
        val response = request("/users/@me/guilds")
        return JSONArray(response.data)
    }
    fun getGuildChannels(id:String): JSONArray {
        val response = request("/guilds/$id/channels")
        return JSONArray(response.data)
    }

    fun getSelf(): JSONObject {
        val response = request("/users/@me")
        return JSONObject(response.data)
    }
    fun login(email:String, password:String): JSONObject {
        val obj = JSONObject().put("email", email).put("password", password).put("undelete", false)
        return JSONObject(request("/v6/auth/login", data = obj.toString(), method = "POST", withoutToken = true).data)
    }
    fun getMessages(channelId: String): JSONArray {
        val response = request("/channels/$channelId/messages")
        return JSONArray(response.data)
    }
    fun editMessage(channelId: String, messageId:String, content: String): Response {
        return request("/channels/$channelId/messages/$messageId", method = "PATCH", data = JSONObject().put("content", content).toString())
    }
    fun deleteMessage(channelId: String, messageId:String): Response {
        return request("/channels/$channelId/messages/$messageId", method = "DELETE")
    }
    fun createDm(target:String): Response {
        return request("/users/@me/channels", method = "POST", data = JSONObject().put("recipient_id", target).toString())
    }
    fun sendRequest(targetId:String): Response {
        return request("/users/@me/relationships/$targetId", method = "PUT")
    }
    fun removeFriend(targetId:String): Response {
        return request("/users/@me/relationships/$targetId", method = "DELETE")
    }
    fun getFrieends(): Response {
        return request("/users/@me/relationships")
    }
    fun leaveServer(id:String): Response {
        return request("/users/@me/guilds/$id", method = "DELETE")
    }
    fun getMessages(channelId: String, before: String): JSONArray {
        val response = request("/channels/$channelId/messages?before=$before")
        return JSONArray(response.data)
    }
    fun acceptInvite(id:String): Response {
        return request("/invites/$id", method = "POST", data = "")
    }
    fun updateSettings(obj:JSONObject): Response {
        return request("/users/@me/settings", method = "PATCH", data = obj.toString())
    }
    fun updateOnlineStatus(status: OnlineStatus) {
        val obj = JSONObject().put("status", status.value).put("since", 0).put("activities", JSONArray()).put("afk", false)
        webSocket.sendMessage(OpCode.STATUS_UPDATE, obj)
        updateSettings(JSONObject().put("status", status.value))
    }
    fun sendSimpleMessage(channelId: String, message: String): Response {
        val obj = JSONObject()
        obj.put("content", message)
        obj.put("tts", false)
        obj.put("none", "${System.currentTimeMillis()}")
        return request("/channels/$channelId/messages", method = "POST", data = obj.toString())
    }
}